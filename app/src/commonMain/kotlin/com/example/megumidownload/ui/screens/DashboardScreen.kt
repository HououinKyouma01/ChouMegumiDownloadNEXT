package com.example.megumidownload.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.megumidownload.ConfigManager
import com.example.megumidownload.PermissionHandler
import com.example.megumidownload.BackgroundScheduler
import com.example.megumidownload.viewmodel.LogType
import com.example.megumidownload.viewmodel.LogViewModel
import com.example.megumidownload.ProgressRepository
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    viewModel: LogViewModel, 
    permissionHandler: PermissionHandler, 
    configManager: ConfigManager,
    backgroundScheduler: BackgroundScheduler? = null // Optional for now to avoid compile error if I missed passing it, but strictly it should be passed
) {
    val logs by viewModel.logs.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    // Progress State
    val currentFile by ProgressRepository.currentFile.collectAsState()
    val currentStep by ProgressRepository.currentStep.collectAsState()
    val progress by ProgressRepository.progress.collectAsState()
    val totalFiles by ProgressRepository.totalFiles.collectAsState()
    val processedFiles by ProgressRepository.processedFiles.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { 
                    if (!permissionHandler.hasStoragePermission()) {
                        permissionHandler.requestStoragePermission()
                    } else {
                        viewModel.addLog("Storage permission already granted", LogType.SUCCESS)
                    }
                }) {
                    Text("Check Permissions")
                }
                
                Button(onClick = {
                    backgroundScheduler?.scheduleDownload(force = true)
                    viewModel.addLog("Download started in background...", LogType.INFO)
                }) {
                    Text("Start Download")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Logs:", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.clearLogs() }) {
                    Text("Clear")
                }
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.05f))
                    .padding(8.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = "[${log.timestamp}] ${log.message}",
                        color = when (log.type) {
                            LogType.ERROR -> MaterialTheme.colorScheme.error
                            LogType.SUCCESS -> Color(0xFF4CAF50) // Green
                            LogType.WARNING -> Color(0xFFFF9800) // Orange
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // Add spacer to avoid overlap with progress bar if it's visible
            if (currentFile.isNotEmpty()) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // Progress Bar Section
        if (currentFile.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Processing: $currentFile (${processedFiles + 1} of $totalFiles)",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentStep.contains("Re-encoding")) currentStep.replace("Re-encoding", "Fixing timing") else currentStep.toString(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        // Auto-start Logic
        val autoStart by configManager.autoStart.collectAsState(initial = false)
        LaunchedEffect(autoStart) {
            if (autoStart && logs.isEmpty()) { 
                viewModel.addLog("Auto-start triggering detected (Config=True)...", LogType.INFO)
                backgroundScheduler?.scheduleDownload(force = false)
            }
        }
    }
}
