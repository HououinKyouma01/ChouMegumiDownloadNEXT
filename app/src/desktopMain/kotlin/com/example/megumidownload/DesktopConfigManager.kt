package com.example.megumidownload

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Properties

class DesktopConfigManager : ConfigManager {
    private val configFile = File(System.getProperty("user.home"), ".megumidownloader/config.properties")
    private val properties = Properties()

    // Host Configs
    private val _host = MutableStateFlow("")
    override val host: Flow<String> = _host.asStateFlow()

    private val _user = MutableStateFlow("")
    override val user: Flow<String> = _user.asStateFlow()

    private val _password = MutableStateFlow("")
    override val password: Flow<String> = _password.asStateFlow()

    private val _remotePath = MutableStateFlow("")
    override val remotePath: Flow<String> = _remotePath.asStateFlow()

    // Local Configs
    private val _localPath = MutableStateFlow("")
    override val localPath: Flow<String> = _localPath.asStateFlow()

    private val _localOnly = MutableStateFlow(false)
    override val localOnly: Flow<Boolean> = _localOnly.asStateFlow()
    
    private val _localSourcePath = MutableStateFlow("")
    override val localSourcePath: Flow<String> = _localSourcePath.asStateFlow()

    private val _prefixSeriesName = MutableStateFlow(false)
    override val prefixSeriesName: Flow<Boolean> = _prefixSeriesName.asStateFlow()
    
    private val _appendQuality = MutableStateFlow(false)
    override val appendQuality: Flow<Boolean> = _appendQuality.asStateFlow()
    
    private val _autoStart = MutableStateFlow(false)
    override val autoStart: Flow<Boolean> = _autoStart.asStateFlow()

    // SMB Configs
    private val _smbHost = MutableStateFlow("")
    override val smbHost: Flow<String> = _smbHost.asStateFlow()
    
    private val _smbUser = MutableStateFlow("")
    override val smbUser: Flow<String> = _smbUser.asStateFlow()
    
    private val _smbPassword = MutableStateFlow("")
    override val smbPassword: Flow<String> = _smbPassword.asStateFlow()
    
    private val _smbSharePath = MutableStateFlow("")
    override val smbSharePath: Flow<String> = _smbSharePath.asStateFlow()
    
    private val _enableGroups = MutableStateFlow(false)
    override val enableGroups: Flow<Boolean> = _enableGroups.asStateFlow()
    
    // Sync
    private val _autoSyncInterval = MutableStateFlow(60L)
    override val autoSyncInterval: Flow<Long> = _autoSyncInterval.asStateFlow()
    
    private val _wifiOnly = MutableStateFlow(false)
    override val wifiOnly: Flow<Boolean> = _wifiOnly.asStateFlow()

    // RSS
    private val _rssEnabled = MutableStateFlow(false)
    override val rssEnabled: Flow<Boolean> = _rssEnabled.asStateFlow()
    
    private val _rssGroup = MutableStateFlow("")
    override val rssGroup: Flow<String> = _rssGroup.asStateFlow()
    
    private val _rssQuality = MutableStateFlow("1080p")
    override val rssQuality: Flow<String> = _rssQuality.asStateFlow()
    
    private val _autoDownloadGofile = MutableStateFlow(false)
    override val autoDownloadGofile: Flow<Boolean> = _autoDownloadGofile.asStateFlow()
    
    private val _rssCheckIntervalHours = MutableStateFlow(1)
    override val rssCheckIntervalHours: Flow<Int> = _rssCheckIntervalHours.asStateFlow()
    
    private val _rssLastCheckTime = MutableStateFlow(0L)
    override val rssLastCheckTime: Flow<Long> = _rssLastCheckTime.asStateFlow()
    
    private val _debugLogs = MutableStateFlow(false)
    override val debugLogs: Flow<Boolean> = _debugLogs.asStateFlow()
    
    // Desktop Specific
    private val _parallelDownloads = MutableStateFlow(1)
    override val parallelDownloads: Flow<Int> = _parallelDownloads.asStateFlow()
    
    private val _batchProcessing = MutableStateFlow(false)
    override val batchProcessing: Flow<Boolean> = _batchProcessing.asStateFlow()
    
    private val _reprocessMode = MutableStateFlow(false)
    override val reprocessMode: Flow<Boolean> = _reprocessMode.asStateFlow()
    
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    
    // Window Config Keys
    private val WINDOW_WIDTH = "window_width"
    private val WINDOW_HEIGHT = "window_height"
    private val WINDOW_X = "window_x"
    private val WINDOW_Y = "window_y"
    private val WINDOW_MAXIMIZED = "window_maximized"

    data class WindowGeometry(
        val width: Int, 
        val height: Int, 
        val x: Int, 
        val y: Int, 
        val isMaximized: Boolean
    )
    
    fun getWindowGeometry(): WindowGeometry {
        val width = properties.getProperty(WINDOW_WIDTH, "1024").toIntOrNull() ?: 1024
        val height = properties.getProperty(WINDOW_HEIGHT, "768").toIntOrNull() ?: 768
        val x = properties.getProperty(WINDOW_X, "100").toIntOrNull() ?: 100
        val y = properties.getProperty(WINDOW_Y, "100").toIntOrNull() ?: 100
        val maximized = properties.getProperty(WINDOW_MAXIMIZED, "false").toBoolean()
        return WindowGeometry(width, height, x, y, maximized)
    }

    fun saveWindowGeometry(geometry: WindowGeometry) {
        properties.setProperty(WINDOW_WIDTH, geometry.width.toString())
        properties.setProperty(WINDOW_HEIGHT, geometry.height.toString())
        properties.setProperty(WINDOW_X, geometry.x.toString())
        properties.setProperty(WINDOW_Y, geometry.y.toString())
        properties.setProperty(WINDOW_MAXIMIZED, geometry.isMaximized.toString())
        
        configFile.outputStream().use { properties.store(it, "Megumi Downloader Config") }
    }
    
    init {
        loadConfig()
        scope.launch {
            debugLogs.collect { enabled ->
                Logger.debugEnabled = enabled
                println("DesktopConfigManager: Debug logs enabled: $enabled")
            }
        }
    }

    private fun loadConfig() {
        if (!configFile.parentFile.exists()) {
             configFile.parentFile.mkdirs()
        }
        if (configFile.exists()) {
            configFile.inputStream().use { properties.load(it) }
        }
        
        // Load String props
        _host.value = properties.getProperty(ConfigKeys.HOST.keyName, "")
        _user.value = properties.getProperty(ConfigKeys.USER.keyName, "")
        _password.value = properties.getProperty(ConfigKeys.PASSWORD.keyName, "")
        _remotePath.value = properties.getProperty(ConfigKeys.REMOTE_PATH.keyName, "")
        _localPath.value = properties.getProperty(ConfigKeys.LOCAL_PATH.keyName, "")
        _localSourcePath.value = properties.getProperty(ConfigKeys.LOCAL_SOURCE_PATH.keyName, "")
        
        _smbHost.value = properties.getProperty(ConfigKeys.SMB_HOST.keyName, "")
        _smbUser.value = properties.getProperty(ConfigKeys.SMB_USER.keyName, "")
        _smbPassword.value = properties.getProperty(ConfigKeys.SMB_PASSWORD.keyName, "")
        _smbSharePath.value = properties.getProperty(ConfigKeys.SMB_SHARE_PATH.keyName, "")

        _rssGroup.value = properties.getProperty(ConfigKeys.RSS_GROUP.keyName, "")
        _rssQuality.value = properties.getProperty(ConfigKeys.RSS_QUALITY.keyName, "1080p")
        
        // Load Boolean props
        _localOnly.value = properties.getProperty(ConfigKeys.LOCAL_ONLY.keyName, "false").toBoolean()
        _prefixSeriesName.value = properties.getProperty(ConfigKeys.PREFIX_SERIESNAME.keyName, "false").toBoolean()
        _appendQuality.value = properties.getProperty(ConfigKeys.APPEND_QUALITY.keyName, "false").toBoolean()
        _autoStart.value = properties.getProperty(ConfigKeys.AUTO_START.keyName, "false").toBoolean()
        _enableGroups.value = properties.getProperty(ConfigKeys.ENABLE_GROUPS.keyName, "false").toBoolean()
        _wifiOnly.value = properties.getProperty(ConfigKeys.WIFI_ONLY.keyName, "false").toBoolean()
        _rssEnabled.value = properties.getProperty(ConfigKeys.RSS_ENABLED.keyName, "false").toBoolean()
        _autoDownloadGofile.value = properties.getProperty(ConfigKeys.AUTO_DOWNLOAD_GOFILE.keyName, "false").toBoolean()
        _debugLogs.value = properties.getProperty(ConfigKeys.DEBUG_LOGS.keyName, "false").toBoolean()

        // Load Long/Int props
        _autoSyncInterval.value = properties.getProperty(ConfigKeys.AUTO_SYNC_INTERVAL.keyName, "60").toLongOrNull() ?: 60L
        _rssCheckIntervalHours.value = properties.getProperty(ConfigKeys.RSS_CHECK_INTERVAL_HOURS.keyName, "1").toIntOrNull() ?: 1
        _rssLastCheckTime.value = properties.getProperty(ConfigKeys.RSS_LAST_CHECK_TIME.keyName, "0").toLongOrNull() ?: 0L
        
        _parallelDownloads.value = properties.getProperty(ConfigKeys.PARALLEL_DOWNLOADS.keyName, "1").toIntOrNull() ?: 1
        _batchProcessing.value = properties.getProperty(ConfigKeys.BATCH_PROCESSING.keyName, "false").toBoolean()
        _reprocessMode.value = properties.getProperty(ConfigKeys.REPROCESS_MODE.keyName, "false").toBoolean()
    }
    
    private fun saveConfig() {
        properties.setProperty(ConfigKeys.HOST.keyName, _host.value)
        properties.setProperty(ConfigKeys.USER.keyName, _user.value)
        properties.setProperty(ConfigKeys.PASSWORD.keyName, _password.value)
        properties.setProperty(ConfigKeys.REMOTE_PATH.keyName, _remotePath.value)
        properties.setProperty(ConfigKeys.LOCAL_PATH.keyName, _localPath.value)
        properties.setProperty(ConfigKeys.LOCAL_SOURCE_PATH.keyName, _localSourcePath.value)
        
        properties.setProperty(ConfigKeys.SMB_HOST.keyName, _smbHost.value)
        properties.setProperty(ConfigKeys.SMB_USER.keyName, _smbUser.value)
        properties.setProperty(ConfigKeys.SMB_PASSWORD.keyName, _smbPassword.value)
        properties.setProperty(ConfigKeys.SMB_SHARE_PATH.keyName, _smbSharePath.value)

        properties.setProperty(ConfigKeys.RSS_GROUP.keyName, _rssGroup.value)
        properties.setProperty(ConfigKeys.RSS_QUALITY.keyName, _rssQuality.value)

        properties.setProperty(ConfigKeys.LOCAL_ONLY.keyName, _localOnly.value.toString())
        properties.setProperty(ConfigKeys.PREFIX_SERIESNAME.keyName, _prefixSeriesName.value.toString())
        properties.setProperty(ConfigKeys.APPEND_QUALITY.keyName, _appendQuality.value.toString())
        properties.setProperty(ConfigKeys.AUTO_START.keyName, _autoStart.value.toString())
        properties.setProperty(ConfigKeys.ENABLE_GROUPS.keyName, _enableGroups.value.toString())
        properties.setProperty(ConfigKeys.WIFI_ONLY.keyName, _wifiOnly.value.toString())
        properties.setProperty(ConfigKeys.RSS_ENABLED.keyName, _rssEnabled.value.toString())
        properties.setProperty(ConfigKeys.AUTO_DOWNLOAD_GOFILE.keyName, _autoDownloadGofile.value.toString())
        properties.setProperty(ConfigKeys.DEBUG_LOGS.keyName, _debugLogs.value.toString())
        
        properties.setProperty(ConfigKeys.RSS_CHECK_INTERVAL_HOURS.keyName, _rssCheckIntervalHours.value.toString())
        properties.setProperty(ConfigKeys.AUTO_SYNC_INTERVAL.keyName, _autoSyncInterval.value.toString())
        properties.setProperty(ConfigKeys.RSS_LAST_CHECK_TIME.keyName, _rssLastCheckTime.value.toString())
        
        properties.setProperty(ConfigKeys.PARALLEL_DOWNLOADS.keyName, _parallelDownloads.value.toString())
        properties.setProperty(ConfigKeys.BATCH_PROCESSING.keyName, _batchProcessing.value.toString())
        properties.setProperty(ConfigKeys.REPROCESS_MODE.keyName, _reprocessMode.value.toString())

        configFile.outputStream().use { properties.store(it, "Megumi Downloader Config") }
    }

    override suspend fun <T> updateConfig(key: ConfigKey<T>, value: T) {
        when (key) {
            ConfigKeys.HOST -> _host.value = value as String
            ConfigKeys.USER -> _user.value = value as String
            ConfigKeys.PASSWORD -> _password.value = value as String
            ConfigKeys.REMOTE_PATH -> _remotePath.value = value as String
            ConfigKeys.LOCAL_PATH -> _localPath.value = value as String
            ConfigKeys.LOCAL_SOURCE_PATH -> _localSourcePath.value = value as String
            ConfigKeys.SMB_HOST -> _smbHost.value = value as String
            ConfigKeys.SMB_USER -> _smbUser.value = value as String
            ConfigKeys.SMB_PASSWORD -> _smbPassword.value = value as String
            ConfigKeys.SMB_SHARE_PATH -> _smbSharePath.value = value as String
            ConfigKeys.RSS_GROUP -> _rssGroup.value = value as String
            ConfigKeys.RSS_QUALITY -> _rssQuality.value = value as String
            
            ConfigKeys.LOCAL_ONLY -> _localOnly.value = value as Boolean
            ConfigKeys.PREFIX_SERIESNAME -> _prefixSeriesName.value = value as Boolean
            ConfigKeys.APPEND_QUALITY -> _appendQuality.value = value as Boolean
            ConfigKeys.AUTO_START -> _autoStart.value = value as Boolean
            ConfigKeys.ENABLE_GROUPS -> _enableGroups.value = value as Boolean
            ConfigKeys.WIFI_ONLY -> _wifiOnly.value = value as Boolean
            ConfigKeys.RSS_ENABLED -> _rssEnabled.value = value as Boolean
            ConfigKeys.AUTO_DOWNLOAD_GOFILE -> _autoDownloadGofile.value = value as Boolean
            ConfigKeys.DEBUG_LOGS -> _debugLogs.value = value as Boolean
            
            ConfigKeys.RSS_CHECK_INTERVAL_HOURS -> _rssCheckIntervalHours.value = value as Int
            ConfigKeys.AUTO_SYNC_INTERVAL -> _autoSyncInterval.value = value as Long
            ConfigKeys.RSS_LAST_CHECK_TIME -> _rssLastCheckTime.value = value as Long
            
            ConfigKeys.PARALLEL_DOWNLOADS -> _parallelDownloads.value = value as Int
            ConfigKeys.BATCH_PROCESSING -> _batchProcessing.value = value as Boolean
            ConfigKeys.REPROCESS_MODE -> _reprocessMode.value = value as Boolean
            else -> {}
        }
        saveConfig()
    }
    
    override suspend fun getDownloadConfig(): DownloadConfig {
        return DownloadConfig(
            host = _host.value,
            user = _user.value,
            pass = _password.value,
            remotePath = _remotePath.value,
            localBasePath = _localPath.value,
            localOnly = _localOnly.value,
            localSourcePath = _localSourcePath.value,
            prefixSeriesName = _prefixSeriesName.value,
            appendQuality = _appendQuality.value
        )
    }

    override suspend fun getSmbConfig(): SmbConfig {
        return SmbConfig(
            host = _smbHost.value,
            user = _smbUser.value,
            pass = _smbPassword.value,
            sharePath = _smbSharePath.value
        )
    }
}
