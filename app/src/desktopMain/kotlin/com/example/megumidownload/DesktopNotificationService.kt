package com.example.megumidownload

class DesktopNotificationService : NotificationService {
    override fun showProgressNotification(title: String, message: String, progress: Int, max: Int, indeterminate: Boolean) {
        // Print to console or unimplemented
        Logger.d("Notification", "$title: $message [$progress/$max]")
    }

    override fun showNotification(title: String, message: String) {
        Logger.i("Notification", "$title: $message")
    }
}
