package com.example.megumidownload

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

class AndroidBackgroundScheduler(private val context: Context) : BackgroundScheduler {

    override fun scheduleDownload(force: Boolean) {
        val inputData = androidx.work.workDataOf("force" to force)
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "DownloadWorker",
            ExistingWorkPolicy.KEEP, // If auto-start triggers while manual running, ignore. If manual triggers while auto, wait? KEEP is safer.
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

    override fun scheduleUrlDownload(url: String, title: String, series: SeriesEntry, config: DownloadConfig, cookie: String?, userAgent: String?) {
        val gson = com.google.gson.Gson()
        val inputData = androidx.work.workDataOf(
            "url" to url,
            "title" to title,
            "seriesJson" to gson.toJson(series),
            "configJson" to gson.toJson(config),
            "cookie" to cookie,
            "userAgent" to userAgent
        )

        val request = androidx.work.OneTimeWorkRequest.Builder(RssDownloadWorker::class.java)
            .setInputData(inputData)
            .addTag("rss_download")
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            "url_download_${System.currentTimeMillis()}", // Unique-ish to allow parallel
            ExistingWorkPolicy.APPEND,
            request
        )
    }
}
