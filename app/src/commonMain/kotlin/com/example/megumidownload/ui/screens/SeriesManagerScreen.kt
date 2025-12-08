package com.example.megumidownload.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import com.example.megumidownload.ConfigManager
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Create
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.example.megumidownload.Logger
import com.example.megumidownload.ui.components.DiffViewerDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.megumidownload.SyncManager
import com.example.megumidownload.SmbClientWrapper
import com.example.megumidownload.SeriesEntry
import com.example.megumidownload.SeriesManager
import com.example.megumidownload.BackgroundScheduler
import java.io.File
import kotlinx.coroutines.launch
import com.example.megumidownload.RssItem
import com.example.megumidownload.CharacterNameFetcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.StandardCopyOption


@Composable
fun SeriesManagerScreen(
    seriesManager: SeriesManager,
    syncManager: SyncManager,
    configManager: ConfigManager,
    backgroundScheduler: BackgroundScheduler?
) {
    val scope = rememberCoroutineScope()
    
    var seriesList by remember { mutableStateOf(seriesManager.getSeriesList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    // FAB Menu State
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showGlobalSyncDialog by remember { mutableStateOf(false) }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var seriesToEdit by remember { mutableStateOf<SeriesEntry?>(null) }
    
    var showReplaceEditor by remember { mutableStateOf(false) }
    var replaceSeries by remember { mutableStateOf<SeriesEntry?>(null) }
    var replaceFileContent by remember { mutableStateOf("") }
    
    var showRemoteReplaceEditor by remember { mutableStateOf(false) }
    var remoteReplaceSeries by remember { mutableStateOf<SeriesEntry?>(null) }
    var remoteReplaceContent by remember { mutableStateOf("") }
    
    // Sync State
    var showSyncProgress by remember { mutableStateOf(false) }
    var syncProgressMessage by remember { mutableStateOf("") }
    
    var globalSyncProgress by remember { mutableStateOf<SyncManager.SyncProgress?>(null) }
    var showGlobalSyncProgressDialog by remember { mutableStateOf(false) }
    
    var showSingleSyncDialog by remember { mutableStateOf(false) }
    var singleSyncSeries by remember { mutableStateOf<SeriesEntry?>(null) }
    
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictLocalFile by remember { mutableStateOf<File?>(null) }
    var conflictRemoteFile by remember { mutableStateOf<SmbClientWrapper.SmbFile?>(null) }
    var onConflictResolve by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    
    var rememberConflictChoice by remember { mutableStateOf(false) }
    var conflictChoice by remember { mutableStateOf(false) }
    
    // Diff Viewer State
    var showDiffDialog by remember { mutableStateOf(false) }
    var diffLocalContent by remember { mutableStateOf("") }
    var diffRemoteContent by remember { mutableStateOf("") }
    var onDiffResolve by remember { mutableStateOf<((String) -> Unit)?>(null) }
    
    val localPath by configManager.localPath.collectAsState(initial = "")
    val smbHost by configManager.smbHost.collectAsState(initial = "")
    
    // Generic Error Dialog for debugging
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

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = { 
                                showGlobalSyncDialog = true
                                isMenuExpanded = false
                            },
                            modifier = Modifier.padding(bottom = 16.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync All")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync All")
                            }
                        }
                        
                        FloatingActionButton(
                            onClick = { 
                                showAddDialog = true 
                                isMenuExpanded = false
                            },
                            modifier = Modifier.padding(bottom = 16.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = "Add Series")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Series")
                            }
                        }
                    }
                }

                FloatingActionButton(onClick = { isMenuExpanded = !isMenuExpanded }) {
                    Icon(
                        if (isMenuExpanded) Icons.Default.Close else Icons.Default.Menu,
                        contentDescription = if (isMenuExpanded) "Close Menu" else "Menu"
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Series Manager", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                items(seriesList) { series ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(series.folderName, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = {
                                        seriesToEdit = series
                                        showEditDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                }
                            }
                            Text("Match: ${series.fileNameMatch}", style = MaterialTheme.typography.bodyMedium)
                            Text("Season: ${series.seasonNumber}", style = MaterialTheme.typography.bodyMedium)
                            if (series.fixTiming) {
                                Text("Fix Timing: ON", color = MaterialTheme.colorScheme.primary)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        if (localPath.isNotBlank()) {
                                            val file = java.io.File(localPath, "${series.folderName}/Season ${series.seasonNumber}/replace.txt")
                                            if (file.exists()) {
                                                replaceFileContent = file.readText()
                                            } else {
                                                replaceFileContent = ""
                                            }
                                            replaceSeries = series
                                            showReplaceEditor = true
                                        } else {
                                            Logger.w("SeriesManager", "Local Path not set")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Edit Replacements")
                                }
                                
                                if (smbHost.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val result = syncManager.getRemoteReplaceContent(series)
                                                result.onSuccess { content ->
                                                    remoteReplaceContent = content
                                                    remoteReplaceSeries = series
                                                    showRemoteReplaceEditor = true
                                                }.onFailure { e ->
                                                    Logger.e("SeriesManager", "Error fetching remote replace: ${e.message}")
                                                    errorMessage = "Error: ${e.message}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Remote Edit")
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        singleSyncSeries = series
                                        showSingleSyncDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showSyncProgress) {
        Dialog(onDismissRequest = { }) {
            Card {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(syncProgressMessage)
                }
            }
        }
    }
    
    if (showConflictDialog && conflictLocalFile != null && conflictRemoteFile != null) {
        var rememberChoice by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { onConflictResolve?.invoke(false) },
            title = { Text("File Conflict") },
            text = { 
                Column {
                    Text("Remote file already exists and differs in size.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("File: ${conflictLocalFile!!.name}")
                    Text("Local Size: ${conflictLocalFile!!.length()} bytes")
                    Text("Remote Size: ${conflictRemoteFile!!.size} bytes")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                        Text("Remember choice for this sync")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Do you want to overwrite the remote file?")
                }
            },
            confirmButton = {
                Row {
                    if (conflictLocalFile!!.name == "replace.txt") {
                        Button(
                            onClick = {
                                scope.launch {
                                    // Fetch remote content
                                    try {
                                        val remoteContent = syncManager.readRemoteFileContent(conflictRemoteFile!!.path)
                                        diffLocalContent = conflictLocalFile!!.readText()
                                        diffRemoteContent = remoteContent
                                        
                                        onDiffResolve = { mergedContent ->
                                            scope.launch {
                                                try {
                                                    // Save to local
                                                    conflictLocalFile!!.writeText(mergedContent)
                                                    // Save to remote
                                                    syncManager.writeRemoteFileContent(conflictRemoteFile!!.path, mergedContent)
                                                    
                                                    showDiffDialog = false
                                                    // Resolve conflict as "Skip" (false) because we manually handled it
                                                    onConflictResolve?.invoke(false)
                                                } catch (e: Exception) {
                                                    Logger.e("SeriesManager", "Error saving merge: ${e.message}")
                                                }
                                            }
                                        }
                                        showConflictDialog = false
                                        showDiffDialog = true
                                    } catch (e: Exception) {
                                        Logger.e("SeriesManager", "Error reading remote file: ${e.message}")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Diff / Merge")
                        }
                    }
                    
                    Button(onClick = { 
                        if (rememberChoice) {
                            rememberConflictChoice = true
                            conflictChoice = true
                        }
                        onConflictResolve?.invoke(true) 
                    }) {
                        Text("Overwrite")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (rememberChoice) {
                        rememberConflictChoice = true
                        conflictChoice = false
                    }
                    onConflictResolve?.invoke(false) 
                }) {
                    Text("Skip")
                }
            }
        )
    }
    
    if (showDiffDialog) {
        DiffViewerDialog(
            localContent = diffLocalContent,
            remoteContent = diffRemoteContent,
            onDismiss = { 
                showDiffDialog = false
                showConflictDialog = true
            },
            onResolve = { content ->
                onDiffResolve?.invoke(content)
            }
        )
    }

    if (showAddDialog) {
        AddSeriesDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { entry ->
                seriesManager.addSeries(entry)
                seriesList = seriesManager.getSeriesList()
                showAddDialog = false
            }
        )
    }
    
    if (showEditDialog && seriesToEdit != null) {
        EditSeriesDialog(
            series = seriesToEdit!!,
            onDismiss = { showEditDialog = false },
            onUpdate = { newEntry ->
                seriesManager.updateSeries(seriesToEdit!!, newEntry)
                seriesList = seriesManager.getSeriesList()
                showEditDialog = false
            },
            onDelete = {
                seriesManager.deleteSeries(seriesToEdit!!)
                seriesList = seriesManager.getSeriesList()
                showEditDialog = false
            }
        )
    }

    if (showGlobalSyncDialog) {
        GlobalSyncDialog(
            seriesList = seriesList,
            onDismiss = { showGlobalSyncDialog = false },
            onSync = { selectedSeries, isLocalToRemote, syncFilelist, syncReplace, syncEpisodes ->
                showGlobalSyncDialog = false
                showGlobalSyncProgressDialog = true
                rememberConflictChoice = false 
                scope.launch {
                    val options = SyncManager.SyncOptions(isLocalToRemote, syncFilelist, syncReplace, syncEpisodes)
                    val result = syncManager.syncAll(
                        seriesList = selectedSeries,
                        options = options,
                        onProgress = { progress -> globalSyncProgress = progress },
                        onConflict = { local, remote ->
                            if (rememberConflictChoice) {
                                return@syncAll conflictChoice
                            }
                            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                conflictLocalFile = local
                                conflictRemoteFile = remote
                                onConflictResolve = { overwrite ->
                                    showConflictDialog = false
                                    cont.resume(overwrite) { }
                                }
                                showConflictDialog = true
                            }
                        }
                    )
                    showGlobalSyncProgressDialog = false
                    if (result is SyncManager.SyncResult.Error) {
                        Logger.e("SeriesManager", "Sync Error: ${result.message}")
                    } else {
                        Logger.i("SeriesManager", "Global Sync Completed")
                    }
                }
            }
        )
    }

    if (showGlobalSyncProgressDialog && globalSyncProgress != null) {
        Dialog(onDismissRequest = { }) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Syncing...", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Series: ${globalSyncProgress!!.currentSeries} (${globalSyncProgress!!.seriesIndex + 1}/${globalSyncProgress!!.totalSeries})")
                    LinearProgressIndicator(progress = globalSyncProgress!!.totalProgress, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("File: ${globalSyncProgress!!.currentFile}")
                    LinearProgressIndicator(progress = globalSyncProgress!!.fileProgress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showSingleSyncDialog && singleSyncSeries != null) {
        SingleSeriesSyncDialog(
            series = singleSyncSeries!!,
            syncManager = syncManager,
            onDismiss = { showSingleSyncDialog = false },
            onSync = { options ->
                showSingleSyncDialog = false
                rememberConflictChoice = false 
                scope.launch {
                    showSyncProgress = true
                    syncProgressMessage = "Starting sync..."
                    
                    val result = syncManager.syncAll(
                        seriesList = listOf(singleSyncSeries!!),
                        options = options,
                        onProgress = { progress -> 
                            syncProgressMessage = "${progress.currentFile} (${(progress.fileProgress * 100).toInt()}%)"
                        },
                        onConflict = { local, remote ->
                            if (rememberConflictChoice) {
                                return@syncAll conflictChoice
                            }
                             @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                             kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                conflictLocalFile = local
                                conflictRemoteFile = remote
                                onConflictResolve = { overwrite ->
                                    showConflictDialog = false
                                    cont.resume(overwrite) { }
                                }
                                showConflictDialog = true
                            }
                        }
                    )
                    showSyncProgress = false
                    if (result is SyncManager.SyncResult.Error) {
                        Logger.e("SeriesManager", "Sync Error: ${result.message}")
                    } else {
                        Logger.i("SeriesManager", "Sync Completed")
                    }
                }
            }
        )
    }
    
    if (showReplaceEditor && replaceSeries != null) {
        ReplaceEditorDialog(
            initialContent = replaceFileContent,
            onDismiss = { showReplaceEditor = false },
            onSave = { content ->
                if (localPath.isNotBlank()) {
                    val dir = java.io.File(localPath, "${replaceSeries!!.folderName}/Season ${replaceSeries!!.seasonNumber}")
                    if (!dir.exists()) dir.mkdirs()
                    val file = java.io.File(dir, "replace.txt")
                    file.writeText(content)
                    Logger.i("SeriesManager", "Saved replace.txt")
                }
                showReplaceEditor = false
            }
        )
    }

    if (showRemoteReplaceEditor && remoteReplaceSeries != null) {
        ReplaceEditorDialog(
            initialContent = remoteReplaceContent,
            onDismiss = { showRemoteReplaceEditor = false },
            onSave = { content ->
                scope.launch {
                    val success = syncManager.saveRemoteReplaceContent(remoteReplaceSeries!!, content)
                    if (success) {
                        Logger.i("SeriesManager", "Saved remote replace.txt")
                    } else {
                        Logger.e("SeriesManager", "Failed to save remote replace.txt")
                        // We could show error here too, but let's focus on "Open" first
                    }
                    showRemoteReplaceEditor = false
                }
            }
        )
    }
    
    // ... inside Remote Edit button ...
    // Note: I need to update the Remote Edit button onClick listener to set errorMessage
}

// Dialog Implementations

@Composable
fun ReplaceEditorDialog(initialContent: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var content by remember { mutableStateOf(initialContent) }
    var error by remember { mutableStateOf<String?>(null) }
    
    var showFetchDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isFetching by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit replace.txt", style = MaterialTheme.typography.titleLarge)
                Text("Format: OldText|NewText (one per line)", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
                
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // Left side: Fetch Button
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = { showFetchDialog = true }) {
                            Text("Fetch Names")
                        }
                    }

                    // Right side: Save/Cancel
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val lines = content.lines()
                            val invalidLines = lines.filter { it.isNotBlank() && !it.contains("|") && !it.startsWith("#") }
                            if (invalidLines.isNotEmpty()) {
                                error = "Invalid lines (missing '|'): ${invalidLines.take(3)}"
                            } else {
                                onSave(content)
                            }
                        }) { Text("Save") }
                    }
                }
            }
        }
    }

    if (showFetchDialog) {
        var urlInput by remember { mutableStateOf("") }
        var fetchError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showFetchDialog = false },
            title = { Text("Fetch Character Names") },
            text = {
                Column {
                    Text("Enter MyAnimeList or AniDB URL:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlInput, 
                        onValueChange = { urlInput = it },
                        label = { Text("URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (fetchError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(fetchError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (urlInput.isNotBlank()) {
                        showFetchDialog = false
                        isFetching = true
                        scope.launch {
                            try {
                                val names = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    CharacterNameFetcher.fetchNames(urlInput)
                                }
                                if (names.isNotEmpty()) {
                                    // Parse existing content to check for duplicates
                                    val existingLines = content.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
                                    val sb = StringBuilder(content)
                                    if (content.isNotBlank() && !content.endsWith("\n")) {
                                        sb.append("\n")
                                    }
                                    
                                    var addedCount = 0
                                    names.forEach { name ->
                                        val newLine = "${name.original}|${name.replacement}"
                                        // Check if this exact line exists (ignoring whitespace differences around it)
                                        if (!existingLines.contains(newLine)) {
                                            sb.append(newLine).append("\n")
                                            addedCount++
                                        }
                                    }
                                    
                                    if (addedCount > 0) {
                                        content = sb.toString()
                                        Logger.i("ReplaceEditor", "Added $addedCount new names")
                                    } else {
                                        Logger.i("ReplaceEditor", "No new unique names found to add")
                                    }
                                } else {
                                    error = "No names found."
                                }
                            } catch (e: Exception) {
                                Logger.e("ReplaceEditor", "Error fetching names: ${e.message}")
                                error = "Error: ${e.message}"
                            } finally {
                                isFetching = false
                            }
                        }
                    }
                }) {
                    Text("Fetch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFetchDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EditSeriesDialog(series: SeriesEntry, onDismiss: () -> Unit, onUpdate: (SeriesEntry) -> Unit, onDelete: (() -> Unit)? = null) {
    var fileNameMatch by remember { mutableStateOf(series.fileNameMatch) }
    var folderName by remember { mutableStateOf(series.folderName) }
    var seasonNumber by remember { mutableStateOf(series.seasonNumber) }
    var fixTiming by remember { mutableStateOf(series.fixTiming) }
    var replaceUrl by remember { mutableStateOf(series.replaceUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Series", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(value = fileNameMatch, onValueChange = { fileNameMatch = it }, label = { Text("Filename Match") })
                OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Folder Name") })
                OutlinedTextField(value = seasonNumber, onValueChange = { seasonNumber = it }, label = { Text("Season Number") })
                
                OutlinedTextField(value = replaceUrl, onValueChange = { replaceUrl = it }, label = { Text("Remote Replace URL (Optional)") })
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fixTiming, onCheckedChange = { fixTiming = it })
                    Text("Fix Timing")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete Series") },
                                text = { Text("Are you sure you want to delete this series?") },
                                confirmButton = {
                                    Button(
                                        onClick = { onDelete() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp)) 
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = {
                            if (fileNameMatch.isNotBlank() && folderName.isNotBlank()) {
                                onUpdate(SeriesEntry(fileNameMatch, folderName, seasonNumber, replaceUrl, fixTiming))
                            }
                        }) { Text("Update") }
                    }
                }
            }
        }
    }
}

@Composable
fun AddSeriesDialog(onDismiss: () -> Unit, onAdd: (SeriesEntry) -> Unit) {
    var fileNameMatch by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var seasonNumber by remember { mutableStateOf("1") }
    var fixTiming by remember { mutableStateOf(false) }
    var replaceUrl by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Series", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(value = fileNameMatch, onValueChange = { fileNameMatch = it }, label = { Text("Filename Match") })
                OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Folder Name") })
                OutlinedTextField(value = seasonNumber, onValueChange = { seasonNumber = it }, label = { Text("Season Number") })
                
                OutlinedTextField(value = replaceUrl, onValueChange = { replaceUrl = it }, label = { Text("Remote Replace URL (Optional)") })
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fixTiming, onCheckedChange = { fixTiming = it })
                    Text("Fix Timing")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        if (fileNameMatch.isNotBlank() && folderName.isNotBlank()) {
                            onAdd(SeriesEntry(fileNameMatch, folderName, seasonNumber, replaceUrl, fixTiming))
                        }
                    }) { Text("Add") }
                }
            }
        }
    }
}

@Composable
fun GlobalSyncDialog(
    seriesList: List<SeriesEntry>,
    onDismiss: () -> Unit,
    onSync: (List<SeriesEntry>, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    val selectionStates = remember { mutableStateListOf<Boolean>().apply { addAll(seriesList.map { true }) } }
    var isLocalToRemote by remember { mutableStateOf(false) } 
    var syncFilelist by remember { mutableStateOf(true) }
    var syncReplace by remember { mutableStateOf(true) }
    var syncEpisodes by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxHeight(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Global Sync", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Direction: ", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(if (isLocalToRemote) "Local -> Remote" else "Remote -> Local", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isLocalToRemote, onCheckedChange = { isLocalToRemote = it })
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncFilelist, onCheckedChange = { syncFilelist = it })
                    Text("Sync filelist.txt")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncReplace, onCheckedChange = { syncReplace = it })
                    Text("Sync replace.txt")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncEpisodes, onCheckedChange = { syncEpisodes = it })
                    Text("Sync Episodes (MKV)")
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { selectionStates.fill(true) }) { Text("Select All") }
                    TextButton(onClick = { selectionStates.fill(false) }) { Text("Select None") }
                }
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(seriesList.size) { index ->
                        val series = seriesList[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectionStates[index] = !selectionStates[index] }
                        ) {
                            Checkbox(checked = selectionStates[index], onCheckedChange = { selectionStates[index] = it })
                            Text(series.folderName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val selectedSeries = seriesList.filterIndexed { index, _ -> selectionStates[index] }
                        onSync(selectedSeries, isLocalToRemote, syncFilelist, syncReplace, syncEpisodes)
                    }) { Text("Sync (${seriesList.filterIndexed { index, _ -> selectionStates[index] }.size})") }
                }
            }
        }
    }
}

@Composable
fun SingleSeriesSyncDialog(
    series: SeriesEntry,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onSync: (SyncManager.SyncOptions) -> Unit
) {
    var isLocalToRemote by remember { mutableStateOf(false) }
    var syncFilelist by remember { mutableStateOf(true) }
    var syncReplace by remember { mutableStateOf(true) }
    var syncEpisodes by remember { mutableStateOf(true) }
    
    var showEpisodeSelection by remember { mutableStateOf(false) }
    var availableFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<List<String>?>(null) } 
    var isLoadingFiles by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    LaunchedEffect(isLocalToRemote) {
        selectedFiles = null
        availableFiles = emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sync ${series.folderName}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Direction: ", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(if (isLocalToRemote) "Local -> Remote" else "Remote -> Local", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isLocalToRemote, onCheckedChange = { isLocalToRemote = it })
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("What to Sync:", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncEpisodes, onCheckedChange = { syncEpisodes = it })
                    Text("Episodes")
                    Spacer(modifier = Modifier.width(8.dp))
                    if (syncEpisodes) {
                        if (isLoadingFiles) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            TextButton(onClick = { 
                                scope.launch {
                                    isLoadingFiles = true
                                    val result = if (isLocalToRemote) {
                                        syncManager.fetchLocalFiles(series)
                                    } else {
                                        syncManager.fetchRemoteFiles(series)
                                    }
                                    isLoadingFiles = false
                                    if (result.isSuccess) {
                                        availableFiles = result.getOrDefault(emptyList())
                                        showEpisodeSelection = true
                                    } else {
                                        Logger.e("SingleSeriesSyncDialog", "Error fetching files: ${result.exceptionOrNull()?.message}")
                                    }
                                }
                            }) {
                                Text(if (selectedFiles == null) "All" else "${selectedFiles!!.size} Selected")
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncFilelist, onCheckedChange = { syncFilelist = it })
                    Text("filelist.txt")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncReplace, onCheckedChange = { syncReplace = it })
                    Text("replace.txt")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        onSync(SyncManager.SyncOptions(isLocalToRemote, syncFilelist, syncReplace, syncEpisodes, selectedFiles))
                    }) { Text("Sync") }
                }
            }
        }
    }
    
    if (showEpisodeSelection) {
        EpisodeSelectionDialog(
            files = availableFiles,
            onDismiss = { showEpisodeSelection = false },
            onConfirm = { selected ->
                selectedFiles = if (selected.size == availableFiles.size) null else selected
                showEpisodeSelection = false
            }
        )
    }
}

@Composable
fun EpisodeSelectionDialog(
    files: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectionStates = remember { mutableStateListOf<Boolean>().apply { addAll(List(files.size) { true }) } }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Episodes", style = MaterialTheme.typography.titleLarge)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { 
                        for (i in selectionStates.indices) selectionStates[i] = true 
                    }) { Text("Select All") }
                    TextButton(onClick = { 
                        for (i in selectionStates.indices) selectionStates[i] = false 
                    }) { Text("Select None") }
                }
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(files.size) { index ->
                        val file = files[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectionStates[index] = !selectionStates[index] }
                        ) {
                            Checkbox(checked = selectionStates[index], onCheckedChange = { selectionStates[index] = it })
                            Text(file, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val selectedFiles = files.filterIndexed { index, _ -> selectionStates[index] }
                        onConfirm(selectedFiles)
                    }) { Text("Confirm (${files.filterIndexed { index, _ -> selectionStates[index] }.size})") }
                }
            }
        }
    }
}


