package com.example.megumidownload.viewmodel

import com.example.megumidownload.LogRepository
import kotlinx.coroutines.flow.StateFlow

class LogViewModel {
    val logs: StateFlow<List<LogEntry>> = LogRepository.logs

    fun addLog(message: String, type: LogType = LogType.INFO) {
        LogRepository.addLog(message, type)
    }
    
    fun clearLogs() {
        LogRepository.clearLogs()
    }
}
