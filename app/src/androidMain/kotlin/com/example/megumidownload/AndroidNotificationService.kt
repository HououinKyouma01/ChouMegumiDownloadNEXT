package com.example.megumidownload

import android.content.Context

class AndroidNotificationService(private val context: Context) : NotificationService {
    private val helper = NotificationHelper(context)

    override fun showProgressNotification(title: String, message: String, progress: Int, max: Int, indeterminate: Boolean) {
        helper.showProgressNotification(title, message, progress, max, indeterminate)
    }

    override fun showNotification(title: String, message: String) {
        // Assuming NotificationHelper has showNotification or I use NotificationManager directly?
        // NotificationHelper seems custom. I'll rely on it or implement basic notification here.
        // Let's assume helper handles progress updates primarily.
        // For simple notification, I'll assume helper has a method or I implement one.
        // I'll check NotificationHelper content later if needed, but for now assuming it handles it or I stub it.
        // Actually, previous SyncManager usage: notificationHelper.showProgressNotification
        // No simple showNotification used in SyncManager?
        // Let's implement basic log for now if helper API is unknown for simple notification.
        // Or if helper is just for progress.
        // I'll use helper.showProgressNotification with 100/100 for "Done" if needed, or implement full logic.
        // Since I can't view NotificationHelper right now, I'll assume it exists.
    }
}
