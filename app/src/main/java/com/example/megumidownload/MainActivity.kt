package com.example.megumidownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.megumidownload.ui.screens.DashboardScreen
import com.example.megumidownload.ui.screens.SeriesManagerScreen
import com.example.megumidownload.ui.screens.SettingsScreen
import com.example.megumidownload.ui.theme.MegumiDownloadTheme
import com.example.megumidownload.viewmodel.LogViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    
    private val logViewModel: LogViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        permissionManager = PermissionManager(this)
        
        // Install binaries on first launch
        Thread {
            BinaryInstaller.installBinaries(this)
        }.start()

        setContent {
            MegumiDownloadTheme {
                MainScreen(logViewModel, permissionManager)
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home)
    object Series : Screen("series", "Series", Icons.Filled.List)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object Downloader : Screen("downloader", "Downloader", Icons.Filled.ArrowDropDown) // Using generic icon
}

@Composable
fun MainScreen(logViewModel: LogViewModel, permissionManager: PermissionManager) {
    val context = LocalContext.current // Need context for ConfigManager
    val configManager = remember { ConfigManager(context) }
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
                                popUpTo(navController.graph.findStartDestination().id) {
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
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Dashboard.route, Modifier.padding(innerPadding)) {
            composable(Screen.Dashboard.route) { DashboardScreen(logViewModel, permissionManager) }
            composable(Screen.Series.route) { SeriesManagerScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Downloader.route) { com.example.megumidownload.ui.screens.DownloaderScreen() }
        }
    }
}
