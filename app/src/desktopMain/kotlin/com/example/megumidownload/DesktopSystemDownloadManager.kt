package com.example.megumidownload

import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class DesktopSystemDownloadManager : SystemDownloadManager {
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<Long, DownloadStatus>()

    override fun downloadFile(url: String, fileName: String, title: String, cookie: String?, userAgent: String?): Long {
        val id = System.currentTimeMillis()
        activeDownloads[id] = DownloadStatus.RUNNING
        
        thread {
            try {
                // ... same logic ...
                // Determine Downloads folder
                val userHome = System.getProperty("user.home")
                val downloadsDir = File(userHome, "Downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val file = File(downloadsDir, fileName)
                if (file.exists()) file.delete()
                
                Logger.i("DesktopDownload", "Starting download: $url -> ${file.absolutePath}")
                
                val connection = URL(url).openConnection()
                if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
                if (!userAgent.isNullOrBlank()) connection.setRequestProperty("User-Agent", userAgent)
                
                connection.getInputStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.i("DesktopDownload", "Download complete: ${file.absolutePath}")
                activeDownloads[id] = DownloadStatus.SUCCESSFUL
            } catch (e: Exception) {
                Logger.e("DesktopDownload", "Download failed: ${e.message}")
                activeDownloads[id] = DownloadStatus.FAILED
            }
        }
        return id
    }
    
    override fun getDownloadStatus(id: Long): DownloadStatus {
        return activeDownloads[id] ?: DownloadStatus.UNKNOWN
    }
    
    override fun openLink(url: String) {
        // ... same ...
        thread {
             try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                }
            } catch (e: Exception) {
                Logger.e("DesktopDownload", "Failed to open link: ${e.message}")
            }
        }
    }
}
