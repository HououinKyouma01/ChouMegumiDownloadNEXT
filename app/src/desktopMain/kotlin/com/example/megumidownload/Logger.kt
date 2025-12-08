package com.example.megumidownload

actual object Logger {
    var debugEnabled: Boolean = false
    private val logFile = java.io.File(System.getProperty("user.home"), ".megumidownload/debug.log")

    init {
        // Create/rotate log file on startup? For now just append or fresh start.
        if (!logFile.parentFile.exists()) logFile.parentFile.mkdirs()
        log("SESSION START")
    }
    
    private fun log(msg: String) {
        try {
            val timestamp = java.time.LocalDateTime.now().toString()
            logFile.appendText("[$timestamp] $msg\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun d(tag: String, message: String) {
        if (debugEnabled) {
            println("DEBUG [$tag]: $message")
            log("DEBUG [$tag]: $message")
            LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.INFO)
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val msg = "ERROR [$tag]: $message"
        System.err.println(msg)
        log(msg)
        if (throwable != null) {
             throwable.printStackTrace()
             log(throwable.stackTraceToString())
        }
        
        LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.ERROR)
        if (throwable != null) {
            LogRepository.addLog("Ex: ${throwable.toString()}", com.example.megumidownload.viewmodel.LogType.ERROR)
            throwable.stackTrace.take(8).forEach { element ->
                LogRepository.addLog("  at $element", com.example.megumidownload.viewmodel.LogType.ERROR)
            }
        }
    }

    actual fun w(tag: String, message: String) {
        println("WARN [$tag]: $message")
        log("WARN [$tag]: $message")
        LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.WARNING)
    }

    actual fun i(tag: String, message: String) {
        println("INFO [$tag]: $message")
        log("INFO [$tag]: $message")
        LogRepository.addLog("[$tag] $message", com.example.megumidownload.viewmodel.LogType.INFO)
    }
}
