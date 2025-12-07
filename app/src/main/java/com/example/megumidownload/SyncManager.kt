package com.example.megumidownload

import android.content.Context
import kotlinx.coroutines.flow.first
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.megumidownload.SeriesEntry

class SyncManager(
    private val context: Context,
    private val configManager: ConfigManager,
    private val seriesManager: SeriesManager
) {
    private val smbClient = SmbClientWrapper()

    private fun loadGroups(): List<String> {
        val groupsFile = File(context.filesDir, "groups.megumi")
        if (!groupsFile.exists()) return emptyList()
        return try {
            groupsFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error loading groups.megumi", e)
            emptyList()
        }
    }

    private fun isFileAllowedByGroups(fileName: String, groups: List<String>): Boolean {
        if (groups.isEmpty()) return true
        // Logic from python: any(f"[{group}]" in name or f"【{group}】" in name for group in self.groups)
        return groups.any { group -> fileName.contains("[$group]") || fileName.contains("【$group】") }
    }

    sealed class SyncResult {
        object Success : SyncResult()
        data class Error(val message: String) : SyncResult()
        data class Conflict(val localFile: File, val remoteFile: SmbClientWrapper.SmbFile, val onResolve: suspend (Boolean) -> Unit) : SyncResult() // Boolean: true = overwrite remote, false = skip
    }

    data class SyncOptions(
        val isLocalToRemote: Boolean = true,
        val syncFilelist: Boolean = true,
        val syncReplace: Boolean = true,
        val syncEpisodes: Boolean = true,
        val selectedFiles: List<String>? = null // Null means all files
    )

    data class SyncProgress(
        val currentSeries: String,
        val currentFile: String,
        val seriesIndex: Int,
        val totalSeries: Int,
        val fileProgress: Float,
        val totalProgress: Float
    )

    private val notificationHelper = NotificationHelper(context)

    suspend fun syncAll(
        seriesList: List<SeriesEntry>,
        options: SyncOptions,
        onProgress: (SyncProgress) -> Unit,
        onConflict: suspend (File, SmbClientWrapper.SmbFile) -> Boolean
    ): SyncResult = withContext(Dispatchers.IO) {
        val totalSeries = seriesList.size
        var currentSeriesIndex = 0
        
        notificationHelper.showProgressNotification("Sync Started", "Preparing...", 0, 100, true)

        try {
            val rootPath = connectToShare() ?: return@withContext SyncResult.Error("Could not connect to share")
            
            val localRoot = try {
                configManager.localPath.first()
            } catch (e: Exception) {
                ""
            }

            for (series in seriesList) {
                currentSeriesIndex++
                val seriesProgressBase = (currentSeriesIndex - 1).toFloat() / totalSeries
                
                notificationHelper.showProgressNotification("Syncing ${series.folderName}", "Checking files...", (seriesProgressBase * 100).toInt(), 100)
                
                val result = syncSeriesInternal(
                    series = series,
                    smbClient = smbClient,
                    rootPath = rootPath,
                    localRoot = localRoot,
                    options = options,
                    onProgress = { msg, fileProgress -> 
                        // fileProgress is 0.0 to 1.0 for the current series
                        // Map to overall progress
                        val totalProgress = seriesProgressBase + (fileProgress * (1f / totalSeries))
                        val progressInt = (totalProgress * 100).toInt()
                        
                        onProgress(SyncProgress(
                            currentSeries = series.folderName,
                            currentFile = msg,
                            seriesIndex = currentSeriesIndex - 1,
                            totalSeries = totalSeries,
                            fileProgress = fileProgress,
                            totalProgress = totalProgress
                        ))
                        notificationHelper.showProgressNotification("Syncing ${series.folderName}", msg, progressInt, 100)
                    },
                    onConflict = onConflict
                )
                
                if (result is SyncResult.Error) {
                    android.util.Log.e("SyncManager", "Error syncing ${series.folderName}: ${result.message}")
                    // Continue with next series? Or stop?
                    // User probably wants to continue.
                }
            }
            return@withContext SyncResult.Success
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "SyncAll error", e)
            return@withContext SyncResult.Error("SyncAll error: ${e.message}")
        } finally {
            smbClient.disconnect()
        }
    }

    suspend fun syncSeries(series: SeriesEntry, onProgress: (String) -> Unit, onConflict: suspend (File, SmbClientWrapper.SmbFile) -> Boolean): SyncResult = withContext(Dispatchers.IO) {
        val rootPath = connectToShare() ?: return@withContext SyncResult.Error("Could not connect to share")
        
        val localRoot = try {
            configManager.localPath.first()
        } catch (e: Exception) {
            ""
        }

        try {
            return@withContext syncSeriesInternal(
                series = series,
                smbClient = smbClient,
                rootPath = rootPath,
                localRoot = localRoot,
                options = SyncOptions(), // Default options
                onProgress = { msg, _ -> onProgress(msg) }, // Ignore float progress for single sync simple callback? Or update it?
                // The caller of syncSeries expects (String) -> Unit.
                // But SingleSeriesSyncDialog uses syncAll internally now!
                // Wait, syncSeries is used by... nothing?
                // Let's check usages. SingleSeriesSyncDialog calls syncAll with list of 1.
                // So syncSeries might be unused or legacy.
                // I'll keep it compatible but ignore progress float for now.
                onConflict = onConflict
            )
        } catch (e: Exception) {
            return@withContext SyncResult.Error("Sync error: ${e.message}")
        } finally {
            smbClient.disconnect()
        }
    }

    suspend fun fetchRemoteFiles(series: SeriesEntry): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val rootPath = connectToShare() ?: return@withContext Result.failure(Exception("Could not connect to share"))
            // Use rootPath returned by connectToShare, which handles the share name split correctly
            val remotePath = if (rootPath.isNotEmpty()) "$rootPath/${series.folderName}/Season ${series.seasonNumber}" else "${series.folderName}/Season ${series.seasonNumber}"
            
            if (smbClient.exists(remotePath)) {
                val files = smbClient.listFiles(remotePath)
                val enableGroups = configManager.enableGroups.first()
                val groups = if (enableGroups) loadGroups() else emptyList()
                
                android.util.Log.d("SyncManager", "fetchRemoteFiles: Path=$remotePath, FilesFound=${files.size}, EnableGroups=$enableGroups, Groups=$groups")

                val mkvFiles = files
                    .filter { it.name.endsWith(".mkv", ignoreCase = true) }
                    .filter { !enableGroups || isFileAllowedByGroups(it.name, groups) }
                    .map { it.name }
                
                android.util.Log.d("SyncManager", "fetchRemoteFiles: Filtered MKVs=${mkvFiles.size}")
                Result.success(mkvFiles)
            } else {
                android.util.Log.w("SyncManager", "fetchRemoteFiles: Remote path does not exist: $remotePath")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLocalFiles(series: SeriesEntry): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val localPath = configManager.localPath.first()
            if (localPath.isBlank()) return@withContext Result.failure(Exception("Local path not set"))
            
            val dir = File(localPath, "${series.folderName}/Season ${series.seasonNumber}")
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".mkv", ignoreCase = true) }?.map { it.name } ?: emptyList()
                Result.success(files)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncSeriesInternal(
        series: SeriesEntry,
        smbClient: SmbClientWrapper,
        rootPath: String,
        localRoot: String,
        options: SyncOptions,
        onProgress: (String, Float) -> Unit, // Changed to accept progress float (0.0 - 1.0)
        onConflict: suspend (File, SmbClientWrapper.SmbFile) -> Boolean
    ): SyncResult {
        val seriesFolder = series.folderName
        val seasonFolder = "Season ${series.seasonNumber}"
        val remoteSeriesPath = if (rootPath.isNotEmpty()) "$rootPath/$seriesFolder/$seasonFolder" else "$seriesFolder/$seasonFolder"
        val localSeriesDir = File(localRoot, "$seriesFolder/$seasonFolder")
        
        if (!localSeriesDir.exists()) {
             // If syncing Remote -> Local, we might need to create it.
             if (!options.isLocalToRemote) {
                 localSeriesDir.mkdirs()
             } else {
                 return SyncResult.Error("Local directory not found: ${localSeriesDir.absolutePath}")
             }
        }

        onProgress("Listing local files...", 0f)
        val localFiles = localSeriesDir.listFiles()?.toList() ?: emptyList()
        
        onProgress("Listing remote files...", 0f)
        val remoteFiles = try {
            smbClient.listFiles(remoteSeriesPath)
        } catch (e: Exception) {
            // Remote dir might not exist
            emptyList()
        }
        
        if (options.isLocalToRemote) {
            // Sync Local -> Remote (Upload)
            for (file in localFiles) {
                if (file.isDirectory) continue
                
                // Apply selectedFiles filter for episodes
                if (file.name.endsWith(".mkv") && options.selectedFiles != null && !options.selectedFiles.contains(file.name)) {
                    continue
                }

                val remoteFile = remoteFiles.find { it.name == file.name }
                
                if (remoteFile != null) {
                    // Conflict / Update check
                    if (file.name.endsWith(".mkv")) {
                        if (options.syncEpisodes) {
                            if (file.length() != remoteFile.size) {
                                 val overwrite = onConflict(file, remoteFile)
                                 if (overwrite) {
                                     onProgress("Uploading ${file.name}...", 0f)
                                     smbClient.uploadFile(file, "$remoteSeriesPath/${file.name}") { progress ->
                                         onProgress("Uploading ${file.name}...", progress)
                                     }
                                 } else {
                                     onProgress("Skipping ${file.name}", 0f)
                                 }
                            }
                        }
                    } else if (file.name == "filelist.txt" && options.syncFilelist) {
                        onProgress("Merging filelist.txt...", 0f)
                        mergeFilelist(file, "$remoteSeriesPath/${file.name}")
                    } else if (file.name == "replace.txt" && options.syncReplace) {
                        if (file.length() > remoteFile.size) {
                            onProgress("Updating replace.txt...", 0f)
                            smbClient.uploadFile(file, "$remoteSeriesPath/${file.name}")
                        }
                    }
                } else {
                    // New file
                    if ((file.name == "filelist.txt" && !options.syncFilelist) || (file.name == "replace.txt" && !options.syncReplace)) {
                        continue
                    }
                    if (file.name.endsWith(".mkv") && !options.syncEpisodes) {
                        continue
                    }
                    onProgress("Uploading ${file.name}...", 0f)
                    smbClient.uploadFile(file, "$remoteSeriesPath/${file.name}") { progress ->
                        onProgress("Uploading ${file.name}...", progress)
                    }
                }
            }
        } else {
            // Sync Remote -> Local (Download)
             val enableGroups = configManager.enableGroups.first()
             val groups = if (enableGroups) loadGroups() else emptyList()
             
             for (rFile in remoteFiles) {
                if (rFile.isDirectory) continue
                
                val lFile = File(localSeriesDir, rFile.name)
                
                if ((rFile.name == "filelist.txt" && !options.syncFilelist) || (rFile.name == "replace.txt" && !options.syncReplace)) {
                    continue
                }
                if (rFile.name.endsWith(".mkv")) {
                    if (!options.syncEpisodes) continue
                    if (enableGroups && !isFileAllowedByGroups(rFile.name, groups)) {
                        android.util.Log.d("SyncManager", "Skipping ${rFile.name} (Group filter)")
                        continue
                    }
                    
                    // Apply selectedFiles filter
                    if (options.selectedFiles != null && !options.selectedFiles.contains(rFile.name)) {
                        android.util.Log.d("SyncManager", "Skipping ${rFile.name} (Not selected)")
                        continue
                    }
                }

                if (lFile.exists()) {
                    // Conflict check for download
                    // Always check replace.txt for content differences (or just ask if exists)
                    // For MKVs, check size
                    val isConflict = if (rFile.name == "replace.txt") {
                        try {
                            val tempFile = File.createTempFile("compare", ".txt", context.cacheDir)
                            smbClient.downloadFile(rFile.path, tempFile)
                            val localContent = lFile.readText()
                            val remoteContent = tempFile.readText()
                            tempFile.delete()
                            
                            if (localContent != remoteContent) {
                                true
                            } else {
                                onProgress("replace.txt identical, skipping.", 0f)
                                false
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SyncManager", "Error comparing replace.txt", e)
                            true // Fallback to conflict if check fails
                        }
                    } else {
                        lFile.length() != rFile.size
                    }

                    if (isConflict) {
                         val overwrite = onConflict(lFile, rFile)
                         if (overwrite) {
                             onProgress("Downloading ${rFile.name}...", 0f)
                             smbClient.downloadFile(rFile.path, lFile) { progress ->
                                 onProgress("Downloading ${rFile.name}...", progress)
                             }
                         } else {
                             onProgress("Skipping ${rFile.name}", 0f)
                         }
                    }
                } else {
                     onProgress("Downloading ${rFile.name}...", 0f)
                     smbClient.downloadFile(rFile.path, lFile) { progress ->
                         onProgress("Downloading ${rFile.name}...", progress)
                     }
                }
            }
        }

        return SyncResult.Success
    }

    private suspend fun mergeFilelist(localFile: File, remotePath: String) {
        // Download remote filelist to temp
        val tempFile = File.createTempFile("remote_filelist", ".txt")
        try {
            smbClient.downloadFile(remotePath, tempFile)
            val localLines = localFile.readLines().toMutableSet()
            val remoteLines = tempFile.readLines()
            
            var changed = false
            for (line in remoteLines) {
                if (line.isNotBlank() && !localLines.contains(line)) {
                    localLines.add(line)
                    changed = true
                }
            }
            
            if (changed) {
                // Write merged back to local
                localFile.writeText(localLines.joinToString("\n"))
                // And upload to remote
                smbClient.uploadFile(localFile, remotePath)
            }
        } catch (e: Exception) {
            // If remote doesn't exist, just upload local?
            // Or if download fails.
            android.util.Log.e("SyncManager", "Error merging filelist", e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun readRemoteFileContent(remotePath: String): String = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("remote_read", ".tmp", context.cacheDir)
        try {
            smbClient.downloadFile(remotePath, tempFile)
            return@withContext tempFile.readText()
        } finally {
            tempFile.delete()
        }
    }

    suspend fun writeRemoteFileContent(remotePath: String, content: String) = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("remote_write", ".tmp", context.cacheDir)
        try {
            tempFile.writeText(content)
            smbClient.uploadFile(tempFile, remotePath)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun getRemoteReplaceContent(series: SeriesEntry): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rootPath = connectToShare() ?: return@withContext Result.failure(Exception("Could not connect to share (check settings)"))
            val seriesFolder = series.folderName
            val seasonFolder = "Season ${series.seasonNumber}"
            val remotePath = if (rootPath.isNotEmpty()) "$rootPath/$seriesFolder/$seasonFolder/replace.txt" else "$seriesFolder/$seasonFolder/replace.txt"
            
            android.util.Log.d("SyncManager", "Downloading replace.txt from $remotePath")
            
            val tempFile = File.createTempFile("remote_replace", ".txt")
            try {
                smbClient.downloadFile(remotePath, tempFile)
                val content = tempFile.readText()
                return@withContext Result.success(content)
            } catch (e: java.io.FileNotFoundException) {
                android.util.Log.w("SyncManager", "Remote replace.txt not found at $remotePath")
                return@withContext Result.success("") // Return empty string if not found
            } catch (e: Exception) {
                android.util.Log.e("SyncManager", "Error downloading replace.txt", e)
                val stackTrace = e.stackTrace.firstOrNull()
                val innerSeriesFolder = series.folderName
                val innerSeasonFolder = "Season ${series.seasonNumber}"
                val debugPath = "$innerSeriesFolder/$innerSeasonFolder/replace.txt"
                return@withContext Result.failure(Exception("DEBUG (Inner): Error at ${stackTrace?.fileName}:${stackTrace?.lineNumber} - ${e.message} (Path: $debugPath)"))
            } finally {
                tempFile.delete()
                smbClient.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error getting remote replace.txt", e)
            smbClient.disconnect()
            val stackTrace = e.stackTrace.firstOrNull()
            // Construct path for debug info
            val seriesFolder = series.folderName
            val seasonFolder = "Season ${series.seasonNumber}"
            val debugPath = "$seriesFolder/$seasonFolder/replace.txt"
            return@withContext Result.failure(Exception("DEBUG: Error at ${stackTrace?.fileName}:${stackTrace?.lineNumber} - ${e.message} (Path: $debugPath)"))
        }
    }

    suspend fun saveRemoteReplaceContent(series: SeriesEntry, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootPath = connectToShare() ?: return@withContext false
            val seriesFolder = series.folderName
            val seasonFolder = "Season ${series.seasonNumber}"
            val remotePath = if (rootPath.isNotEmpty()) "$rootPath/$seriesFolder/$seasonFolder/replace.txt" else "$seriesFolder/$seasonFolder/replace.txt"
            
            val tempFile = File.createTempFile("remote_replace_save", ".txt")
            tempFile.writeText(content)
            try {
                smbClient.uploadFile(tempFile, remotePath)
                return@withContext true
            } finally {
                tempFile.delete()
                smbClient.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error saving remote replace.txt", e)
            smbClient.disconnect()
            return@withContext false
        }
    }

    private suspend fun connectToShare(): String? {
        val host: String
        val user: String
        val pass: String
        var sharePath: String
        
        try {
            val config = configManager.getSmbConfig()
            host = config.host
            user = config.user
            pass = config.pass
            sharePath = config.sharePath
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error loading config", e)
            return null
        }
        
        if (host.isBlank() || sharePath.isBlank()) {
            android.util.Log.e("SyncManager", "Host or SharePath is blank")
            return null
        }
        
        // Normalize path: replace backslashes with forward slashes, remove leading slashes
        sharePath = sharePath.replace("\\", "/").trimStart('/')
        
        val parts = sharePath.split("/", limit = 2)
        val shareName = parts.getOrNull(0) ?: ""
        val rootPath = parts.getOrNull(1) ?: ""

        if (shareName.isEmpty()) return null

        smbClient.connect(host, user, pass, shareName)
        return rootPath
    }
}
