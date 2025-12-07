package com.example.megumidownload

import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class DesktopSystemDownloadManager : SystemDownloadManager {
    override fun downloadFile(url: String, fileName: String, title: String): Long {
        thread {
            try {
                // Determine Downloads folder
                val userHome = System.getProperty("user.home")
                val downloadsDir = File(userHome, "Downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val file = File(downloadsDir, fileName)
                Logger.i("DesktopDownload", "Starting download: $url -> ${file.absolutePath}")
                
                URL(url).openStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.i("DesktopDownload", "Download complete: ${file.absolutePath}")
                // Ideally, show a notification here
            } catch (e: Exception) {
                Logger.e("DesktopDownload", "Download failed: ${e.message}")
            }
        }
        return System.currentTimeMillis() // Return dummy ID
    }
}
