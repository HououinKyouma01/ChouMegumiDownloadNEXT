package com.example.megumidownload

interface SystemDownloadManager {
    fun downloadFile(url: String, fileName: String, title: String, cookie: String? = null, userAgent: String? = null): Long
    fun openLink(url: String)
    fun getDownloadStatus(id: Long): DownloadStatus
}

enum class DownloadStatus {
    RUNNING, SUCCESSFUL, FAILED, UNKNOWN
}
