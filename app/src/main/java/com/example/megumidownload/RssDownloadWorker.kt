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
                // Initialize Foreground
                setForeground(createForegroundInfo(0f, "Starting $itemTitle..."))
                
                val cookie = inputData.getString("cookie")
                val userAgent = inputData.getString("userAgent")

                // Download to a known temp file
                val tempName = "$itemTitle.mkv"
                val tempFile = File(applicationContext.cacheDir, tempName)
                
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val requestBuilder = okhttp3.Request.Builder().url(url)
                    if (!cookie.isNullOrBlank()) {
                        requestBuilder.addHeader("Cookie", cookie)
                    }
                    if (!userAgent.isNullOrBlank()) {
                        requestBuilder.addHeader("User-Agent", userAgent)
                    }
                    
                    val request = requestBuilder.build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(workDataOf("error" to "HTTP Error ${response.code}"))
                    }
                    
                    val responseBody = response.body
                    if (responseBody == null) {
                         return@withContext Result.failure(workDataOf("error" to "Empty response body"))
                    }

                    // Validation
                    val contentType = responseBody.contentType()?.toString() ?: ""
                    val contentLength = responseBody.contentLength()
                    
                    if (contentLength < 10 * 1024 * 1024 && contentLength != -1L) { // < 10MB
                        return@withContext Result.failure(workDataOf("error" to "File too small ($contentLength bytes). Likely an error page. Type: $contentType"))
                    }
                    
                    if (contentType.contains("text/html", true)) {
                         return@withContext Result.failure(workDataOf("error" to "Invalid content type: $contentType. Downloaded an HTML page instead of video."))
                    }

                    val input = responseBody.byteStream()
                    val output = java.io.BufferedOutputStream(FileOutputStream(tempFile))
                    val buffer = ByteArray(64 * 1024) 
                    var bytesRead = 0L
                    var count: Int
                    
                    // Safe length for progress if unknown
                    val totalLength = if (contentLength > 0) contentLength else 100 * 1024 * 1024 // arbitrary 100MB for progress calc if unknown

                    while (input.read(buffer).also { count = it } != -1) {
                         if (isStopped) {
                            output.close()
                            input.close()
                            response.close()
                            tempFile.delete()
                            return@withContext Result.failure(workDataOf("error" to "Cancelled"))
                        }
                        output.write(buffer, 0, count)
                        bytesRead += count
                        
                        val rawProgress = if (contentLength > 0) {
                            (bytesRead.toFloat() / contentLength.toFloat())
                        } else {
                            0f
                        }
                        
                        // Map Download to 0% -> 95%
                        val progress = rawProgress * 0.95f
                        val progressMb = bytesRead / 1024 / 1024
                        val totalMb = if (contentLength > 0) contentLength / 1024 / 1024 else 0
                        
                        val statusMsg = if (totalMb > 0L) "Downloading $itemTitle ($progressMb/${totalMb}MB)" else "Downloading $itemTitle ($progressMb MB)"
                        
                        // Update both Worker Progress (for UI) and Foreground Notification
                        val progressData = workDataOf("progress" to progress, "status" to statusMsg)
                        setProgress(progressData)
                        setForeground(createForegroundInfo(progress, statusMsg))
                    }
                    output.flush()
                    output.close()
                    input.close()
                    response.close()
                    
                    // Now Process using DownloadManager
                    val processingMsg = "Processing $itemTitle..."
                    setProgress(workDataOf("progress" to 0.95f, "status" to processingMsg))
                    setForeground(createForegroundInfo(0.95f, processingMsg))

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
