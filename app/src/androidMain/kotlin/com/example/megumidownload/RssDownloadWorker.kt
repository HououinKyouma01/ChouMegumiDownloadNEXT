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
        val configManager = AndroidConfigManager(applicationContext)
        val seriesManager = SeriesManager(applicationContext.filesDir)
        val videoProcessor = AndroidVideoProcessor(applicationContext)
        val notificationService = AndroidNotificationService(applicationContext)
        val downloadManager = DownloadManager(configManager, seriesManager, videoProcessor, applicationContext.cacheDir, notificationService)

        try {
            if (url != null) {
                // --- Auto-Download Mode ---
                val downloadConfig = configManager.getDownloadConfig()
                
                // Determine download directory: Prefer localSourcePath if set, else Cache
                val downloadDir = if (downloadConfig != null && downloadConfig.localSourcePath.isNotBlank()) {
                     val f = File(downloadConfig.localSourcePath)
                     if (f.exists() && f.isDirectory && f.canWrite()) f else applicationContext.cacheDir
                } else {
                     applicationContext.cacheDir
                }

                // Initialize Foreground
                setForeground(createForegroundInfo(0f, "Starting $itemTitle..."))
                
                val cookie = inputData.getString("cookie")
                val userAgent = inputData.getString("userAgent")

                // Sanitize filename to ensure it matches what file system allows (and what Scanner sees)
                val safeTitle = itemTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")

                // Download to the chosen directory
                // Use .part extension to prevent premature scanning/processing
                val finalName = if (safeTitle.endsWith(".mkv", true)) safeTitle else "$safeTitle.mkv"
                val tempName = "$finalName.part"
                val tempFile = File(downloadDir, tempName)
                val finalFile = File(downloadDir, finalName)

                // Cleanup previous partials or existing finals if we are forcing a redownload (worker unique logic handles queue, but file system might have leftovers)
                if (tempFile.exists()) tempFile.delete()
                if (finalFile.exists()) finalFile.delete()
                
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
                    
                    var lastUpdate = System.currentTimeMillis()
                    
                    ProgressRepository.startDownload(itemTitle)

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
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) { // Throttle updates: Max 2 per second
                            lastUpdate = now
                            
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
                            
                            ProgressRepository.updateProgress(itemTitle, bytesRead, if (contentLength > 0) contentLength else -1L)
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()
                    response.close()
                    
                    ProgressRepository.endDownload(itemTitle)
                    
                    // Download Complete.
                    // We DO NOT rename to .mkv here. We pass the .part file to DownloadManager.
                    // This ensures the Auto-Scanner (which ignores .part) doesn't pick it up during processing.
                    
                    val processingMsg = "Processing $itemTitle..."
                    setProgress(workDataOf("progress" to 0.95f, "status" to processingMsg))
                    setForeground(createForegroundInfo(0.95f, processingMsg))

                    val downloadConfig = configManager.getDownloadConfig()
                    
                    // Pass the .part file (tempFile) as the "localTempFile"
                    // Pass the finalName (.mkv) as "originalFileName" so the logic knows what it IS
                    val success = downloadManager.processTempFile(
                        localTempFile = tempFile,
                        originalFileName = finalName,
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
                    if (finalFile.exists()) finalFile.delete()
                    if (tempFile.exists()) tempFile.delete()
                    if (finalFile.exists()) finalFile.delete()
                    ProgressRepository.endDownload(itemTitle)
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


    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        try {
            val itemTitle = inputData.getString("title") ?: "Download"
            return createForegroundInfo(0f, "Starting $itemTitle...")
        } catch (e: Exception) {
            // Hard coded fallback 
             val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "com.example.megumidownload.notification.DOWNLOAD_CHANNEL")
                .setContentTitle("Megumi Downloader")
                .setContentText("Resuming...")
                .setSmallIcon(R.drawable.ic_launcher)
                .build()
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                return androidx.work.ForegroundInfo(1337, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
             }
             return androidx.work.ForegroundInfo(1337, notification)
        }
    }

    private fun createForegroundInfo(progress: Float, status: String): androidx.work.ForegroundInfo {
        val id = "com.example.megumidownload.notification.DOWNLOAD_CHANNEL"
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(id, "Downloads", android.app.NotificationManager.IMPORTANCE_LOW)
                val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }

            val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, id)
                .setContentTitle("Megumi Downloader")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setProgress(100, (progress * 100).toInt(), false)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(getId()))
                .build()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                return androidx.work.ForegroundInfo(
                    1337, 
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
            return androidx.work.ForegroundInfo(1337, notification)
        } catch (e: Exception) {
             val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, id)
                .setContentTitle("Megumi Downloader")
                .setContentText("Processing...")
                .setSmallIcon(R.drawable.ic_launcher)
                .build()
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                return androidx.work.ForegroundInfo(
                    1337, 
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
             }
             return androidx.work.ForegroundInfo(1337, notification)
        }
    }
}
