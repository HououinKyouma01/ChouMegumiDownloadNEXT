package com.example.megumidownload

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// Allow access to dataStore extension from this file
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AndroidConfigManager(private val context: Context) : ConfigManager {
    companion object {
        private val HOST = stringPreferencesKey("host")
        private val USER = stringPreferencesKey("user")
        private val PASSWORD = stringPreferencesKey("password")
        private val REMOTE_PATH = stringPreferencesKey("remote_path")
        private val LOCAL_PATH = stringPreferencesKey("local_path")
        private val LOCAL_ONLY = booleanPreferencesKey("local_only")
        private val LOCAL_SOURCE_PATH = stringPreferencesKey("local_source_path")
        private val PREFIX_SERIESNAME = booleanPreferencesKey("prefix_seriesname")
        private val APPEND_QUALITY = booleanPreferencesKey("append_quality")
        private val AUTO_START = booleanPreferencesKey("auto_start")
        private val SMB_HOST = stringPreferencesKey("smb_host")
        private val SMB_USER = stringPreferencesKey("smb_user")
        private val SMB_PASSWORD = stringPreferencesKey("smb_password")
        private val SMB_SHARE_PATH = stringPreferencesKey("smb_share_path")
        private val ENABLE_GROUPS = booleanPreferencesKey("enable_groups")
        private val AUTO_SYNC_INTERVAL = longPreferencesKey("auto_sync_interval")
        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val RSS_ENABLED = booleanPreferencesKey("rss_enabled")
        private val RSS_GROUP = stringPreferencesKey("rss_group")
        private val RSS_QUALITY = stringPreferencesKey("rss_quality")
        private val AUTO_DOWNLOAD_GOFILE = booleanPreferencesKey("auto_download_gofile")
        private val RSS_CHECK_INTERVAL_HOURS = intPreferencesKey("rss_check_interval_hours")
        private val RSS_LAST_CHECK_TIME = longPreferencesKey("rss_last_check_time")
        private val DEBUG_LOGS = booleanPreferencesKey("debug_logs")
        private val PARALLEL_DOWNLOADS = intPreferencesKey("parallel_downloads")
        private val BATCH_PROCESSING = booleanPreferencesKey("batch_processing")
        private val REPROCESS_MODE = booleanPreferencesKey("reprocess_mode")
        private val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
    }

    override val host: Flow<String> = context.dataStore.data.map { it[HOST] ?: "" }
    override val user: Flow<String> = context.dataStore.data.map { it[USER] ?: "" }
    override val password: Flow<String> = context.dataStore.data.map { it[PASSWORD] ?: "" }
    override val remotePath: Flow<String> = context.dataStore.data.map { it[REMOTE_PATH] ?: "" }
    override val localPath: Flow<String> = context.dataStore.data.map { it[LOCAL_PATH] ?: "" }
    override val localOnly: Flow<Boolean> = context.dataStore.data.map { it[LOCAL_ONLY] ?: false }
    override val localSourcePath: Flow<String> = context.dataStore.data.map { it[LOCAL_SOURCE_PATH] ?: "" }
    override val prefixSeriesName: Flow<Boolean> = context.dataStore.data.map { it[PREFIX_SERIESNAME] ?: false }
    override val appendQuality: Flow<Boolean> = context.dataStore.data.map { it[APPEND_QUALITY] ?: true }
    override val autoStart: Flow<Boolean> = context.dataStore.data.map { it[AUTO_START] ?: false }
    
    override val smbHost: Flow<String> = context.dataStore.data.map { it[SMB_HOST] ?: "" }
    override val smbUser: Flow<String> = context.dataStore.data.map { it[SMB_USER] ?: "" }
    override val smbPassword: Flow<String> = context.dataStore.data.map { it[SMB_PASSWORD] ?: "" }
    override val smbSharePath: Flow<String> = context.dataStore.data.map { it[SMB_SHARE_PATH] ?: "" }
    override val enableGroups: Flow<Boolean> = context.dataStore.data.map { it[ENABLE_GROUPS] ?: false }
    
    override val autoSyncInterval: Flow<Long> = context.dataStore.data.map { it[AUTO_SYNC_INTERVAL] ?: 0L }
    override val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[WIFI_ONLY] ?: true }
    
    override val rssEnabled: Flow<Boolean> = context.dataStore.data.map { it[RSS_ENABLED] ?: false }
    override val rssGroup: Flow<String> = context.dataStore.data.map { it[RSS_GROUP] ?: "" }
    override val rssQuality: Flow<String> = context.dataStore.data.map { it[RSS_QUALITY] ?: "1080p" }
    override val autoDownloadGofile: Flow<Boolean> = context.dataStore.data.map { it[AUTO_DOWNLOAD_GOFILE] ?: false }
    override val rssCheckIntervalHours: Flow<Int> = context.dataStore.data.map { it[RSS_CHECK_INTERVAL_HOURS] ?: 1 }
    override val rssLastCheckTime: Flow<Long> = context.dataStore.data.map { it[RSS_LAST_CHECK_TIME] ?: 0L }
    override val debugLogs: Flow<Boolean> = context.dataStore.data.map { it[DEBUG_LOGS] ?: false }
    override val parallelDownloads: Flow<Int> = context.dataStore.data.map { it[PARALLEL_DOWNLOADS] ?: 1 }
    override val batchProcessing: Flow<Boolean> = context.dataStore.data.map { it[BATCH_PROCESSING] ?: false }
    override val reprocessMode: Flow<Boolean> = context.dataStore.data.map { it[REPROCESS_MODE] ?: false }
    override val subtitleLanguage: Flow<String> = context.dataStore.data.map { it[SUBTITLE_LANGUAGE] ?: "eng" }

    init {
       // Watch for debug flag changes and update Logger
       kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
           debugLogs.collect { enabled ->
               Logger.debugEnabled = enabled
               Log.d("AndroidConfigManager", "Debug logs enabled: $enabled")
           }
       }
    }

    override suspend fun <T> updateConfig(key: ConfigKey<T>, value: T) {
        Log.d("AndroidConfigManager", "Updating config: ${key.keyName} = $value")
        val prefKey = getPreferenceKey(key)
        context.dataStore.edit { settings ->
            settings[prefKey] = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPreferenceKey(key: ConfigKey<T>): Preferences.Key<T> {
        return when(key) {
            ConfigKeys.HOST -> HOST as Preferences.Key<T>
            ConfigKeys.USER -> USER as Preferences.Key<T>
            ConfigKeys.PASSWORD -> PASSWORD as Preferences.Key<T>
            ConfigKeys.REMOTE_PATH -> REMOTE_PATH as Preferences.Key<T>
            ConfigKeys.LOCAL_PATH -> LOCAL_PATH as Preferences.Key<T>
            ConfigKeys.LOCAL_ONLY -> LOCAL_ONLY as Preferences.Key<T>
            ConfigKeys.LOCAL_SOURCE_PATH -> LOCAL_SOURCE_PATH as Preferences.Key<T>
            ConfigKeys.PREFIX_SERIESNAME -> PREFIX_SERIESNAME as Preferences.Key<T>
            ConfigKeys.APPEND_QUALITY -> APPEND_QUALITY as Preferences.Key<T>
            ConfigKeys.AUTO_START -> AUTO_START as Preferences.Key<T>
            ConfigKeys.SMB_HOST -> SMB_HOST as Preferences.Key<T>
            ConfigKeys.SMB_USER -> SMB_USER as Preferences.Key<T>
            ConfigKeys.SMB_PASSWORD -> SMB_PASSWORD as Preferences.Key<T>
            ConfigKeys.SMB_SHARE_PATH -> SMB_SHARE_PATH as Preferences.Key<T>
            ConfigKeys.ENABLE_GROUPS -> ENABLE_GROUPS as Preferences.Key<T>
            ConfigKeys.AUTO_SYNC_INTERVAL -> AUTO_SYNC_INTERVAL as Preferences.Key<T>
            ConfigKeys.WIFI_ONLY -> WIFI_ONLY as Preferences.Key<T>
            ConfigKeys.RSS_ENABLED -> RSS_ENABLED as Preferences.Key<T>
            ConfigKeys.RSS_GROUP -> RSS_GROUP as Preferences.Key<T>
            ConfigKeys.RSS_QUALITY -> RSS_QUALITY as Preferences.Key<T>
            ConfigKeys.AUTO_DOWNLOAD_GOFILE -> AUTO_DOWNLOAD_GOFILE as Preferences.Key<T>
            ConfigKeys.RSS_CHECK_INTERVAL_HOURS -> RSS_CHECK_INTERVAL_HOURS as Preferences.Key<T>
            ConfigKeys.RSS_LAST_CHECK_TIME -> RSS_LAST_CHECK_TIME as Preferences.Key<T>
            ConfigKeys.DEBUG_LOGS -> DEBUG_LOGS as Preferences.Key<T>
            ConfigKeys.PARALLEL_DOWNLOADS -> PARALLEL_DOWNLOADS as Preferences.Key<T>
            ConfigKeys.BATCH_PROCESSING -> BATCH_PROCESSING as Preferences.Key<T>
            ConfigKeys.REPROCESS_MODE -> REPROCESS_MODE as Preferences.Key<T>
            ConfigKeys.SUBTITLE_LANGUAGE -> SUBTITLE_LANGUAGE as Preferences.Key<T>
            else -> throw IllegalArgumentException("Unknown key: ${key.keyName}")
        }
    }

    override suspend fun getDownloadConfig(): DownloadConfig {
        val prefs = try {
            context.dataStore.data.first()
        } catch (e: Exception) {
            Log.e("ConfigManager", "Error reading DataStore", e)
            emptyPreferences()
        }
        return DownloadConfig(
            host = prefs[HOST] ?: "",
            user = prefs[USER] ?: "",
            pass = prefs[PASSWORD] ?: "",
            remotePath = prefs[REMOTE_PATH] ?: "",
            localBasePath = prefs[LOCAL_PATH] ?: "",
            localOnly = prefs[LOCAL_ONLY] ?: false,
            localSourcePath = prefs[LOCAL_SOURCE_PATH] ?: "",
            prefixSeriesName = prefs[PREFIX_SERIESNAME] ?: false,
            appendQuality = prefs[APPEND_QUALITY] ?: true
        )
    }

    override suspend fun getSmbConfig(): SmbConfig {
        val prefs = try {
            context.dataStore.data.first()
        } catch (e: Exception) {
            Log.e("ConfigManager", "Error reading DataStore", e)
            emptyPreferences()
        }
        return SmbConfig(
            host = prefs[SMB_HOST] ?: "",
            user = prefs[SMB_USER] ?: "",
            pass = prefs[SMB_PASSWORD] ?: "",
            sharePath = prefs[SMB_SHARE_PATH] ?: ""
        )
    }
}
