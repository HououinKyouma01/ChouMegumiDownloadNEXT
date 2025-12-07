package com.example.megumidownload.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.megumidownload.ConfigManager
import com.example.megumidownload.RssItem
import com.example.megumidownload.RssRepository
import com.example.megumidownload.SeriesEntry
import com.example.megumidownload.SeriesManager
// Removed GoFileExtractor import
import android.app.DownloadManager
import android.os.Environment
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DownloaderScreen() {
    val context = LocalContext.current
    val configManager = remember { ConfigManager(context) }
    val seriesManager = remember { SeriesManager(context) }
    val rssRepository = remember { RssRepository() }
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
    
    // Workers State
    val workManager = androidx.work.WorkManager.getInstance(context)
    // We can observe work by tag if we tag them "rss_download"
    val workInfos by workManager.getWorkInfosByTagLiveData("rss_download").observeAsState(emptyList())
    // Filter for active work
    val activeWorker = workInfos.find { it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED }
    
    // Auto-Scan State
    var showScanDialog by remember { mutableStateOf(false) }
    var foundFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }

    // State to refresh local file checks and series list
    var refreshTrigger by remember { mutableStateOf(0) } // Incremented to refresh
    
    val seriesList = produceState<List<SeriesEntry>>(initialValue = emptyList(), key1 = refreshTrigger) {
        value = seriesManager.getSeriesList()
    }.value
    
    // Config for local file checking
    val downloadConfig = produceState(initialValue = null as com.example.megumidownload.DownloadConfig?) {
        value = configManager.getDownloadConfig()
    }
    
    // Auto-Scan Logic
    LaunchedEffect(seriesList, localSourcePath, scanned) {
        if (!scanned && seriesList.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Default to Downloads if localSourcePath is empty
                val sourceDir = if (localSourcePath.isNotBlank()) File(localSourcePath) else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                
                if (sourceDir.exists() && sourceDir.isDirectory) {
                    val matching = sourceDir.listFiles()?.filter { file ->
                        file.isFile && file.name.endsWith(".mkv") && seriesList.any { series ->
                             file.name.contains(series.fileNameMatch, ignoreCase = true)
                        }
                    } ?: emptyList()
                    
                    if (matching.isNotEmpty()) {
                         foundFiles = matching
                         showScanDialog = true
                    }
                }
            }
            scanned = true // Run once per session/screen load
        }
    }

    // Notifications & Scheduling
    val checkInterval by configManager.rssCheckIntervalHours.collectAsState(initial = 1)
    
    // Permission Launcher
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
             // Permission granted
        } else {
             // Handle denial
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Schedule Background Work
    LaunchedEffect(checkInterval) {
        val workRequest = androidx.work.PeriodicWorkRequest.Builder(
            com.example.megumidownload.RssCheckWorker::class.java,
            checkInterval.toLong(), java.util.concurrent.TimeUnit.HOURS
        )
        .addTag("rss_check")
        .build()

        workManager.enqueueUniquePeriodicWork(
            "RssBackgroundCheck",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    // Hidden WebView for Extraction
    if (extractionUrl != null) {
        HiddenWebView(
            url = extractionUrl!!,
            onLinkFound = { directLink ->
                if (directLink != null && extractionSeries != null && downloadConfig.value != null) {
                    try {
                        // Start RssDownloadWorker
                        val gson = com.google.gson.Gson()
                        val inputData = androidx.work.workDataOf(
                            "url" to directLink,
                            "title" to extractionItemTitle,
                            "seriesJson" to gson.toJson(extractionSeries),
                            "configJson" to gson.toJson(downloadConfig.value)
                        )
                        
                        val request = androidx.work.OneTimeWorkRequest.Builder(com.example.megumidownload.RssDownloadWorker::class.java)
                            .setInputData(inputData)
                            .addTag("rss_download")
                            .build()
                        
                        workManager.enqueue(request)
                        android.widget.Toast.makeText(context, "Download & Processing Started", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to start worker: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Fallback
                     android.widget.Toast.makeText(context, "Auto-extraction failed. Showing links.", android.widget.Toast.LENGTH_SHORT).show()
                     showLinksDialog = true
                }
                // Cleanup
                isLoading = false
                extractionUrl = null 
                extractionSeries = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Active Worker Progress
        if (activeWorker != null) {
            val progress = activeWorker.progress.getFloat("progress", 0f)
            val status = activeWorker.progress.getString("status") ?: "Working..."
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Background Task", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    
        // ... (Rest of UI)
        // ... (Existing code for lazy column etc)

        // ... (Header and Manual Override UI - unchanged)
        
        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.lazy.LazyColumn {
            items(seriesList) { series ->
                
                val hasFiles = remember(refreshTrigger, downloadConfig.value) {
                     hasLocalFiles(series, downloadConfig.value)
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
                                listOf(series.overrideGroup)
                            } else {
                                groups
                            }
                            
                            dialogTitle = "Newest: ${series.fileNameMatch}"
                            
                            for (group in targetGroups) {
                                if (group.isBlank()) continue
                                loadingMessage = "Checking $group..."
                                val result = rssRepository.fetchFeed(group, series.fileNameMatch, rssQuality)
                                if (result.isSuccess) {
                                    val items = result.getOrNull()
                                    if (!items.isNullOrEmpty()) {
                                        val firstItem = items.first()
                                        currentLinks = listOf(firstItem)
                                        
                                        // Auto Download Logic
                                        if (autoDownloadGofile) {
                                            val goFileLink = firstItem.hostLinks.entries.find { it.key.contains("GoFile", true) }?.value
                                            
                                            if (goFileLink != null) {
                                                 loadingMessage = "Analyzing GoFile link..."
                                                 extractionItemTitle = firstItem.title
                                                 extractionUrl = goFileLink
                                                 extractionSeries = series
                                                 // WebView will handle the rest via callback
                                                 // Don't set showLinksDialog yet
                                            } else {
                                                // No GoFile link, just show dialog
                                                showLinksDialog = true
                                                isLoading = false
                                            }
                                        } else {
                                            showLinksDialog = true
                                            isLoading = false
                                        }
                                        break
                                    }
                                }
                            }
                            
                            if (currentLinks.isEmpty()) {
                                android.widget.Toast.makeText(context, "No releases found.", android.widget.Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                            
                            // If auto-download triggered, isLoading remains true until callback
                        }
                    },
                    onDownloadAll = {
                         scope.launch {
                            isLoading = true
                            currentLinks = emptyList()
                            
                            val targetGroups = if (!series.overrideGroup.isNullOrBlank()) {
                                listOf(series.overrideGroup)
                            } else {
                                groups
                            }
                            
                            dialogTitle = "All: ${series.fileNameMatch}"
                            
                             for (group in targetGroups) {
                                if (group.isBlank()) continue
                                loadingMessage = "Checking $group..."
                                val result = rssRepository.fetchFeed(group, series.fileNameMatch, rssQuality)
                                if (result.isSuccess) {
                                    val items = result.getOrNull()
                                    if (!items.isNullOrEmpty()) {
                                        currentLinks = items
                                        showLinksDialog = true
                                        break
                                    }
                                }
                            }
                             
                            if (currentLinks.isEmpty()) {
                                android.widget.Toast.makeText(context, "No releases found.", android.widget.Toast.LENGTH_SHORT).show()
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

    if (showScanDialog && downloadConfig.value != null) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Found Local Files") },
            text = {
                Column {
                    Text("Found ${foundFiles.size} potentially matching files in source folder. Process them?")
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max=200.dp)) {
                        items(foundFiles) { file ->
                             Text(file.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Enqueue processing for each file
                    foundFiles.forEach { file ->
                        // Find matching series
                         val match = seriesList.find { file.name.contains(it.fileNameMatch, ignoreCase = true) }
                         if (match != null) {
                            val gson = com.google.gson.Gson()
                            val inputData = androidx.work.workDataOf(
                                "filePath" to file.absolutePath,
                                "title" to file.nameWithoutExtension,
                                "seriesJson" to gson.toJson(match),
                                "configJson" to gson.toJson(downloadConfig.value)
                            )
                            val request = androidx.work.OneTimeWorkRequest.Builder(com.example.megumidownload.RssDownloadWorker::class.java)
                                .setInputData(inputData)
                                .addTag("rss_download")
                                .build()
                            workManager.enqueue(request)
                         }
                    }
                    android.widget.Toast.makeText(context, "Processing started for ${foundFiles.size} files", android.widget.Toast.LENGTH_SHORT).show()
                    showScanDialog = false
                }) {
                    Text("Process All")
                }
            },
            dismissButton = {
                 TextButton(onClick = { showScanDialog = false }) { Text("Ignore") }
            }
        )
    }

    if (showLinksDialog) {
        val dConfig = downloadConfig.value
        DownloadLinksDialog(
            title = dialogTitle,
            items = currentLinks,
            downloadConfig = dConfig,
            seriesNameFolder = seriesList.find { dialogTitle.contains(it.fileNameMatch) }?.folderName, // Heuristic to find series folder
            onDismiss = { showLinksDialog = false },
            onLinkClick = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        )
    }
}

private fun hasLocalFiles(series: SeriesEntry, config: com.example.megumidownload.DownloadConfig?): Boolean {
    if (config == null) return false
    val destDir = File(config.localBasePath, "${series.folderName}/Season ${series.seasonNumber}")
    return destDir.exists() && (destDir.listFiles()?.any { it.isFile && it.name.endsWith(".mkv") } == true)
}

// Function to check if specific episode exists
private fun isEpisodeDownloaded(rssTitle: String, seriesFolderName: String?, config: com.example.megumidownload.DownloadConfig?): Boolean {
    if (config == null || seriesFolderName == null) return false
    
    // Extract episode number from RSS title
    val epMatch = Regex("(?i)(?:s\\d{1,2}e|-|\\s|ep|episode)\\s*(\\d{1,3})(?:v\\d)?(?:end)?(?:[\\s\\[\\(._-]|\$)").find(rssTitle)
    val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: return false
    
    val seriesDir = File(config.localBasePath, seriesFolderName)
    if (!seriesDir.exists()) return false
    
    // Walk series dir to find matching episode number
    // Limit depth to 2 (Season folders)
    val found = seriesDir.walkTopDown().maxDepth(2).filter { it.isFile && it.name.endsWith(".mkv") }.any { file ->
         val fEpMatch = Regex("(?i)(?:s\\d{1,2}e|-|\\s|ep|episode)\\s*(\\d{1,3})(?:v\\d)?(?:end)?(?:[\\s\\[\\(._-]|\$)").find(file.name)
         val fEpNum = fEpMatch?.groupValues?.get(1)?.toIntOrNull()
         fEpNum == epNum
    }
    return found
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
             
             // Notify Toggle
             IconButton(onClick = { onUpdateSeries(series.copy(notify = !series.notify)) }) {
                 Icon(
                     imageVector = Icons.Default.Notifications,
                     contentDescription = "Toggle Notification",
                     tint = if (series.notify) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                 )
             }

             // Group Selector
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
}

@Composable
fun DownloadLinksDialog(
    title: String,
    items: List<RssItem>,
    downloadConfig: com.example.megumidownload.DownloadConfig?,
    seriesNameFolder: String?,
    onDismiss: () -> Unit,
    onLinkClick: (String) -> Unit
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
                        val isDownloaded = remember(item.title) { isEpisodeDownloaded(item.title, seriesNameFolder, downloadConfig) }
                        
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
                            
                            // Host Links
                            Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                                if (item.hostLinks.isNotEmpty()) {
                                    item.hostLinks.entries.sortedByDescending { it.key.contains("GoFile", true) }.forEach { (host, url) ->
                                        AssistChip(
                                            onClick = { onLinkClick(url) },
                                            label = { Text(host) },
                                            modifier = Modifier.padding(end = 8.dp),
                                            colors = if (host.contains("GoFile", true)) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors()
                                        )
                                    }
                                } else {
                                    // Fallback
                                    AssistChip(
                                        onClick = { onLinkClick(item.link) },
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

fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
fun HiddenWebView(url: String, onLinkFound: (String?) -> Unit) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                
                webViewClient = object : android.webkit.WebViewClient() {
                    private var found = false
                    
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (found) return
                        
                        // Inject safe JS to find link
                        // Heuristic: Find first 'a' tag ending in .mkv or .mp4
                        val js = """
                            (function() {
                                var links = document.getElementsByTagName('a');
                                for(var i=0; i<links.length; i++) {
                                    if(links[i].href.match(/\.(mkv|mp4)$/i)) {
                                        return links[i].href;
                                    }
                                }
                                return null;
                            })();
                        """.trimIndent()
                        
                        view?.evaluateJavascript(js) { result ->
                            // result is a JSON string, e.g. "https://..." or "null"
                            if (result != null && result != "null" && result != "\"null\"") {
                                // Strip quotes if present
                                val cleanLink = result.trim('"')
                                if (cleanLink.isNotEmpty()) {
                                    found = true
                                    onLinkFound(cleanLink)
                                }
                            }
                        }
                    }
                }
            }
        },
        update = { webView ->
            webView.loadUrl(url)
        },
        modifier = Modifier.size(0.dp).alpha(0f)
    )
    
    // Timeout safety
    LaunchedEffect(url) {
        kotlinx.coroutines.delay(15000) // 15s timeout
        onLinkFound(null) // Signal failure
    }
}

