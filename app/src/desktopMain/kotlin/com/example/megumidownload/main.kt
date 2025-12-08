package com.example.megumidownload

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material3.MaterialTheme
import java.io.File
import com.example.megumidownload.viewmodel.LogViewModel

fun main() = application {
    val appDir = File(System.getProperty("user.home"), ".megumidownload")
    if (!appDir.exists()) appDir.mkdirs()
    val cacheDir = File(appDir, "cache")
    if (!cacheDir.exists()) cacheDir.mkdirs()

    Logger.i("Main", "Starting Megumi Downloader v1.2")
    Logger.i("Main", "App Dir: ${appDir.absolutePath}")


    val configManager = DesktopConfigManager()
    val seriesManager = SeriesManager(appDir)
    val notificationService = DesktopNotificationService()
    val syncManager = SyncManager(configManager, seriesManager, notificationService, appDir, cacheDir)
    val rssRepository = RssRepository()
    val videoProcessor = DesktopVideoProcessor()
    val downloadManager = DownloadManager(configManager, seriesManager, videoProcessor, cacheDir, notificationService)
    val systemDownloadManager = DesktopSystemDownloadManager()
    val logViewModel = LogViewModel()
    val permissionHandler = DesktopPermissionHandler()
    val backgroundScheduler = DesktopBackgroundScheduler(downloadManager, configManager, videoProcessor)
    val linkExtractor = DesktopLinkExtractor()

    Window(onCloseRequest = ::exitApplication, title = "Megumi Downloader") {
        MaterialTheme {
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
