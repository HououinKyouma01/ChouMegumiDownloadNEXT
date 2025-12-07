package com.example.megumidownload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class RssCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "RssCheckWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val configManager = AndroidConfigManager(applicationContext)
        val seriesManager = SeriesManager(applicationContext.filesDir)
        val rssRepository = RssRepository()

        // 1. Get Last Check Time and Interval
        val lastCheckTime = configManager.rssLastCheckTime.first()
        val rssGroup = configManager.rssGroup.first()
        val quality = configManager.rssQuality.first()

        // Safety check (if manually triggered or scheduled)
        // We rely on WorkManager interval, but good to know context
        Log.d(TAG, "Starting RSS Check. Last Check: $lastCheckTime")

        val seriesList = seriesManager.getSeriesList()
        val notifySeries = seriesList.filter { it.notify }
        
        if (notifySeries.isEmpty()) {
            return@withContext Result.success()
        }

        var newEpisodesCount = 0
        val newEpisodesDetails = mutableListOf<String>()
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

        // 2. Iterate Series
        for (series in notifySeries) {
            val targetGroup = if (!series.overrideGroup.isNullOrBlank()) series.overrideGroup else rssGroup
            if (targetGroup.isNullOrBlank()) continue

            val result = rssRepository.fetchFeed(targetGroup, series.fileNameMatch, quality)
            
            if (result.isSuccess) {
                val items = result.getOrNull() ?: emptyList()
                
                // Find items published AFTER lastCheckTime
                val newItems = items.filter { item ->
                    try {
                        val date = dateFormat.parse(item.pubDate)
                        date != null && date.time > lastCheckTime
                    } catch (e: Exception) {
                        false
                    }
                }

                if (newItems.isNotEmpty()) {
                    newEpisodesCount += newItems.size
                    newItems.forEach { 
                         newEpisodesDetails.add("${series.folderName}: ${it.title}")
                    }
                }
            }
        }

        // 3. Notify
        if (newEpisodesCount > 0) {
            sendNotification(newEpisodesCount, newEpisodesDetails)
        }

        // 4. Update Last Check Time
        configManager.updateConfig(ConfigKeys.RSS_LAST_CHECK_TIME, System.currentTimeMillis())

        Result.success()
    }

    private fun sendNotification(count: Int, details: List<String>) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "new_episodes_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "New Episodes", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "downloader") // Optional: handle deep link
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val style = NotificationCompat.InboxStyle()
        details.take(5).forEach { style.addLine(it) }
        if (details.size > 5) style.setSummaryText("+${details.size - 5} more")

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher) // Ensure this exists or use system default
            .setContentTitle("$count New Episodes Found")
            .setContentText(details.firstOrNull() ?: "Check Downloader for new releases.")
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
