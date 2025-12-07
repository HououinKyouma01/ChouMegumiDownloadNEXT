package com.example.megumidownload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val configManager = AndroidConfigManager(applicationContext)
        val seriesManager = SeriesManager(applicationContext.filesDir)
        val notificationService = AndroidNotificationService(applicationContext)
        val syncManager = SyncManager(configManager, seriesManager, notificationService, applicationContext.filesDir, applicationContext.cacheDir)
        
        // Default options for background sync:
        // Remote -> Local (Download)
        // Sync Episodes, Filelist, Replace
        // No specific selection (Sync All)
        val options = SyncManager.SyncOptions(
            isLocalToRemote = false,
            syncFilelist = true,
            syncReplace = true,
            syncEpisodes = true,
            selectedFiles = null
        )
        
        val seriesList = seriesManager.getSeriesList()
        if (seriesList.isEmpty()) return@withContext Result.success()

        val syncResult = syncManager.syncAll(
            seriesList = seriesList,
            options = options,
            onProgress = { /* Progress handled by NotificationHelper inside SyncManager */ },
            onConflict = { _, _ -> 
                // Auto-resolution strategy: Skip conflicts to avoid data loss in background
                false 
            }
        )

        if (syncResult is SyncManager.SyncResult.Success) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context, intervalHours: Long, wifiOnly: Boolean) {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val workName = "auto_sync_work"
            
            if (intervalHours <= 0) {
                workManager.cancelUniqueWork(workName)
                return
            }

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) androidx.work.NetworkType.UNMETERED 
                    else androidx.work.NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()

            val request = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours, java.util.concurrent.TimeUnit.HOURS
            )
            .setConstraints(constraints)
            .build()

            workManager.enqueueUniquePeriodicWork(
                workName,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
