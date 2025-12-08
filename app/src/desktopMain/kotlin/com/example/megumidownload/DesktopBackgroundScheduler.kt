package com.example.megumidownload

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.io.File

class DesktopBackgroundScheduler(
    private val downloadManager: DownloadManager,
    private val configManager: DesktopConfigManager,
    private val videoProcessor: DesktopVideoProcessor
) : BackgroundScheduler {

    override fun scheduleDownload(force: Boolean) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Mimic DownloadWorker logic
                downloadManager.startDownloadCycle(force)
            } catch (e: Exception) {
                Logger.e("DesktopBackgroundScheduler", "Error in download cycle: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    override fun scheduleRssCheck(intervalMinutes: Long) {
        // Simple polling for Desktop
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                   // Mimic RssCheckWorker logic (Simplified)
                   // TODO: Implement actual RSS check logic
                   Logger.d("BackgroundScheduler", "RSS Check Triggered")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(intervalMinutes * 60 * 1000)
            }
        }
    }

    override fun scheduleUrlDownload(url: String, title: String, series: SeriesEntry, config: DownloadConfig, cookie: String?, userAgent: String?) {
         GlobalScope.launch(Dispatchers.IO) {
            try {
                Logger.i("BackgroundScheduler", "Starting download for $title")
                // On desktop, we can use SystemDownloadManager or simple ktor client.
                // Since this is "auto download" flow, we want to download to a temp location and then process.
                
                // We'll simplistic logic here:
                // Use OkHttp for robust download (GoFile needs cookies/headers)
                val tempFile = File(config.localBasePath, "$title.part")
                val finalName = if (title.endsWith(".mkv", true)) title else "$title.mkv"
                val finalFile = File(config.localBasePath, finalName)

                if (tempFile.exists()) tempFile.delete()
                if (finalFile.exists()) finalFile.delete()

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

                val response = client.newCall(requestBuilder.build()).execute()
                
                if (!response.isSuccessful) {
                    Logger.e("BackgroundScheduler", "Download failed: HTTP ${response.code}")
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    Logger.e("BackgroundScheduler", "Download failed: Empty body")
                    return@launch
                }

                val contentLength = body.contentLength()
                val contentType = body.contentType()?.toString()
                
                if (contentType?.contains("text/html", true) == true) {
                     Logger.e("BackgroundScheduler", "Download failed: Content is HTML (likely error page)")
                     return@launch
                }

                ProgressRepository.startDownload(title)
                
                body.byteStream().use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead = 0L
                        var count: Int
                        var lastUpdate = System.currentTimeMillis()

                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            bytesRead += count

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                lastUpdate = now
                                ProgressRepository.updateProgress(title, bytesRead, if(contentLength > 0) contentLength else -1L)
                            }
                        }
                    }
                }
                
                ProgressRepository.endDownload(title)
                Logger.i("BackgroundScheduler", "File downloaded to ${tempFile.absolutePath}, processing...")
                 
                 // Process
                 val success = downloadManager.processTempFile(tempFile, finalName, config.localBasePath, series)
                 if (success) {
                     Logger.i("BackgroundScheduler", "Download & Process Complete: $title")
                 } else {
                     Logger.e("BackgroundScheduler", "Processing Failed: $title")
                 }

            } catch (e: Exception) {
                Logger.e("BackgroundScheduler", "Download Failed: ${e.message}")
                ProgressRepository.endDownload(title) // Ensure cleanup on error
            }
        }
    }
}
