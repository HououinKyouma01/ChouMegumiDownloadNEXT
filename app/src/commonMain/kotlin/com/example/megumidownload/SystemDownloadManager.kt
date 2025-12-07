package com.example.megumidownload

interface SystemDownloadManager {
    fun downloadFile(url: String, fileName: String, title: String): Long
}
