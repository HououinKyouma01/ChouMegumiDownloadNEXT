package com.example.megumidownload

import android.util.Log

actual object Logger {
    private const val TAG_PREFIX = "MegumiDownload"
    var debugEnabled: Boolean = false

    actual fun d(tag: String, message: String) {
        if (debugEnabled) {
            Log.d(tag, message)
            LogRepository.addLog("DEBUG: [$tag] $message", com.example.megumidownload.viewmodel.LogType.INFO)
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        if (throwable != null) {
            Log.e("$TAG_PREFIX:$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX:$tag", message)
        }
        LogRepository.addLog("ERROR: [$TAG_PREFIX:$tag] $fullMsg", com.example.megumidownload.viewmodel.LogType.ERROR)
    }

    actual fun w(tag: String, message: String) {
        Log.w("$TAG_PREFIX:$tag", message)
        LogRepository.addLog("WARN: [$TAG_PREFIX:$tag] $message", com.example.megumidownload.viewmodel.LogType.WARNING)
    }

    actual fun i(tag: String, message: String) {
        // Always show INFO logs in the UI Repository
        LogRepository.addLog("INFO: [$TAG_PREFIX:$tag] $message", com.example.megumidownload.viewmodel.LogType.INFO)
        
        // System log can remain conditional or always on for INFO. 
        // Let's keep system log clean if debug disabled, but UI log MUST show it.
        if (debugEnabled) {
            Log.i("$TAG_PREFIX:$tag", message)
        }
    }
}
