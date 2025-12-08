package com.example.megumidownload

import android.content.Context
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment

class AndroidSystemDownloadManager(private val context: Context) : SystemDownloadManager {
    override fun downloadFile(url: String, fileName: String, title: String, cookie: String?, userAgent: String?): Long {
        // 1. Delete existing file to prevent duplicates (e.g. filename(1).mkv)
        try {
            val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace() // Ignore check, DownloadManager will just rename if it fails
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Downloading $fileName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        // 2. Add Headers (Critical for GoFile)
        if (!cookie.isNullOrBlank()) {
            request.addRequestHeader("Cookie", cookie)
        }
        if (!userAgent.isNullOrBlank()) {
            request.addRequestHeader("User-Agent", userAgent)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request) 
    }
    override fun openLink(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun getDownloadStatus(id: Long): DownloadStatus {
        val query = DownloadManager.Query().setFilterById(id)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(query)
        
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex >= 0) {
                val status = cursor.getInt(statusIndex)
                cursor.close()
                return when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> DownloadStatus.RUNNING
                    else -> DownloadStatus.UNKNOWN
                }
            }
            cursor.close()
        }
        return DownloadStatus.UNKNOWN
    }
}
