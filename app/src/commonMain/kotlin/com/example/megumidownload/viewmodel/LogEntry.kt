package com.example.megumidownload.viewmodel

data class LogEntry(
    val timestamp: String,
    val message: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO, ERROR, SUCCESS, WARNING
}
