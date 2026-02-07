package com.example.megumidownload

import com.example.megumidownload.viewmodel.LogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.regex.Pattern

class DownloadManager(
    private val configManager: ConfigManager,
    private val seriesManager: SeriesManager,
    private val videoProcessor: VideoProcessor,
    private val cacheDir: File,
    private val notificationService: NotificationService
) {
    private val TAG = "DownloadManager"

    suspend fun startDownloadCycle(force: Boolean = false) = withContext(Dispatchers.IO) {
        log("Starting download cycle (Force=$force)...")
        
        val config = configManager.getDownloadConfig()
        // Double check auto-start if not forced
        if (!force) {
            val autoStart = configManager.autoStart.first()
            if (!autoStart) {
                log("Auto-start disabled in config. Aborting cycle.")
                return@withContext
            }
        }
        val host = config.host
        val user = config.user
        val password = config.pass
        val remotePath = config.remotePath
        val localBasePath = config.localBasePath
        val localOnly = config.localOnly
        val localSourcePath = config.localSourcePath

        if (localBasePath.isBlank()) {
            log("Local Destination Path is missing. Please check settings.", LogType.ERROR)
            return@withContext
        }

        if (localOnly) {
            // Local processing remains sequential as it's just move/copy/process
            if (localSourcePath.isBlank()) {
                log("Local Source Path is missing. Please check settings.", LogType.ERROR)
                return@withContext
            }
            processLocalFiles(localSourcePath, localBasePath)
        } else {
            if (host.isBlank() || user.isBlank()) {
                log("SFTP Configuration missing. Please check settings.", LogType.ERROR)
                return@withContext
            }
            processRemoteFiles(host, user, password, remotePath, localBasePath, localSourcePath)
        }
    }

    private suspend fun processLocalFiles(sourcePath: String, destPath: String) {
        log("Scanning local source: $sourcePath")
        val sourceDir = File(sourcePath)
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            log("Source directory does not exist or is not a directory.", LogType.ERROR)
            return
        }

        val files = sourceDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mkv", ignoreCase = true) } ?: emptyList()
        log("Found ${files.size} MKV files.")
        
        ProgressRepository.setTotalFiles(files.size)

        val seriesList = seriesManager.getSeriesList()

        for (file in files) {
            val matchedSeries = seriesList.find { file.name.contains(it.fileNameMatch, ignoreCase = true) }
            if (matchedSeries != null) {
                // Local "download" is just copy, so we treat it as fetch + process immediately
                processFile(null, file.name, sourcePath, destPath, matchedSeries)
            }
            ProgressRepository.incrementProcessedFiles()
        }
        ProgressRepository.reset()
    }

    private suspend fun processRemoteFiles(host: String, user: String, pass: String, remotePath: String, localBasePath: String, localSourcePath: String) {
        // We need a fresh scope or client for the listing
        var mainSftp: SftpClientWrapper? = null
        try {
            mainSftp = SftpClientWrapper(host = host, username = user, password = pass)
            log("Connecting to $host...")
            mainSftp.connect()
            log("Connected.", LogType.SUCCESS)
            
            val files = mainSftp.listFiles(remotePath)
            val mkvFiles = files.filter { it.endsWith(".mkv", ignoreCase = true) }
            
            log("Found ${mkvFiles.size} MKV files.")
            ProgressRepository.setTotalFiles(mkvFiles.size)
            
            val seriesList = seriesManager.getSeriesList()
            
            val filesToProcess = mkvFiles.mapNotNull { fileName ->
                val matchedSeries = seriesManager.getSeriesList().find { fileName.contains(it.fileNameMatch, ignoreCase = true) }
                if (matchedSeries != null) fileName to matchedSeries else null
            }
            
            val parallelDownloads = configManager.parallelDownloads.first().coerceAtLeast(1)
            val batchProcessing = configManager.batchProcessing.first()
            
            if (batchProcessing) {
                log("Batch Processing Enabled. Parallel Downloads: $parallelDownloads")
                // 1. Download Phase
                val semaphore = Semaphore(parallelDownloads)
                val downloadedFiles = mutableListOf<Triple<File, String, SeriesEntry>>() // TempFile, OriginalName, Series

                coroutineScope {
                    val deferredDownloads = filesToProcess.map { (fileName, series) ->
                        async(Dispatchers.IO) {
                           semaphore.withPermit {
                               // Start tracking progress for this file
                               ProgressRepository.startDownload(fileName)
                               
                               // Create a dedicated SFTP client for this thread/download
                               val sftp = SftpClientWrapper(host = host, username = user, password = pass)
                               try {
                                   sftp.connect()
                                   val tempFile = fetchFile(sftp, fileName, remotePath, localSourcePath, isBatch = true)
                                   if (tempFile != null) {
                                       synchronized(downloadedFiles) {
                                           downloadedFiles.add(Triple(tempFile, fileName, series))
                                       }
                                       // Delete remote file immediately after successful download if desired? 
                                       // Original logic deletes AFTER processing success.
                                       // If we wait for all, we might carry many temp files.
                                       // Let's stick to: Download ALL -> Process ALL.
                                       // Remote deletion should probably happen after processing success?
                                       // If we delete now, and processing fails later, we lost the file from remote.
                                       // So we must NOT delete yet.
                                   }
                               } catch (e: Exception) {
                                   log("Download failed for $fileName: ${e.message}", LogType.ERROR)
                               } finally {
                                   sftp.disconnect()
                                   ProgressRepository.endDownload(fileName)
                               }
                           }
                        }
                    }
                    deferredDownloads.awaitAll()
                }
                
                log("Batch Download Complete. Starting Processing...")
                
                // 2. Processing Phase
                // Sequential processing (less resource intensive for ffmpeg/mkvmerge) usually better
                for ((tempFile, fileName, series) in downloadedFiles) {
                     val success = processTempFile(tempFile, fileName, localBasePath, series)
                     if (success) {
                         try {
                             mainSftp.delete("$remotePath/$fileName")
                             log("Deleted remote file: $fileName")
                         } catch (e: Exception) {
                             log("Failed to delete remote file: ${e.message}", LogType.WARNING)
                         }
                     }
                     ProgressRepository.incrementProcessedFiles()
                }

            } else {
                // Traditional: Download #1 -> Process #1 -> Download #2...
                for ((fileName, series) in filesToProcess) {
                    processFile(mainSftp, fileName, remotePath, localBasePath, series, localSourcePath)
                    ProgressRepository.incrementProcessedFiles()
                }
            }
            
        } catch (e: Exception) {
            log("Error: ${e.message}", LogType.ERROR)
            e.printStackTrace()
        } finally {
            mainSftp?.disconnect()
            log("Disconnected.")
            ProgressRepository.reset()
        }
    }

    // Legacy integrated method (Sequential)
    suspend fun processFile(
        sftp: SftpClientWrapper?,
        fileName: String,
        sourceBasePath: String,
        localDestBasePath: String,
        series: SeriesEntry,
        localSourcePath: String = ""
    ) {
        log("Processing $fileName for series ${series.folderName}...")
        
        // 1. Fetch
        val tempDir = if (localSourcePath.isNotBlank()) {
            File(localSourcePath).apply { if (!exists()) mkdirs() }
        } else {
            cacheDir
        }
        val localTempFile = File(tempDir, fileName)
        
        var fetchSuccess = false
        if (sftp != null) {
             try {
                // Re-use passed SFTP (Linear execution)
                notificationService.showProgressNotification(fileName, "Downloading...", 0, 100, true)
                var lastProgressTime = 0L
                ProgressRepository.startDownload(fileName)
                
                 sftp.downloadFile("$sourceBasePath/$fileName", localTempFile.absolutePath) { bytesRead, totalBytes ->
                      val now = System.currentTimeMillis()
                      if (now - lastProgressTime > 500) { 
                         lastProgressTime = now
                         ProgressRepository.updateProgress(fileName, bytesRead, totalBytes)
                         
                         val percentage = if (totalBytes > 0) ((bytesRead.toDouble() / totalBytes.toDouble()) * 100).toInt() else -1
                         val mbRead = bytesRead / 1024 / 1024
                         val mbTotal = totalBytes / 1024 / 1024
                         val status = if (totalBytes > 0) "Downloading ($mbRead/${mbTotal} MB)" else "Downloading ($mbRead MB)"
                         notificationService.showProgressNotification(fileName, status, percentage, 100, false)
                      }
                 }
                notificationService.showNotification(fileName, "Download Complete")
                fetchSuccess = true
            } catch (e: Exception) {
                log("Fetch failed: ${e.message}", LogType.ERROR)
                notificationService.showNotification("Download Failed", "Error downloading $fileName: ${e.message}")
            } finally {
                ProgressRepository.endDownload(fileName)
                ProgressRepository.setIdle() // Clear single-file progress
            }
        } else {
             // Local copy
             val sourceFile = File(sourceBasePath, fileName)
             if (sourceFile.exists()) {
                 sourceFile.copyTo(localTempFile, overwrite = true)
                 fetchSuccess = true
             }
        }

        if (!fetchSuccess) return

        // 2. Process
        val success = processTempFile(localTempFile, fileName, localDestBasePath, series)
        
        if (success) {
            // Delete remote/source
            if (sftp != null) {
                try {
                    sftp.delete("$sourceBasePath/$fileName")
                    log("Deleted remote file: $fileName")
                } catch (e: Exception) {
                    log("Failed to delete remote file: ${e.message}", LogType.WARNING)
                }
            } else {
                 val sourceFile = File(sourceBasePath, fileName)
                 if (sourceFile.exists() && sourceFile.delete()) {
                     log("Deleted local source file: $fileName")
                 }
            }
        }
    }
    
    // New Helper: Just Fetch
    private suspend fun fetchFile(
        sftp: SftpClientWrapper,
        fileName: String,
        sourceBasePath: String,
        localSourcePath: String,
        isBatch: Boolean = false
    ): File? {
        val tempDir = if (localSourcePath.isNotBlank()) {
            File(localSourcePath).apply { if (!exists()) mkdirs() }
        } else {
            cacheDir
        }
        val localTempFile = File(tempDir, fileName)
        
        log("Downloading $fileName...")
        return try {
            var lastProgressTime = 0L
            var lastBytesRead = 0L
            
            sftp.downloadFile("$sourceBasePath/$fileName", localTempFile.absolutePath) { bytesRead, totalBytes ->
                 val now = System.currentTimeMillis()
                 if (now - lastProgressTime > 500) {  // Update UI every 500ms for stable speed reading
                    val deltaBytes = bytesRead - lastBytesRead
                    val deltaTime = now - lastProgressTime
                    
                    if (deltaTime > 0) {
                        val speedBps = (deltaBytes.toDouble() / deltaTime.toDouble() * 1000).toLong() // Bytes per second
                        ProgressRepository.updateSpeed(fileName, speedBps)
                    }
                    
                    lastProgressTime = now
                    lastBytesRead = bytesRead
                    // Only update primary global state if NOT in batch mode to prevent flickering
                    ProgressRepository.updateProgress(fileName, bytesRead, totalBytes, updatePrimary = !isBatch)
                 }
            }
            log("Download complete: $fileName", LogType.SUCCESS)
            localTempFile
        } catch (e: Exception) {
            log("Error downloading $fileName: ${e.message}", LogType.ERROR)
            if (localTempFile.exists()) localTempFile.delete()
            null
        }
    }

    suspend fun processTempFile(
        localTempFile: File,
        originalFileName: String,
        localDestBasePath: String,
        series: SeriesEntry,
        fixTimingOverride: Boolean? = null
    ): Boolean {
        // 2. Extract Episode Number
        val epMatch = Regex("(?i)(?:s\\d{1,2}e|-|\\s|ep|episode)\\s*(\\d{1,3})(?:v\\d)?(?:end)?(?:[\\s\\[\\(._-]|\$)").find(originalFileName)
        val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull()?.toString()?.padStart(2, '0') ?: "00"
        
        // 3. Prepare Destination
        val destDir = File(localDestBasePath, "${series.folderName}/Season ${series.seasonNumber}")
        if (!destDir.exists()) destDir.mkdirs()
        
        // Naming Logic
        val config = configManager.getDownloadConfig()
        val prefixSeriesName = config.prefixSeriesName
        val appendQuality = config.appendQuality
        
        val nameParts = mutableListOf<String>()
        if (prefixSeriesName) {
            nameParts.add(series.folderName)
        }
        nameParts.add("S${series.seasonNumber.padStart(2, '0')}E$epNum")
        
        if (appendQuality) {
            val qualityMatch = Regex("(?i)\\[([^\\]]*?(?:BD|WEB|HDTV|720p|1080p|2160p)[^\\]]*)\\]").find(originalFileName)
            if (qualityMatch != null) {
                nameParts.add("[${qualityMatch.groupValues[1]}]")
            }
        }
        
        val newName = nameParts.joinToString(" - ") + ".mkv"
        val destFile = File(destDir, newName)
        
        // 4. Process (Fix Timing / Text Replace)
        var fileToMove = localTempFile
        
        // Locate replace.txt
        val tempDir = localTempFile.parentFile
        var replaceFile: File? = null
        val localReplaceFile = File(destDir, "replace.txt")
        
        // Handle replaceUrl download
        if (series.replaceUrl.isNotBlank()) {
            log("Checking remote replace URL: ${series.replaceUrl}")
            try {
                val url = java.net.URL(series.replaceUrl)
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val content = connection.getInputStream().bufferedReader().readText()
                
                // Verify syntax
                val lines = content.lines()
                val invalidLines = lines.filter { it.isNotBlank() && !it.trim().startsWith("#") && !it.contains("|") }
                
                if (invalidLines.isEmpty()) {
                    localReplaceFile.writeText(content)
                    log("Updated replace.txt from URL.", LogType.SUCCESS)
                } else {
                    log("Invalid syntax in remote replace file. Keeping existing one.", LogType.WARNING)
                }
            } catch (e: Exception) {
                log("Failed to download replace file from URL: ${e.message}", LogType.WARNING)
            }
        }

        if (localReplaceFile.exists()) {
            replaceFile = localReplaceFile
        }
        
        val processedFile = File(tempDir, "processed_$originalFileName")
        
        // We process if fixTiming is ON OR if we have a replace file
        val doFixTiming = fixTimingOverride ?: series.fixTiming
        if (doFixTiming || replaceFile != null) {
             // If manual reprocess (fixTimingOverride != null), we might want to log it
             log("Processing video (Timing: $doFixTiming [Override: ${fixTimingOverride != null}], Replace: ${replaceFile != null})...")
             // Offset is 0 unless we calculate it. Current logic uses 0.
             val offset = 0L 
             
             val success = videoProcessor.processVideo(localTempFile, processedFile, offset, replaceFile, doFixTiming)
             if (success) {
                 fileToMove = processedFile
                 log("Processing complete.", LogType.SUCCESS)
             } else {
                 log("Processing failed. Using original.", LogType.WARNING)
             }
        }
        
        // 5. Move
        log("Moving to $destFile...")
        try {
            if (destFile.exists()) destFile.delete()
            fileToMove.copyTo(destFile)
            
            // Cleanup temps
            if (localTempFile.exists()) localTempFile.delete()
            if (processedFile.exists()) processedFile.delete()
            log("Moved successfully.", LogType.SUCCESS)
            
            // 6. Save to filelist.txt
            try {
                val fileList = File(destDir, "filelist.txt")
                fileList.appendText("$originalFileName ($newName)\n")
            } catch (e: Exception) {
                log("Failed to update filelist.txt: ${e.message}", LogType.ERROR)
            }
            return true
            
        } catch (e: Exception) {
            log("Move failed: ${e.message}", LogType.ERROR)
            return false
        }
    }

    suspend fun reprocessEpisode(series: SeriesEntry, videoFile: File) {
        log("Starting Reprocess for ${videoFile.name}...")
        
        val config = configManager.getDownloadConfig()
        val localSourcePath = config.localSourcePath
        if (localSourcePath.isBlank()) {
            log("Cannot reprocess: Local Source Path is not set in settings.", LogType.ERROR)
            return
        }
        
        val destDir = videoFile.parentFile
        val fileListFile = File(destDir, "filelist.txt")
        var originalName = videoFile.name
        
        // 1. Update filelist.txt and find original name
        if (fileListFile.exists()) {
             val lines = fileListFile.readLines()
             val newLines = mutableListOf<String>()
             var found = false
             
             for (line in lines) {
                 // Format: OriginalName (FinalName.mkv)
                 // We look for FinalName.mkv closing parenthesis? or just contains
                 if (line.contains("(${videoFile.name})")) {
                     found = true
                     // Try to extract original name: "Original.mkv (Final.mkv)"
                     val parts = line.split(" (${videoFile.name})")
                     if (parts.isNotEmpty()) {
                         originalName = parts[0].trim()
                     }
                     log("Removed from filelist.txt: $line")
                 } else {
                     newLines.add(line)
                 }
             }
             
             if (found) {
                 fileListFile.writeText(newLines.joinToString("\n") + "\n")
             } else {
                 log("Could not find entry in filelist.txt. Using current name.", LogType.WARNING)
             }
        }
        
        // 2. Move to Local Source (Drop Folder)
        val sourceDir = File(localSourcePath)
        if (!sourceDir.exists()) sourceDir.mkdirs()
        
        val targetFile = File(sourceDir, originalName)
        
        try {
            if (videoFile.renameTo(targetFile)) {
                log("Moved back to Jump/Drop folder: ${targetFile.name}")
                
                // 3. Process
                processTempFile(
                    localTempFile = targetFile,
                    originalFileName = originalName,
                    localDestBasePath = config.localBasePath,
                    series = series,
                    fixTimingOverride = false // FORCE SKIP TIMING
                )
            } else {
                // Try copy + delete if rename fails (cross-filesystem)
                videoFile.copyTo(targetFile, overwrite = true)
                videoFile.delete()
                log("Moved (Copy/Delete) back to Jump/Drop folder: ${targetFile.name}")
                
                processTempFile(
                    localTempFile = targetFile,
                    originalFileName = originalName,
                    localDestBasePath = config.localBasePath,
                    series = series,
                    fixTimingOverride = false
                )
            }
        } catch (e: Exception) {
            log("Failed to move file for reprocessing: ${e.message}", LogType.ERROR)
        }
    }

    private fun log(message: String, type: LogType = LogType.INFO) {
        LogRepository.addLog(message, type)
        Logger.d(TAG, message)
    }
}
