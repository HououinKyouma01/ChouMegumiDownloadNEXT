package com.example.megumidownload

actual object Logger {
    var debugEnabled: Boolean = false

    actual fun d(tag: String, message: String) {
        if (debugEnabled) println("DEBUG [$tag]: $message")
        // Optionally omit debug logs from UI unless verbose? User enabled debug logs in settings, 
        // but Logger.debugEnabled is static. 
        // Let's assume ConfigManager updates Logger.debugEnabled? 
        // Use LogType.INFO for d() if debug is enabled, or LogType.DEBUG if we had it.
        // LogEntry has LogType.INFO, ERROR, WARNING, SUCCESS.
        if (debugEnabled) LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.INFO)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.ERROR)
        if (throwable != null) {
            LogRepository.addLog("Ex: ${throwable.toString()}", com.example.megumidownload.viewmodel.LogType.ERROR)
            // Log first 10 lines of stack trace to UI to debug the NPE
            throwable.stackTrace.take(8).forEach { element ->
                LogRepository.addLog("  at $element", com.example.megumidownload.viewmodel.LogType.ERROR)
            }
        }
    }

    actual fun w(tag: String, message: String) {
        System.err.println("WARN [$tag]: $message")
        LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.WARNING)
    }

    actual fun i(tag: String, message: String) {
        println("INFO [$tag]: $message")
        LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.INFO)
    }
}
