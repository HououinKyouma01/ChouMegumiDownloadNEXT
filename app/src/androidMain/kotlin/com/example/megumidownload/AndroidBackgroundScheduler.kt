package com.example.megumidownload

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

class AndroidBackgroundScheduler(private val context: Context) : BackgroundScheduler {

    override fun scheduleDownload() {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "DownloadWorker",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
    
    override fun scheduleRssCheck(intervalMinutes: Long) {
         val clampedInterval = if (intervalMinutes < 15) 15L else intervalMinutes
         
         val workRequest = PeriodicWorkRequestBuilder<RssCheckWorker>(
            clampedInterval, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "RssCheckWorker",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
