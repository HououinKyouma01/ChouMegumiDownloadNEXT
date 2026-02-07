package com.example.megumidownload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.megumidownload.viewmodel.LogViewModel
import com.example.megumidownload.ui.screens.DashboardScreen
import com.example.megumidownload.ui.screens.SeriesManagerScreen
import com.example.megumidownload.ui.screens.SettingsScreen
import com.example.megumidownload.ui.screens.DownloaderScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home)
    object Series : Screen("series", "Series", Icons.Filled.List)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object Downloader : Screen("downloader", "Downloader", Icons.Filled.ArrowDropDown)
}

@Composable
fun App(
    configManager: ConfigManager,
    seriesManager: SeriesManager,
    syncManager: SyncManager,
    rssRepository: RssRepository,
    downloadManager: DownloadManager,
    systemDownloadManager: SystemDownloadManager,
    logViewModel: LogViewModel,
    permissionHandler: PermissionHandler,
    backgroundScheduler: BackgroundScheduler,
    linkExtractor: LinkExtractor,
    notificationService: NotificationService,
    videoProcessor: VideoProcessor
) {
    val rssEnabled by configManager.rssEnabled.collectAsState(initial = false)

    val navController = rememberNavController()
    val items = remember(rssEnabled) {
        mutableListOf<Screen>().apply {
            add(Screen.Dashboard)
            add(Screen.Series)
            if (rssEnabled) add(Screen.Downloader)
            add(Screen.Settings)
        }
    }
    Scaffold(
        bottomBar = {
            Column {
                // Global Progress Bar Overlay
                val activeDownloads by ProgressRepository.activeDownloads.collectAsState()
                val activeSpeed by ProgressRepository.activeSpeed.collectAsState()
                
                fun formatSpeed(bytesPerSec: Long): String {
                    if (bytesPerSec < 1024) return "$bytesPerSec B/s"
                    val kb = bytesPerSec / 1024.0
                    if (kb < 1024) return "${kb.toString().take(4)} KB/s"
                    val mb = kb / 1024.0
                    return "${mb.toString().take(4)} MB/s"
                }
                
                if (activeDownloads.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // Total Speed Header
                        val totalSpeed = activeSpeed.values.sum()
                        if (totalSpeed > 0) {
                            Text(
                                text = "Total Speed: ${formatSpeed(totalSpeed)}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 4.dp).align(Alignment.End)
                            )
                        }

                        activeDownloads.forEach { (fileName, progress) ->
                            androidx.compose.runtime.key(fileName) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val speed = activeSpeed[fileName] ?: 0L
                                    if (speed > 0) {
                                        Text(
                                            text = formatSpeed(speed),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.width(100.dp).height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().route!!) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Dashboard.route, Modifier.padding(innerPadding)) {
            composable(Screen.Dashboard.route) { 
                 DashboardScreen(logViewModel, permissionHandler, configManager, backgroundScheduler)
            }
            composable(Screen.Series.route) { 
                 val scope = rememberCoroutineScope()
                 SeriesManagerScreen(
                     seriesManager, 
                     syncManager, 
                     configManager, 
                     backgroundScheduler,
                     onReprocessEpisode = { series, file ->
                         scope.launch {
                             downloadManager.reprocessEpisode(series, file)
                         }
                     }
                 )
            }
            composable(Screen.Settings.route) { 
                 SettingsScreen(configManager, seriesManager, permissionHandler) 
            }
            composable(Screen.Downloader.route) {
                 DownloaderScreen(configManager, seriesManager, rssRepository, systemDownloadManager, linkExtractor, downloadManager, backgroundScheduler)
            }
        }
    }
}
