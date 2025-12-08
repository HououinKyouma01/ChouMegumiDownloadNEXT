package com.example.megumidownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.megumidownload.ui.theme.MegumiDownloadTheme
import com.example.megumidownload.viewmodel.LogViewModel

class MainActivity : ComponentActivity() {
    private lateinit var permissionHandler: AndroidPermissionHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val configManager = AndroidConfigManager(this)
        val seriesManager = SeriesManager(filesDir)
        val notificationService = AndroidNotificationService(this)
        val syncManager = SyncManager(configManager, seriesManager, notificationService, filesDir, cacheDir)
        val rssRepository = RssRepository()
        val videoProcessor = AndroidVideoProcessor(this)
        val downloadManager = DownloadManager(configManager, seriesManager, videoProcessor, cacheDir, notificationService)
        val systemDownloadManager = AndroidSystemDownloadManager(this)
        val logViewModel = LogViewModel()
        
        // Initialize the property
        permissionHandler = AndroidPermissionHandler(this)
        
        val backgroundScheduler = AndroidBackgroundScheduler(this)
        val linkExtractor = AndroidLinkExtractor()

        setContent {
            MegumiDownloadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        configManager = configManager,
                        seriesManager = seriesManager,
                        syncManager = syncManager,
                        rssRepository = rssRepository,
                        downloadManager = downloadManager,
                        systemDownloadManager = systemDownloadManager,
                        logViewModel = logViewModel,
                        permissionHandler = permissionHandler,
                        backgroundScheduler = backgroundScheduler,
                        linkExtractor = linkExtractor,
                        notificationService = notificationService,
                        videoProcessor = videoProcessor
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::permissionHandler.isInitialized) {
            permissionHandler.checkPermissions()
        }
    }
}
