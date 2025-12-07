package com.example.megumidownload

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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

class ConfigManager(private val context: Context) {
    companion object {
        val HOST = stringPreferencesKey("host")
        val USER = stringPreferencesKey("user")
        val PASSWORD = stringPreferencesKey("password")
        val REMOTE_PATH = stringPreferencesKey("remote_path")
        val LOCAL_PATH = stringPreferencesKey("local_path") // Root folder for series
        val LOCAL_ONLY = booleanPreferencesKey("local_only")
        val LOCAL_SOURCE_PATH = stringPreferencesKey("local_source_path")
        val PREFIX_SERIESNAME = booleanPreferencesKey("prefix_seriesname")
        val APPEND_QUALITY = booleanPreferencesKey("append_quality")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val SMB_HOST = stringPreferencesKey("smb_host")
        val SMB_USER = stringPreferencesKey("smb_user")
        val SMB_PASSWORD = stringPreferencesKey("smb_password")
        val SMB_SHARE_PATH = stringPreferencesKey("smb_share_path")
        val ENABLE_GROUPS = booleanPreferencesKey("enable_groups")
        val AUTO_SYNC_INTERVAL = androidx.datastore.preferences.core.longPreferencesKey("auto_sync_interval")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val RSS_ENABLED = booleanPreferencesKey("rss_enabled")
        val RSS_GROUP = stringPreferencesKey("rss_group")
        val RSS_QUALITY = stringPreferencesKey("rss_quality")
        val AUTO_DOWNLOAD_GOFILE = booleanPreferencesKey("auto_download_gofile")
        val RSS_CHECK_INTERVAL_HOURS = androidx.datastore.preferences.core.intPreferencesKey("rss_check_interval_hours")
        val RSS_LAST_CHECK_TIME = androidx.datastore.preferences.core.longPreferencesKey("rss_last_check_time")
    }

    val host: Flow<String> = context.dataStore.data.map { it[HOST] ?: "" }
    val user: Flow<String> = context.dataStore.data.map { it[USER] ?: "" }
    val password: Flow<String> = context.dataStore.data.map { it[PASSWORD] ?: "" }
    val remotePath: Flow<String> = context.dataStore.data.map { it[REMOTE_PATH] ?: "" }
    val localPath: Flow<String> = context.dataStore.data.map { it[LOCAL_PATH] ?: "" }
    val localOnly: Flow<Boolean> = context.dataStore.data.map { it[LOCAL_ONLY] ?: false }
    val localSourcePath: Flow<String> = context.dataStore.data.map { it[LOCAL_SOURCE_PATH] ?: "" }
    val prefixSeriesName: Flow<Boolean> = context.dataStore.data.map { it[PREFIX_SERIESNAME] ?: false }
    val appendQuality: Flow<Boolean> = context.dataStore.data.map { it[APPEND_QUALITY] ?: true }
    val autoStart: Flow<Boolean> = context.dataStore.data.map { it[AUTO_START] ?: false }
    
    val smbHost: Flow<String> = context.dataStore.data.map { it[SMB_HOST] ?: "" }
    val smbUser: Flow<String> = context.dataStore.data.map { it[SMB_USER] ?: "" }
    val smbPassword: Flow<String> = context.dataStore.data.map { it[SMB_PASSWORD] ?: "" }
    val smbSharePath: Flow<String> = context.dataStore.data.map { it[SMB_SHARE_PATH] ?: "" }
    val enableGroups: Flow<Boolean> = context.dataStore.data.map { it[ENABLE_GROUPS] ?: false }
    
    val autoSyncInterval: Flow<Long> = context.dataStore.data.map { it[AUTO_SYNC_INTERVAL] ?: 0L } // 0 = Disabled
    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[WIFI_ONLY] ?: true }
    
    val rssEnabled: Flow<Boolean> = context.dataStore.data.map { it[RSS_ENABLED] ?: false }
    val rssGroup: Flow<String> = context.dataStore.data.map { it[RSS_GROUP] ?: "" }
    val rssQuality: Flow<String> = context.dataStore.data.map { it[RSS_QUALITY] ?: "1080p" }
    val autoDownloadGofile: Flow<Boolean> = context.dataStore.data.map { it[AUTO_DOWNLOAD_GOFILE] ?: false }
    val rssCheckIntervalHours: Flow<Int> = context.dataStore.data.map { it[RSS_CHECK_INTERVAL_HOURS] ?: 1 } // Default 1 hour
    val rssLastCheckTime: Flow<Long> = context.dataStore.data.map { it[RSS_LAST_CHECK_TIME] ?: 0L }

    suspend fun <T> updateConfig(key: Preferences.Key<T>, value: T) {
        android.util.Log.d("ConfigManager", "Updating config: ${key.name} = $value")
        context.dataStore.edit { settings ->
            settings[key] = value
        }
        android.util.Log.d("ConfigManager", "Update complete for: ${key.name}")
    }

    suspend fun getDownloadConfig(): DownloadConfig {
        val prefs = try {
            context.dataStore.data.first()
        } catch (e: Exception) {
            android.util.Log.e("ConfigManager", "Error reading DataStore", e)
            androidx.datastore.preferences.core.emptyPreferences()
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

    suspend fun getSmbConfig(): SmbConfig {
        val prefs = try {
            context.dataStore.data.first()
        } catch (e: Exception) {
            android.util.Log.e("ConfigManager", "Error reading DataStore", e)
            androidx.datastore.preferences.core.emptyPreferences()
        }
        return SmbConfig(
            host = prefs[SMB_HOST] ?: "",
            user = prefs[SMB_USER] ?: "",
            pass = prefs[SMB_PASSWORD] ?: "",
            sharePath = prefs[SMB_SHARE_PATH] ?: ""
        )
    }
}
