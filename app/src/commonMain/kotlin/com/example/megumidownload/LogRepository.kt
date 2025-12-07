package com.example.megumidownload

import com.example.megumidownload.viewmodel.LogEntry
import com.example.megumidownload.viewmodel.LogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton repository to hold logs so they can be accessed from both UI and Background Workers.
 */
object LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun addLog(message: String, type: LogType = LogType.INFO) {
        val timestamp = PlatformUtils.getCurrentTimestamp()
        val entry = LogEntry(timestamp, message, type)
        // Keep only last 200 logs to avoid memory issues
        val currentList = _logs.value
        val newList = if (currentList.size > 200) {
            currentList.drop(1) + entry
        } else {
            currentList + entry
        }
        _logs.value = newList
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
