package com.example.megumidownload

import kotlinx.coroutines.flow.Flow

data class DownloadConfig(
    val host: String,
    val user: String,
    val pass: String,
    val remotePath: String,
    val localBasePath: String,
    val localOnly: Boolean,
    val localSourcePath: String,
    val prefixSeriesName: Boolean,
    val appendQuality: Boolean
)

data class SmbConfig(
    val host: String,
    val user: String,
    val pass: String,
    val sharePath: String
)

data class RssConfig(
    val enabled: Boolean,
    val group: String,
    val quality: String
)

sealed class ConfigKey<T>(val keyName: String) {
    class StringKey(name: String) : ConfigKey<String>(name)
    class BooleanKey(name: String) : ConfigKey<Boolean>(name)
    class LongKey(name: String) : ConfigKey<Long>(name)
    class IntKey(name: String) : ConfigKey<Int>(name)
}

object ConfigKeys {
    val HOST = ConfigKey.StringKey("host")
    val USER = ConfigKey.StringKey("user")
    val PASSWORD = ConfigKey.StringKey("password")
    val REMOTE_PATH = ConfigKey.StringKey("remote_path")
    val LOCAL_PATH = ConfigKey.StringKey("local_path") // Root folder for series
    val LOCAL_ONLY = ConfigKey.BooleanKey("local_only")
    val LOCAL_SOURCE_PATH = ConfigKey.StringKey("local_source_path")
    val PREFIX_SERIESNAME = ConfigKey.BooleanKey("prefix_seriesname")
    val APPEND_QUALITY = ConfigKey.BooleanKey("append_quality")
    val AUTO_START = ConfigKey.BooleanKey("auto_start")
    val SMB_HOST = ConfigKey.StringKey("smb_host")
    val SMB_USER = ConfigKey.StringKey("smb_user")
    val SMB_PASSWORD = ConfigKey.StringKey("smb_password")
    val SMB_SHARE_PATH = ConfigKey.StringKey("smb_share_path")
    val ENABLE_GROUPS = ConfigKey.BooleanKey("enable_groups")
    val AUTO_SYNC_INTERVAL = ConfigKey.LongKey("auto_sync_interval")
    val WIFI_ONLY = ConfigKey.BooleanKey("wifi_only")
    val RSS_ENABLED = ConfigKey.BooleanKey("rss_enabled")
    val RSS_GROUP = ConfigKey.StringKey("rss_group")
    val RSS_QUALITY = ConfigKey.StringKey("rss_quality")
    val AUTO_DOWNLOAD_GOFILE = ConfigKey.BooleanKey("auto_download_gofile")
    val RSS_CHECK_INTERVAL_HOURS = ConfigKey.IntKey("rss_check_interval_hours")
    val RSS_LAST_CHECK_TIME = ConfigKey.LongKey("rss_last_check_time")
    val DEBUG_LOGS = ConfigKey.BooleanKey("debug_logs")
    val PARALLEL_DOWNLOADS = ConfigKey.IntKey("parallel_downloads")
    val BATCH_PROCESSING = ConfigKey.BooleanKey("batch_processing")
    val REPROCESS_MODE = ConfigKey.BooleanKey("reprocess_mode")
    val SUBTITLE_LANGUAGE = ConfigKey.StringKey("subtitle_language")
}

interface ConfigManager {
    val host: Flow<String>
    val user: Flow<String>
    val password: Flow<String>
    val remotePath: Flow<String>
    val localPath: Flow<String>
    val localOnly: Flow<Boolean>
    val localSourcePath: Flow<String>
    val prefixSeriesName: Flow<Boolean>
    val appendQuality: Flow<Boolean>
    val autoStart: Flow<Boolean>
    
    val smbHost: Flow<String>
    val smbUser: Flow<String>
    val smbPassword: Flow<String>
    val smbSharePath: Flow<String>
    val enableGroups: Flow<Boolean>
    
    val autoSyncInterval: Flow<Long>
    val wifiOnly: Flow<Boolean>
    
    val rssEnabled: Flow<Boolean>
    val rssGroup: Flow<String>
    val rssQuality: Flow<String>
    val autoDownloadGofile: Flow<Boolean>
    val rssCheckIntervalHours: Flow<Int>
    val rssLastCheckTime: Flow<Long>
    val debugLogs: Flow<Boolean>
    val parallelDownloads: Flow<Int>
    val batchProcessing: Flow<Boolean>
    val reprocessMode: Flow<Boolean>
    val subtitleLanguage: Flow<String>

    suspend fun <T> updateConfig(key: ConfigKey<T>, value: T)
    
    suspend fun getDownloadConfig(): DownloadConfig
    suspend fun getSmbConfig(): SmbConfig
}
