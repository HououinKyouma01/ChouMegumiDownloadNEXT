package com.example.megumidownload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.megumidownload.viewmodel.LogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Manual DI since we are not using Hilt
        val configManager = AndroidConfigManager(applicationContext)
        val seriesManager = SeriesManager(applicationContext.filesDir)
        // Note: LogViewModel is usually UI scoped, but for Worker we might need a different way to log 
        // or just log to system log / file.
        // For this example, we'll create a dummy or try to get the singleton if we had one.
        // Since we don't have a singleton LogViewModel, we'll skip UI logging here 
        // or implement a file logger.
        
        val videoProcessor = AndroidVideoProcessor(applicationContext)
        val downloadManager = DownloadManager(
            configManager, 
            seriesManager,
            videoProcessor,
            applicationContext.cacheDir
        )
        
        try {
            downloadManager.startDownloadCycle()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
