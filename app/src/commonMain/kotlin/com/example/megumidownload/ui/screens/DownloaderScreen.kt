package com.example.megumidownload.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.megumidownload.Logger
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.megumidownload.ConfigManager
import com.example.megumidownload.ConfigKeys
import com.example.megumidownload.RssItem
import com.example.megumidownload.RssRepository
import com.example.megumidownload.SeriesEntry
import com.example.megumidownload.SeriesManager
import com.example.megumidownload.SystemDownloadManager
import com.example.megumidownload.LinkExtractor

import com.example.megumidownload.DownloadConfig
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DownloaderScreen(
    configManager: ConfigManager,
    seriesManager: SeriesManager,
    rssRepository: RssRepository,
    systemDownloadManager: SystemDownloadManager,
    linkExtractor: LinkExtractor,
    downloadManager: com.example.megumidownload.DownloadManager,
    backgroundScheduler: com.example.megumidownload.BackgroundScheduler
) {
    val scope = rememberCoroutineScope()

     // Parse groups from CSV
    val rssGroupsString by configManager.rssGroup.collectAsState(initial = "")
    val groups = remember(rssGroupsString) {
        rssGroupsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    val rssQuality by configManager.rssQuality.collectAsState(initial = "1080p")
    val autoDownloadGofile by configManager.autoDownloadGofile.collectAsState(initial = false)
    val localSourcePath by configManager.localSourcePath.collectAsState(initial = "")
    
    var showLinksDialog by remember { mutableStateOf(false) }
    var currentLinks by remember { mutableStateOf<List<RssItem>>(emptyList()) }
    var dialogTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    
    // Auto-Extraction State
    var extractionUrl by remember { mutableStateOf<String?>(null) }
    var extractionItemTitle by remember { mutableStateOf("") }
    var extractionSeries by remember { mutableStateOf<SeriesEntry?>(null) }
    
    // Queue State
    var pendingRemove by remember { mutableStateOf<Triple<RssItem, SeriesEntry, List<String>>?>(null) }
    val fetchQueue = remember { mutableStateListOf<Triple<RssItem, SeriesEntry, List<String>>>() }
    var isFetching by remember { mutableStateOf(false) }

    // Error Feedback
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }

    // Auto-Scan State
    var showScanDialog by remember { mutableStateOf(false) }
    var foundFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }

    // State to refresh local file checks and series list
    var refreshTrigger by remember { mutableStateOf(0) } 
    
    val seriesList = produceState<List<SeriesEntry>>(initialValue = emptyList(), key1 = refreshTrigger) {
        value = seriesManager.getSeriesList()
    }.value
    
    val downloadConfigState = produceState(initialValue = null as DownloadConfig?) {
        value = configManager.getDownloadConfig()
    }
    val downloadConfig = downloadConfigState.value

    // Queue Processor
    LaunchedEffect(fetchQueue.size, isFetching) {
        if (fetchQueue.isNotEmpty() && !isFetching) {
            val nextItem = fetchQueue[0]
            
            extractionItemTitle = nextItem.first.title
            extractionSeries = nextItem.second
            
            val goFileLink = nextItem.first.hostLinks.entries.find { it.key.contains("GoFile", true) }?.value
            
            if (goFileLink != null) {
                loadingMessage = "Fetching Link: ${nextItem.first.title}..."
                isLoading = true 
                isFetching = true
                extractionUrl = goFileLink
            } else {
                 Logger.w("Downloader", "No GoFile link for ${nextItem.first.title}")
                 fetchQueue.removeAt(0)
            }
        }
    }

    // Auto-Extraction Component
    if (extractionUrl != null) {
        linkExtractor.Extract(
            url = extractionUrl!!,
            onResult = { result ->
                if (result != null && extractionSeries != null) {
                    try {
                        Logger.i("Downloader", "Extracted URL for '${extractionItemTitle}': ${result.url}")
                        
                        // Check if URL looks suspicious (e.g. just the host, or html)
                        if (result.url.endsWith(".html") || !result.url.contains("/")) {
                            Logger.w("Downloader", "WARNING: URL looks like an HTML page, not a file! ${result.url}")
                        }
                        
                        val cleanTitle = if (extractionItemTitle.endsWith(".mkv", true)) extractionItemTitle else "${extractionItemTitle}.mkv"
                        
                        // Use BackgroundScheduler to handle Download + Processing (Restores Legacy Worker Behavior on Android)
                        val config = downloadConfig ?: com.example.megumidownload.DownloadConfig(
                            host = "",
                            user = "",
                            pass = "",
                            remotePath = "",
                            localBasePath = "",
                            localOnly = false,
                            localSourcePath = "",
                            prefixSeriesName = false,
                            appendQuality = false
                        ) // Safe fallback
                        
                        backgroundScheduler.scheduleUrlDownload(
                            url = result.url,
                            title = cleanTitle,
                            series = extractionSeries!!,
                            config = config,
                            cookie = result.cookie,
                            userAgent = result.userAgent
                        )
                        
                        Logger.i("Downloader", "Download Scheduled: $extractionItemTitle")
                        
                        // NOTE: Polling logic removed. Worker handles lifecycle.
                    } catch (e: Exception) {
                        Logger.e("Downloader", "Failed to start download: ${e.message}")
                    }
                } else {
                     Logger.w("Downloader", "Extraction failed for $extractionItemTitle")
                }
                
                // Cleanup & Next
                isFetching = false
                isLoading = false
                extractionUrl = null 
                extractionSeries = null
                if (fetchQueue.isNotEmpty()) {
                    fetchQueue.removeAt(0)
                }
            }
        )
    }

    // Auto-Scan Logic
    LaunchedEffect(seriesList, localSourcePath, scanned) {
         if (!scanned && seriesList.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // If localSourcePath is empty, use a default fallback or nothing
                val sourceDir = if (localSourcePath.isNotBlank()) File(localSourcePath) else null /* No default download dir here without Env */
                
                if (sourceDir != null && sourceDir.exists() && sourceDir.isDirectory) {
                    val matching = sourceDir.listFiles()?.filter { file ->
                        file.isFile && 
                        file.name.endsWith(".mkv") && 
                        !file.name.startsWith("processed_") &&
                        !file.name.endsWith(".part") &&
                        seriesList.any { series ->
                             file.name.contains(series.fileNameMatch, ignoreCase = true)
                        }
                    } ?: emptyList()
                    
                    if (matching.isNotEmpty()) {
                         foundFiles = matching
                         showScanDialog = true
                    }
                }
            }
            scanned = true 
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        
        // Fetch Queue Status
        if (fetchQueue.isNotEmpty()) {
             Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Queue: ${fetchQueue.size} items waiting...", style = MaterialTheme.typography.labelMedium)
                    fetchQueue.forEachIndexed { index, item ->
                        if (index < 3) {
                             Text("${index + 1}. ${item.first.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    if (fetchQueue.size > 3) {
                        Text("+ ${fetchQueue.size - 3} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.lazy.LazyColumn {
            items(seriesList) { series ->
                
                val hasFiles = remember(refreshTrigger, downloadConfig) {
                     hasLocalFiles(series, downloadConfig)
                }

                SeriesDownloadItem(
                    series = series,
                    hasLocalFiles = hasFiles,
                    availableGroups = groups,
                    onUpdateSeries = { updatedSeries ->
                        seriesManager.updateSeries(series, updatedSeries)
                        refreshTrigger++ 
                    },
                    onDownloadNewest = {
                        scope.launch {
                            isLoading = true
                            currentLinks = emptyList()
                            loadingMessage = "Initializing..."
                            
                            val targetGroups = if (!series.overrideGroup.isNullOrBlank()) {
                                listOf(series.overrideGroup!!)
                            } else if (groups.isNotEmpty()) {
                                groups
                            } else {
                                listOf("")
                            }
                            Logger.d("Downloader", "DEBUG: DownloadNewest - Series: ${series.fileNameMatch}, Groups: $targetGroups, ConfigGroups: $groups")
                            
                            dialogTitle = "Newest: ${series.fileNameMatch}"
                            
                            for (group in targetGroups) {
                                // Allow blank group
                                loadingMessage = "Checking ${if(group.isBlank()) "Global (No Group)" else group}..."
                                Logger.d("Downloader", "DEBUG: Checking group: $group")
                                val result = rssRepository.fetchFeed(group, series.fileNameMatch, rssQuality)
                                if (result.isSuccess) {
                                    val items = result.getOrNull()
                                    Logger.d("Downloader", "DEBUG: Group $group result size: ${items?.size}")
                                    if (!items.isNullOrEmpty()) {
                                        val firstItem = items.first()
                                        currentLinks = listOf(firstItem)
                                        
                                        if (autoDownloadGofile) {
                                            val goFileLink = firstItem.hostLinks.entries.find { it.key.contains("GoFile", true) }?.value
                                            
                                            if (goFileLink != null) {
                                                fetchQueue.add(Triple(firstItem, series, groups))
                                                Logger.i("Downloader", "Added to Queue: ${firstItem.title}")
                                                isLoading = false
                                            } else {
                                                showLinksDialog = true
                                                isLoading = false
                                            }
                                        } else {
                                            showLinksDialog = true
                                            isLoading = false
                                        }
                                        break
                                    }
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Unknown Error"
                                    Logger.e("Downloader", "DEBUG: Fetch failed for $group: $err")
                                    // Only show error if ALL groups fail or if user explicitly triggered it?
                                    // For debugging, screw it, show the error.
                                    errorMessage = "Group '$group' Error: $err"
                                }
                            }
                            
                            if (currentLinks.isEmpty() && errorMessage == null) {
                                Logger.w("Downloader", "No releases found.")
                                errorMessage = "No releases found for '${series.fileNameMatch}'. Checked: $targetGroups"
                                isLoading = false
                            } else if (currentLinks.isEmpty()) {
                                isLoading = false
                            }
                        }
                    },
                    onDownloadAll = {
                         scope.launch {
                            isLoading = true
                            currentLinks = emptyList()
                            
                            val targetGroups = if (!series.overrideGroup.isNullOrBlank()) {
                                listOf(series.overrideGroup!!)
                            } else if (groups.isNotEmpty()) {
                                groups
                            } else {
                                // Fallback: Search globally if no groups configured
                                listOf("")
                            }
                            Logger.d("Downloader", "DEBUG: DownloadAll - Series: ${series.fileNameMatch}, Groups: $targetGroups")
                            
                            dialogTitle = "All: ${series.fileNameMatch}"
                            
                             for (group in targetGroups) {
                                // Allow blank group for global search
                                loadingMessage = "Checking ${if(group.isBlank()) "Global (No Group)" else group}..."
                                Logger.d("Downloader", "DEBUG: Checking group: $group")
                                val result = rssRepository.fetchFeed(group, series.fileNameMatch, rssQuality)
                                if (result.isSuccess) {
                                    val items = result.getOrNull()
                                    Logger.d("Downloader", "DEBUG: Group $group result size: ${items?.size}")
                                    if (!items.isNullOrEmpty()) {
                                        currentLinks = items
                                        showLinksDialog = true
                                        break
                                    }
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Unknown Error"
                                    Logger.e("Downloader", "DEBUG: Fetch failed for $group: $err")
                                    errorMessage = "Group '$group' Error: $err"
                                }
                            }
                             
                            if (currentLinks.isEmpty()) {
                                Logger.w("Downloader", "No releases found.")
                            }
                            isLoading = false
                        }
                    }
                )
                Divider()
            }
        }
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(loadingMessage)
                }
            }
        }
        }
        

        
        // No extra brace here!

    if (showScanDialog && downloadConfig != null) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Found Local Files") },
            text = {
                Column {
                    Text("Found ${foundFiles.size} potentially matching files.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        // Launch processing
                        showScanDialog = false
                        loadingMessage = "Processing ${foundFiles.size} files..."
                        isLoading = true
                        
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                foundFiles.forEach { file ->
                                    val series = seriesList.find { file.name.contains(it.fileNameMatch, true) }
                                    if (series != null) {
                                        Logger.i("Downloader", "Processing local file: ${file.name}")
                                        val success = downloadManager.processTempFile(
                                            localTempFile = file,
                                            originalFileName = file.name,
                                            localDestBasePath = downloadConfig.localBasePath,
                                            series = series
                                        )
                                        
                                        if (success) {
                                            Logger.i("Downloader", "Successfully processed: ${file.name}")
                                        } else {
                                            Logger.e("Downloader", "Failed to process: ${file.name}")
                                        }
                                    }
                                }
                            }
                            isLoading = false
                        }
                    }) {
                        Text("Process All")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScanDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLinksDialog) {
        DownloadLinksDialog(
            title = dialogTitle,
            items = currentLinks,
            downloadConfig = downloadConfig,
            seriesNameFolder = seriesList.find { dialogTitle.contains(it.fileNameMatch) }?.folderName,
            onDismiss = { showLinksDialog = false },
            onLinkClick = { url, item -> 
                // Using ConfigManager to open URL logic? 
                // Or we can rely on systemDownloadManager if user wants to download.
                // But this dialog shows Host Links.
                // If Host is GoFile, we might want to extract.
                // For now, let's just create a Download Request.
                if (url.contains("gofile", true)) {
                     // Add to queue for extraction and worker handling
                     val series = seriesList.find { dialogTitle.contains(it.fileNameMatch) }
                     if (series != null) {
                        fetchQueue.add(Triple(item, series, groups))
                        Logger.i("Downloader", "Manually added to Queue: ${item.title}")
                        showLinksDialog = false // Close dialog to show queue progress
                     } else {
                        Logger.e("Downloader", "Could not find series for manual download")
                     }
                } else {
                    // Open in Browser
                    systemDownloadManager.openLink(url)
                }
            }
        )
    }
}

private fun hasLocalFiles(series: SeriesEntry, config: DownloadConfig?): Boolean {
    if (config == null) return false
    val destDir = File(config.localBasePath, "${series.folderName}/Season ${series.seasonNumber}")
    return destDir.exists() && (destDir.listFiles()?.any { it.isFile && it.name.endsWith(".mkv") } == true)
}

private fun isEpisodeDownloaded(rssTitle: String, seriesFolderName: String?, config: DownloadConfig?): Boolean {
    if (config == null || seriesFolderName == null) return false
    val epMatch = Regex("(?i)(?:s\\d{1,2}e|-|\\s|ep|episode)\\s*(\\d{1,3})(?:v\\d)?(?:end)?(?:[\\s\\[\\(._-]|\$)").find(rssTitle)
    val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: return false
    val seriesDir = File(config.localBasePath, seriesFolderName)
    if (!seriesDir.exists()) return false
    return seriesDir.walkTopDown().maxDepth(2).filter { it.isFile && it.name.endsWith(".mkv") }.any { file ->
         val fEpMatch = Regex("(?i)(?:s\\d{1,2}e|-|\\s|ep|episode)\\s*(\\d{1,3})(?:v\\d)?(?:end)?(?:[\\s\\[\\(._-]|\$)").find(file.name)
         val fEpNum = fEpMatch?.groupValues?.get(1)?.toIntOrNull()
         fEpNum == epNum
    }
}

@Composable
fun SeriesDownloadItem(
    series: SeriesEntry,
    hasLocalFiles: Boolean,
    availableGroups: List<String>,
    onUpdateSeries: (SeriesEntry) -> Unit,
    onDownloadNewest: () -> Unit,
    onDownloadAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
             Column(modifier = Modifier.weight(1f)) {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(series.folderName, style = MaterialTheme.typography.titleMedium)
                    if (hasLocalFiles) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                 }
                 Text("Match: ${series.fileNameMatch}", style = MaterialTheme.typography.bodySmall)
             }
             
             IconButton(onClick = { onUpdateSeries(series.copy(notify = !series.notify)) }) {
                 Icon(
                     imageVector = Icons.Default.Notifications,
                     contentDescription = "Toggle Notification",
                     tint = if (series.notify) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                 )
             }

             Box {
                 OutlinedButton(onClick = { expanded = true }) {
                     Text(if (series.overrideGroup.isNullOrBlank()) "Auto" else series.overrideGroup)
                     Spacer(modifier = Modifier.width(4.dp))
                     Icon(Icons.Default.ArrowDropDown, "Select Group", modifier = Modifier.size(16.dp))
                 }
                 DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                     DropdownMenuItem(
                         text = { Text("Auto (Sequential)") },
                         onClick = {
                             onUpdateSeries(series.copy(overrideGroup = null))
                             expanded = false
                         }
                     )
                     availableGroups.forEach { group ->
                          DropdownMenuItem(
                             text = { Text(group) },
                             onClick = {
                                 onUpdateSeries(series.copy(overrideGroup = group))
                                 expanded = false
                             }
                         )
                     }
                 }
             }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDownloadNewest,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text(
                    "Download\nNewest",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            OutlinedButton(
                onClick = onDownloadAll,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text(
                    "Download\nAll",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun DownloadLinksDialog(
    title: String,
    items: List<RssItem>,
    downloadConfig: DownloadConfig?,
    seriesNameFolder: String?,
    onDismiss: () -> Unit,
    onLinkClick: (String, RssItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text("No results found.")
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(items) { item ->
                        val isDownloaded by produceState(initialValue = false, item.title) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                isEpisodeDownloaded(item.title, seriesNameFolder, downloadConfig)
                            }
                        }
                        
                        Column(modifier = Modifier.padding(bottom = 16.dp).background(if(isDownloaded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f) else MaterialTheme.colorScheme.surface)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.title, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f),
                                    textDecoration = if (isDownloaded) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                )
                                if (isDownloaded) {
                                    Icon(Icons.Default.Check, "Downloaded", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                                if (item.hostLinks.isNotEmpty()) {
                                    item.hostLinks.entries.sortedByDescending { it.key.contains("GoFile", true) }.forEach { (host, url) ->
                                        AssistChip(
                                            onClick = { onLinkClick(url, item) },
                                            label = { Text(host) },
                                            modifier = Modifier.padding(end = 8.dp),
                                            colors = if (host.contains("GoFile", true)) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors()
                                        )
                                    }
                                } else {
                                    AssistChip(
                                        onClick = { onLinkClick(item.link, item) },
                                        label = { Text("View Page") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
