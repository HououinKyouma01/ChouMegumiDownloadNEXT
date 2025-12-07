package com.example.megumidownload

import com.example.megumidownload.viewmodel.LogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

class DownloadManager(
    private val configManager: ConfigManager,
    private val seriesManager: SeriesManager,
    private val videoProcessor: VideoProcessor,
    private val cacheDir: File
) {
    private val TAG = "DownloadManager"


    suspend fun startDownloadCycle() = withContext(Dispatchers.IO) {
        log("Starting download cycle...")
        
        val config = configManager.getDownloadConfig()
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
            processRemoteFiles(host, user, password, remotePath, localBasePath)
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
                processFile(null, file.name, sourcePath, destPath, matchedSeries)
            }
            ProgressRepository.incrementProcessedFiles()
        }
        ProgressRepository.reset()
    }

    private suspend fun processRemoteFiles(host: String, user: String, pass: String, remotePath: String, localBasePath: String) {
        val sftp = SftpClientWrapper(host = host, username = user, password = pass)
        try {
            log("Connecting to $host...")
            sftp.connect()
            log("Connected.", LogType.SUCCESS)
            
            val files = sftp.listFiles(remotePath)
            val mkvFiles = files.filter { it.endsWith(".mkv", ignoreCase = true) }
            
            log("Found ${mkvFiles.size} MKV files.")
            
            val seriesList = seriesManager.getSeriesList()
            
            for (fileName in mkvFiles) {
                val matchedSeries = seriesList.find { fileName.contains(it.fileNameMatch, ignoreCase = true) }
                
                if (matchedSeries != null) {
                    processFile(sftp, fileName, remotePath, localBasePath, matchedSeries)
                }
            }
            
        } catch (e: Exception) {
            log("Error: ${e.message}", LogType.ERROR)
            e.printStackTrace()
        } finally {
            sftp.disconnect()
            log("Disconnected.")
        }
    }

    suspend fun processFile(
        sftp: SftpClientWrapper?,
        fileName: String,
        sourceBasePath: String,
        localDestBasePath: String,
        series: SeriesEntry
    ) {
        log("Processing $fileName for series ${series.folderName}...")
        
        log("Processing $fileName for series ${series.folderName}...")
        
        val tempDir = cacheDir
        val localTempFile = File(tempDir, fileName)
        
        // 1. Fetch (Download or Copy)
        log("Fetching $fileName...")
        try {
            if (sftp != null) {
                sftp.downloadFile("$sourceBasePath/$fileName", localTempFile.absolutePath)
            } else {
                // Local copy
                val sourceFile = File(sourceBasePath, fileName)
                if (sourceFile.exists()) {
                    sourceFile.copyTo(localTempFile, overwrite = true)
                } else {
                    throw Exception("Source file not found: ${sourceFile.absolutePath}")
                }
            }
            log("Fetch complete.", LogType.SUCCESS)
        } catch (e: Exception) {
            log("Fetch failed: ${e.message}", LogType.ERROR)
            return
        }
        
        // Delegate processing
        val success = processTempFile(localTempFile, fileName, localDestBasePath, series)
        
        if (success) {
            // Delete remote file or local source file
            if (sftp != null) {
                try {
                    sftp.delete("$sourceBasePath/$fileName")
                    log("Deleted remote file: $fileName")
                } catch (e: Exception) {
                    log("Failed to delete remote file: ${e.message}", LogType.WARNING)
                }
            } else {
                // Local source file deletion
                try {
                    val sourceFile = File(sourceBasePath, fileName)
                    if (sourceFile.exists()) {
                        if (sourceFile.delete()) {
                            log("Deleted local source file: $fileName")
                        } else {
                            log("Failed to delete local source file: $fileName", LogType.WARNING)
                        }
                    }
                } catch (e: Exception) {
                    log("Failed to delete local source file: ${e.message}", LogType.WARNING)
                }
            }
        }
    }

    suspend fun processTempFile(
        localTempFile: File,
        originalFileName: String,
        localDestBasePath: String,
        series: SeriesEntry
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
                
                // Verify syntax (simple check: non-empty lines must contain '|')
                val lines = content.lines()
                val invalidLines = lines.filter { it.isNotBlank() && !it.trim().startsWith("#") && !it.contains("|") }
                
                if (invalidLines.isEmpty()) {
                    localReplaceFile.writeText(content)
                    log("Updated replace.txt from URL.", LogType.SUCCESS)
                } else {
                    log("Invalid syntax in remote replace file. Keeping existing one. Invalid lines: ${invalidLines.take(3)}", LogType.WARNING)
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
        if (series.fixTiming || replaceFile != null) {
             log("Processing video (Timing: ${series.fixTiming}, Replace: ${replaceFile != null})...")
             // If fixTiming is false, offset is 0. If true, we need logic (currently 0 as placeholder)
             val offset = if (series.fixTiming) 0L else 0L 
             
             val success = videoProcessor.processVideo(localTempFile, processedFile, offset, replaceFile)
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
                log("Updated filelist.txt")
            } catch (e: Exception) {
                log("Failed to update filelist.txt: ${e.message}", LogType.ERROR)
            }
            return true
            
        } catch (e: Exception) {
            log("Move failed: ${e.message}", LogType.ERROR)
            return false
        }
    }

    private fun log(message: String, type: LogType = LogType.INFO) {
        LogRepository.addLog(message, type)
        Logger.d(TAG, message)
    }
}
