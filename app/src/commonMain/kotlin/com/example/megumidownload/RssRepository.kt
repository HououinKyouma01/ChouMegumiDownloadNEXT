package com.example.megumidownload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RssRepository {
    
    // Using simple JVM networking for now.
    private val BASE_URL = "https://feed.animetosho.org/rss2"

    suspend fun fetchFeed(group: String, seriesName: String, quality: String): Result<List<RssItem>> = withContext(Dispatchers.IO) {
        try {
            // Query construction: "Group Series Quality"
            // If group is empty, simpler query.
            val queryParts = mutableListOf<String>()
            if (group.isNotBlank()) queryParts.add(group)
            if (seriesName.isNotBlank()) queryParts.add(seriesName)
            if (quality.isNotBlank()) queryParts.add(quality)
            
            val query = queryParts.joinToString(" ")
            if (query.isBlank()) return@withContext Result.failure(Exception("Empty query parameters"))
            
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "$BASE_URL?q=$encodedQuery"
            
            val url = URL(urlString)
            Logger.d("RssRepo", "DEBUG: Fetching URL: $urlString")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            Logger.d("RssRepo", "DEBUG: Response Code: ${connection.responseCode}")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val xmlData = connection.inputStream.bufferedReader().readText()
                Logger.d("RssRepo", "DEBUG: XML Data Length: ${xmlData.length}")
                val items = RssParser.parse(xmlData)
                Logger.d("RssRepo", "DEBUG: Parsed Items: ${items.size}")
                Result.success(items)
            } else {
                Logger.e("RssRepo", "DEBUG: HTTP Error: ${connection.responseCode}")
                Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
             Logger.e("RssRepo", "DEBUG: Exception: ${e.message}")
             e.printStackTrace()
            Result.failure(e)
        }
    }
}
