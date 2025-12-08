package com.example.megumidownload

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
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

    // Load Window State
    val savedGeometry = configManager.getWindowGeometry()
    val windowState = rememberWindowState(
        placement = if (savedGeometry.isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = WindowPosition(savedGeometry.x.dp, savedGeometry.y.dp),
        size = DpSize(savedGeometry.width.dp, savedGeometry.height.dp)
    )

    Window(
        onCloseRequest = {
            // Save Window State
            val geometry = DesktopConfigManager.WindowGeometry(
                width = windowState.size.width.value.toInt(),
                height = windowState.size.height.value.toInt(),
                x = windowState.position.x.value.toInt(),
                y = windowState.position.y.value.toInt(),
                isMaximized = windowState.placement == WindowPlacement.Maximized
            )
            configManager.saveWindowGeometry(geometry)
            exitApplication()
        }, 
        state = windowState,
        title = "Megumi Downloader"
    ) {
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
