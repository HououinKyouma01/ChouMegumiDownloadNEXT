package com.example.megumidownload.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.megumidownload.Logger
import com.example.megumidownload.ui.components.DiffViewerDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.megumidownload.ConfigManager
import com.example.megumidownload.ConfigKeys
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.input.VisualTransformation
import com.example.megumidownload.ui.components.FilePickerDialog
import com.example.megumidownload.ui.components.TagInputField
import com.example.megumidownload.SmbClientWrapper
import com.example.megumidownload.SeriesManager
import com.example.megumidownload.PermissionHandler
import java.io.File

@Composable
fun SettingsScreen(
    configManager: ConfigManager,
    seriesManager: SeriesManager,
    permissionHandler: PermissionHandler
) {
    val scope = rememberCoroutineScope()

    val host by configManager.host.collectAsState(initial = "")
    val user by configManager.user.collectAsState(initial = "")
    val password by configManager.password.collectAsState(initial = "")
    val remotePath by configManager.remotePath.collectAsState(initial = "")
    val localPath by configManager.localPath.collectAsState(initial = "")
    val localOnly by configManager.localOnly.collectAsState(initial = false)
    val localSourcePath by configManager.localSourcePath.collectAsState(initial = "")

    val smbHost by configManager.smbHost.collectAsState(initial = "")
    val smbUser by configManager.smbUser.collectAsState(initial = "")
    val smbPassword by configManager.smbPassword.collectAsState(initial = "")
    val smbSharePath by configManager.smbSharePath.collectAsState(initial = "")
    
    val prefixSeriesName by configManager.prefixSeriesName.collectAsState(initial = false)
    val appendQuality by configManager.appendQuality.collectAsState(initial = true)
    val autoStart by configManager.autoStart.collectAsState(initial = false)

    var showFilePickerFor by remember { mutableStateOf<String?>(null) }
    var currentPickerPath by remember { mutableStateOf("") }
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importFileContent by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        androidx.compose.foundation.lazy.LazyColumn {
            item {

                SettingsSection("General") {
                    // Permissions Check - Observe the flow for immediate updates
                    val needsPermission by permissionHandler.needsPermission.collectAsState()
                    val hasStorage = !needsPermission
                    
                    // Notification permission check (separate from storage flow for now, or could combine)
                    val hasNotification = remember(permissionHandler) { permissionHandler.hasNotificationPermission() }
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("Permissions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (hasStorage) Icons.Default.Add else Icons.Default.Search, 
                                contentDescription = null,
                                tint = if (hasStorage) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Storage Access: ${if (hasStorage) "Granted" else "Missing"}")
                            if (!hasStorage) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { permissionHandler.requestStoragePermission() }) { Text("Grant") }
                            }
                        }
                        
                        // Notifications
                         Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (hasNotification) Icons.Default.Add else Icons.Default.Search, 
                                contentDescription = null,
                                tint = if (hasNotification) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notifications: ${if (hasNotification) "Granted" else "Missing"}")
                            if (!hasNotification) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { 
                                     permissionHandler.requestNotificationPermission()
                                }) { Text("Grant") }
                            }
                        }
                    }
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoStart,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.AUTO_START, it) } }
                        )
                        Text("Auto Start Download on Launch")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = prefixSeriesName,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.PREFIX_SERIESNAME, it) } }
                        )
                        Text("Prefix Series Name")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = appendQuality,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.APPEND_QUALITY, it) } }
                        )
                        Text("Append Quality [1080p]")
                    }
                    Divider()
                    val debugLogs by configManager.debugLogs.collectAsState(initial = false)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = debugLogs,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.DEBUG_LOGS, it) } }
                        )
                        Text("Enable Debug Logs")
                    }
                }
            }
            
            item {
                SettingsSection("Global Replacements") {
                     var showGlobalReplaceDialog by remember { mutableStateOf(false) }
                     
                     Button(
                        onClick = { showGlobalReplaceDialog = true },
                        modifier = Modifier.fillMaxWidth()
                     ) {
                        Text("Edit Global Replacements")
                     }
                     Text("Substitutions applied to ALL series (e.g. Honorifics, Typos)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=4.dp))
                     
                     if (showGlobalReplaceDialog) {
                        val replacements = com.example.megumidownload.GlobalReplacementManager.getReplacements()
                        val initialContent = replacements.joinToString("\n") { "${it.first}|${it.second}" }
                        
                        ReplaceEditorDialog(
                            initialContent = initialContent,
                            allowFetch = false,
                            onDismiss = { showGlobalReplaceDialog = false },
                            onSave = { content ->
                                val newReplacements = content.lines()
                                    .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("|") }
                                    .map { line ->
                                        val parts = line.split("|", limit = 2)
                                        parts[0] to parts[1]
                                    }
                                com.example.megumidownload.GlobalReplacementManager.saveReplacements(newReplacements)
                                showGlobalReplaceDialog = false
                            }
                        )
                     }
                }
            }
            
            item {
                SettingsSection("Local Storage") {
                    SettingsTextField(
                        value = localPath,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.LOCAL_PATH, it) } },
                        label = "Local Path (Destination)",
                        supportingText = "Local folder where files will be downloaded/synced",
                        trailingIcon = {
                            IconButton(onClick = { 
                                currentPickerPath = localPath
                                showFilePickerFor = "localPath" 
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Select")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = localOnly,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.LOCAL_ONLY, it) } }
                        )
                        Text("Local Files Only (Skip SFTP)")
                    }
                    if (localOnly) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsTextField(
                            value = localSourcePath,
                            onValueChange = { newValue: String -> scope.launch { configManager.updateConfig(ConfigKeys.LOCAL_SOURCE_PATH, newValue) } },
                            label = "Local Source Path",
                            supportingText = "Source folder for local file import",
                            trailingIcon = {
                                IconButton(onClick = { 
                                    currentPickerPath = localSourcePath
                                    showFilePickerFor = "localSourcePath" 
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "Select")
                                }
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection("SFTP (Download Source)") {
                    SettingsTextField(
                        value = host,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.HOST, it) } },
                        label = "SFTP Host",
                        supportingText = "e.g., 192.168.1.100 or example.com"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        value = user,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.USER, it) } },
                        label = "SFTP User",
                        supportingText = "Username for SFTP connection"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        value = password,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.PASSWORD, it) } },
                        label = "SFTP Password",
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        value = remotePath,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.REMOTE_PATH, it) } },
                        label = "Remote Path",
                        supportingText = "Root folder on SFTP server (e.g., /home/user/downloads)"
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Optimization (Desktop)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    
                    val parallelDownloads by configManager.parallelDownloads.collectAsState(initial = 1)
                    val batchProcessing by configManager.batchProcessing.collectAsState(initial = false)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = batchProcessing,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.BATCH_PROCESSING, it) } }
                        )
                        Column {
                            Text("Batch Downloading")
                            Text("Download all files first, then process them sequentially.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    if (batchProcessing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Parallel Downloads: $parallelDownloads")
                        Slider(
                            value = parallelDownloads.toFloat(),
                            onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.PARALLEL_DOWNLOADS, it.toInt()) } },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                    }
                }
            }

            item {
                SettingsSection("SMB (Sync Target)") {
                    SettingsTextField(
                        value = smbHost,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.SMB_HOST, it) } },
                        label = "SMB Host",
                        supportingText = "IP address or hostname of SMB server"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        value = smbUser,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.SMB_USER, it) } },
                        label = "SMB User",
                        supportingText = "Username for SMB connection"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        value = smbPassword,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.SMB_PASSWORD, it) } },
                        label = "SMB Password",
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        value = smbSharePath,
                        onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.SMB_SHARE_PATH, it) } },
                        label = "SMB Share Path",
                        supportingText = "Share name and path (e.g., Anime or Anime/Subfolder)",
                        trailingIcon = {
                            IconButton(onClick = { 
                                currentPickerPath = smbSharePath
                                showFilePickerFor = "smbSharePath" 
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Select")
                            }
                        }
                    )
                }
            }

            item {
                SettingsSection("Filter by Group (Sync)") {
                    val enableGroups by configManager.enableGroups.collectAsState(initial = false)
                    var showGroupsDialog by remember { mutableStateOf(false) }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = enableGroups,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.ENABLE_GROUPS, it) } }
                        )
                        Text("Enable Group Filtering")
                    }

                    if (enableGroups) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showGroupsDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit Groups (Sync)")
                        }
                    }
                    
                    if (showGroupsDialog) {
                        GroupsEditorDialog(
                            seriesManager = seriesManager,
                            onDismiss = { showGroupsDialog = false },
                            onSave = { content ->
                                val file = java.io.File(seriesManager.dataDir, "groups.megumi")
                                file.writeText(content)
                                Logger.i("Settings", "Groups saved")
                                showGroupsDialog = false
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection("RSS Downloader") {
                    val rssEnabled by configManager.rssEnabled.collectAsState(initial = false)
                    val rssGroup by configManager.rssGroup.collectAsState(initial = "")
                    val rssQuality by configManager.rssQuality.collectAsState(initial = "1080p")
                    var qualityExpanded by remember { mutableStateOf(false) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rssEnabled,
                            onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.RSS_ENABLED, it) } }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable RSS Downloader Tab")
                    }
                    
                    if (rssEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TagInputField(
                            value = rssGroup,
                            onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.RSS_GROUP, it) } },
                            label = "Group Names (comma separated priority)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = rssQuality,
                                onValueChange = { _: String -> },
                                label = { Text("Quality") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { qualityExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { qualityExpanded = true }
                            )
                            DropdownMenu(
                                expanded = qualityExpanded,
                                onDismissRequest = { qualityExpanded = false }
                            ) {
                                listOf("480p", "720p", "1080p", "2160p").forEach { quality ->
                                    DropdownMenuItem(
                                        text = { Text(quality) },
                                        onClick = {
                                            scope.launch { configManager.updateConfig(ConfigKeys.RSS_QUALITY, quality) }
                                            qualityExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        val autoDownloadGofile by configManager.autoDownloadGofile.collectAsState(initial = false)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = autoDownloadGofile,
                                onCheckedChange = { scope.launch { configManager.updateConfig(ConfigKeys.AUTO_DOWNLOAD_GOFILE, it) } }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Auto Download from GoFile")
                                Text("Attempts to find direct link and start system download automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Background Checks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val checkInterval by configManager.rssCheckIntervalHours.collectAsState(initial = 1)
                        Column {
                            Text("Network", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
                            Spacer(modifier = Modifier.height(8.dp))
            
                            Text("RSS Check Interval (Hours): $checkInterval") 
                            Slider(
                                value = checkInterval.toFloat(),
                                onValueChange = { scope.launch { configManager.updateConfig(ConfigKeys.RSS_CHECK_INTERVAL_HOURS, it.toInt().coerceAtLeast(1)) } },
                                valueRange = 1f..24f,
                                steps = 22
                            )

                            // Maintenance
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Maintenance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    // TODO: Abstract WorkManager Prune logic using BackgroundScheduler
                                    Logger.i("Settings", "Queue clearing not implemented in common yet")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Force Clear Download Queue (Not Impl)")
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection("Series Data") {
                    // Import Select Button using FilePicker
                    Button(
                        onClick = {
                             currentPickerPath = System.getProperty("user.home") ?: "/"
                             showFilePickerFor = "importSeries"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Series List (Select File)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                             currentPickerPath = System.getProperty("user.home") ?: "/"
                             showFilePickerFor = "exportSeries"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Series List (Select Folder)")
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportSeriesDialog(
            fileLines = importFileContent,
            onDismiss = { showImportDialog = false },
            onImport = { selectedLines ->
                val tempFile = java.io.File(seriesManager.dataDir, "temp_import.megumi")
                tempFile.writeText(selectedLines.joinToString("\n"))
                val count = seriesManager.importSeries(tempFile)
                Logger.i("Settings", "Imported $count series")
                showImportDialog = false
            }
        )
    }

    if (showFilePickerFor != null) {
        if (showFilePickerFor == "smbSharePath") {
            SmbFilePickerDialog(
                host = smbHost,
                user = smbUser,
                pass = smbPassword,
                initialPath = currentPickerPath,
                onDismiss = { showFilePickerFor = null },
                onConfirm = { path ->
                    scope.launch {
                        configManager.updateConfig(ConfigKeys.SMB_SHARE_PATH, path)
                        showFilePickerFor = null
                    }
                }
            )
        } else {
            FilePickerDialog(
                initialPath = currentPickerPath,
                onDismiss = { showFilePickerFor = null },
                onConfirm = { path ->
                    Logger.d("SettingsScreen", "FilePicker confirmed path: $path for $showFilePickerFor")
                    scope.launch {
                        try {
                            if (showFilePickerFor == "localPath") {
                                configManager.updateConfig(ConfigKeys.LOCAL_PATH, path)
                            } else if (showFilePickerFor == "localSourcePath") {
                                configManager.updateConfig(ConfigKeys.LOCAL_SOURCE_PATH, path)
                            } else if (showFilePickerFor == "importSeries") {
                                // For import, we expect a file, but FilePickerDialog picks directories?
                                // My FilePickerDialog picks directories or files? 
                                // Step 1614: FilePickerDialog shows dirs. Items are clickable.
                                // It seems to select the `currentPath`.
                                // If I want to select a file, I need to check if FilePickerDialog supports file selection.
                                // Step 1614: `files.filter { it.isDirectory }` -> It ONLY shows directories.
                                // So I cannot pick a file with my current FilePickerDialog.
                                // I will treat it as "Select Directory" and look for "serieslist.megumi" inside, or ask to pick a dir.
                                // I will assume user puts "serieslist.megumi" in the selected dir for Import.
                                val dir = File(path)
                                val file = File(dir, "serieslist.megumi")
                                if (file.exists()) {
                                    importFileContent = file.readLines()
                                    showImportDialog = true
                                } else {
                                    Logger.w("Settings", "serieslist.megumi not found in $path")
                                }
                            } else if (showFilePickerFor == "exportSeries") {
                                val dir = File(path)
                                val file = File(dir, "serieslist_export.megumi")
                                seriesManager.exportSeries(file)
                                Logger.i("Settings", "Exported to ${file.absolutePath}")
                            }
                        } catch (e: Exception) {
                            Logger.e("SettingsScreen", "Error saving path: ${e.message}")
                        } finally {
                            showFilePickerFor = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(value) }
    
    LaunchedEffect(value) {
        if (text != value && (text.isEmpty() || value.isNotEmpty())) {
             text = value
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { 
            text = it
            onValueChange(it) 
        },
        label = { Text(label) },
        supportingText = if (supportingText != null) { { Text(supportingText) } } else null,
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = trailingIcon
    )
}

@Composable
fun SmbFilePickerDialog(
    host: String,
    user: String,
    pass: String,
    initialPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val client = remember { SmbClientWrapper() }
    
    val parts = initialPath.split("/", limit = 2)
    var shareName by remember { mutableStateOf(parts.getOrNull(0) ?: "") }
    var currentPath by remember { mutableStateOf(parts.getOrNull(1) ?: "") }
    
    var files by remember { mutableStateOf<List<SmbClientWrapper.SmbFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isConnected by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch { client.disconnect() }
        }
    }

    fun loadFiles() {
        if (shareName.isBlank()) return
        scope.launch {
            isLoading = true
            error = null
            try {
                if (!isConnected) {
                    client.connect(host, user, pass, shareName)
                    isConnected = true
                }
                files = client.listFiles(currentPath).sortedBy { !it.isDirectory }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
                isConnected = false 
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(shareName, currentPath) {
        if (shareName.isNotBlank()) {
            loadFiles()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Remote Folder", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (shareName.isBlank() || !isConnected) {
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("Share Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { loadFiles() },
                        modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                        enabled = shareName.isNotBlank()
                    ) {
                        Text("Connect")
                    }
                } else {
                    Text("Path: $shareName/$currentPath", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { loadFiles() }) { Text("Retry") }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                            if (currentPath.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val p = currentPath.trimEnd('/')
                                                val lastSlash = p.lastIndexOf('/')
                                                currentPath = if (lastSlash > 0) p.substring(0, lastSlash) else ""
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Up") 
                                        Text("..", modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                            
                            items(files) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (file.isDirectory) {
                                                currentPath = if (currentPath.isEmpty()) file.name else "$currentPath/${file.name}"
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (file.isDirectory) Icons.Default.Search else Icons.Default.Add, 
                                        contentDescription = null,
                                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        file.name,
                                        modifier = Modifier.padding(start = 8.dp),
                                        color = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val fullPath = if (currentPath.isEmpty()) shareName else "$shareName/$currentPath"
                        onConfirm(fullPath)
                    }) { Text("Select Current") }
                }
            }
        }
    }
}

@Composable
fun ImportSeriesDialog(
    fileLines: List<String>,
    onDismiss: () -> Unit,
    onImport: (List<String>) -> Unit
) {
    val validLines = fileLines.filter { it.isNotBlank() && !it.trim().startsWith("#") && it.contains("|") }
    val selectionStates = remember { mutableStateListOf<Boolean>().apply { addAll(validLines.map { true }) } }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxHeight(0.8f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Import Series", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                    items(validLines.size) { index ->
                        val line = validLines[index]
                        val parts = line.split("|")
                        val label = if (parts.size >= 2) "${parts[1].trim()} (${parts[0].trim()})" else line
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectionStates[index] = !selectionStates[index] }
                        ) {
                            Checkbox(checked = selectionStates[index], onCheckedChange = { selectionStates[index] = it })
                            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val selected = validLines.filterIndexed { index, _ -> selectionStates[index] }
                        onImport(selected)
                    }) { Text("Import") }
                }
            }
        }
    }
}

@Composable
fun GroupsEditorDialog(
    seriesManager: SeriesManager, // Passed explicitly
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val file = java.io.File(seriesManager.dataDir, "groups.megumi")
        if (file.exists()) {
            content = file.readText()
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Groups", style = MaterialTheme.typography.titleLarge)
                Text("One group per line. Lines starting with # are comments.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(content) }) { Text("Save") }
                }
            }
        }
    }
}
