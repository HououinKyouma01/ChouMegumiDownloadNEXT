package com.example.megumidownload

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MegumiDownloadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Global Managers
        GlobalReplacementManager.init(filesDir)
        
        // Initialize Notification Channel
        createNotificationChannel()
        
        // Register Bouncy Castle Provider for SSHJ/SFTP
        // Remove existing if any to avoid conflicts
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }


    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Sync Progress"
            val descriptionText = "Shows progress of file synchronization"
            val importance = android.app.NotificationManager.IMPORTANCE_LOW
            val channel = android.app.NotificationChannel("sync_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
