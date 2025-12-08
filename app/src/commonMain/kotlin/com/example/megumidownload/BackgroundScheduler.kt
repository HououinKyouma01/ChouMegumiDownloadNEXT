package com.example.megumidownload

interface BackgroundScheduler {
    fun scheduleDownload(force: Boolean = false)
    fun scheduleRssCheck(intervalMinutes: Long)
    fun scheduleUrlDownload(url: String, title: String, series: SeriesEntry, config: DownloadConfig, cookie: String? = null, userAgent: String? = null)
}
