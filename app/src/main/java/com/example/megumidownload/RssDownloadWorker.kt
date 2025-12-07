package com.example.megumidownload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.megumidownload.DownloadConfig
import com.example.megumidownload.SeriesEntry
import com.example.megumidownload.DownloadManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

class RssDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url")
        val filePath = inputData.getString("filePath")
        val seriesJson = inputData.getString("seriesJson")
        val configJson = inputData.getString("configJson")
        val itemTitle = inputData.getString("title") ?: "Unknown"

        if ((url == null && filePath == null) || seriesJson == null || configJson == null) {
            return@withContext Result.failure(workDataOf("error" to "Missing input data"))
        }

        val series = gson.fromJson(seriesJson, SeriesEntry::class.java)
        // We need ConfigManager and SeriesManager to instantiate DownloadManager
        val configManager = ConfigManager(applicationContext)
        val seriesManager = SeriesManager(applicationContext)
        val downloadManager = DownloadManager(applicationContext, configManager, seriesManager)

        try {
            if (url != null) {
                // --- Auto-Download Mode ---
                setProgress(workDataOf("progress" to 0f, "status" to "Downloading..."))
                
                // Download to a known temp file
                // Use the item title for the filename temporarily, but ensure .mkv
                val tempName = "$itemTitle.mkv"
                val tempFile = File(applicationContext.cacheDir, tempName)
                
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connect()
                    
                    if (connection.responseCode !in 200..299) {
                        return@withContext Result.failure(workDataOf("error" to "HTTP Error ${connection.responseCode}"))
                    }

                    val length = connection.contentLengthLong
                    val input = connection.inputStream
                    val output = FileOutputStream(tempFile)
                    val buffer = ByteArray(8192)
                    var bytesRead = 0L
                    var count: Int

                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                        bytesRead += count
                        if (length > 0) {
                            val progress = (bytesRead.toFloat() / length.toFloat()) * 0.25f // First 25% is download
                            setProgress(workDataOf("progress" to progress, "status" to "Downloading..."))
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()
                    
                    // Now Process using DownloadManager
                    setProgress(workDataOf("progress" to 0.3f, "status" to "Processing..."))
                    val downloadConfig = configManager.getDownloadConfig()
                    
                    val success = downloadManager.processTempFile(
                        localTempFile = tempFile,
                        originalFileName = tempName,
                        localDestBasePath = downloadConfig.localBasePath,
                        series = series
                    )
                    
                    if (success) {
                        setProgress(workDataOf("progress" to 1f, "status" to "Complete"))
                        return@withContext Result.success()
                    } else {
                        return@withContext Result.failure(workDataOf("error" to "Processing Failed"))
                    }

                } catch (e: Exception) {
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext Result.failure(workDataOf("error" to "Download failed: ${e.message}"))
                }
            } else if (filePath != null) {
                // --- Auto-Scan (Local File) Mode ---
                setProgress(workDataOf("progress" to 0.1f, "status" to "Starting processing..."))
                
                val sourceFile = File(filePath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(workDataOf("error" to "File not found"))
                }
                
                val downloadConfig = configManager.getDownloadConfig()
                
                // Reuse DownloadManager.processFile which handles "Copy to Temp -> Process -> Move"
                // It also handles "Delete Source" which matches "Move" behavior
                downloadManager.processFile(
                    sftp = null,
                    fileName = sourceFile.name,
                    sourceBasePath = sourceFile.parent ?: "",
                    localDestBasePath = downloadConfig.localBasePath,
                    series = series
                )
                
                setProgress(workDataOf("progress" to 1f, "status" to "Complete"))
                return@withContext Result.success()
            }
            
            return@withContext Result.failure(workDataOf("error" to "Unknown mode"))
            
        } catch (e: Exception) {
            return@withContext Result.failure(workDataOf("error" to "Exception: ${e.message}"))
        }
    }
}
