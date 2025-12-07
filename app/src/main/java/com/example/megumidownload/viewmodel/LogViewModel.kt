package com.example.megumidownload.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.example.megumidownload.LogRepository

data class LogEntry(
    val timestamp: String,
    val message: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO, ERROR, SUCCESS, WARNING
}

class LogViewModel : ViewModel() {
    val logs: StateFlow<List<LogEntry>> = LogRepository.logs

    fun addLog(message: String, type: LogType = LogType.INFO) {
        LogRepository.addLog(message, type)
    }
    
    fun clearLogs() {
        LogRepository.clearLogs()
    }
}
