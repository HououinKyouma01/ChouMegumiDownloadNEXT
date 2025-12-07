package com.example.megumidownload

interface BackgroundScheduler {
    fun scheduleDownload()
    fun scheduleRssCheck(intervalMinutes: Long)
}
