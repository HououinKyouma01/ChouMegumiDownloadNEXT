package com.example.megumidownload

interface NotificationService {
    fun showProgressNotification(title: String, message: String, progress: Int, max: Int, indeterminate: Boolean = false)
    fun showNotification(title: String, message: String)
}
