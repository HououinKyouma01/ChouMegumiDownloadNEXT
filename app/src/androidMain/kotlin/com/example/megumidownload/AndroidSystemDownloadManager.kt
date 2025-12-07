package com.example.megumidownload

import android.content.Context
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment

class AndroidSystemDownloadManager(private val context: Context) : SystemDownloadManager {
    override fun downloadFile(url: String, fileName: String, title: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Downloading $fileName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request) 
    }
}
