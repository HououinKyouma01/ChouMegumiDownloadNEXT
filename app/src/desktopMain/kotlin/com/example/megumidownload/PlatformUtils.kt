package com.example.megumidownload

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual object PlatformUtils {
    actual fun getCurrentTimestamp(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }
}
