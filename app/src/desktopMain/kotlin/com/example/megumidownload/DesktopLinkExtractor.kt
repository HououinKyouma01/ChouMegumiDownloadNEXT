package com.example.megumidownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

class DesktopLinkExtractor : LinkExtractor {
    @Composable
    override fun Extract(url: String, onResult: (ExtractionResult?) -> Unit) {
        // Desktop implementation extraction not yet supported (requires Headless Browser or JCEF)
        // Helper to run extraction in background
        LaunchedEffect(url) {
            val directLink = if (url.contains("gofile.io/d/")) {
                extractGoFile(url)
            } else {
                url // Assume direct if no extractor matches
                // Or signal null if strict?
                // Logic: If we can't extract, maybe it IS a direct link.
                // But if it's gofile/d/, it's definitely NOT direct.
            }
            
            if (directLink != null) {
                val result = ExtractionResult(
                    url = directLink,
                    cookie = "accountToken=" + (lastToken ?: ""),
                    userAgent = "Mozilla/5.0"
                )
                onResult(result)
            } else {
                Logger.w("DesktopLinkExtractor", "Extraction failed or not supported for $url")
                onResult(null)
            }
        }
    }
    
    private var lastToken: String? = null
    
    // Simple JSON structs for Gson
    data class GAccountResp(val status: String, val data: GAccountData?)
    data class GAccountData(val token: String)
    
    data class GContentResp(val status: String, val data: GContentData?)
    data class GContentData(val contents: Map<String, GContentItem>?)
    data class GContentItem(val link: String, val name: String)

    private suspend fun extractGoFile(url: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Logger.i("DesktopLinkExtractor", "Attempting GoFile extraction for $url")
                
                // 1. Get Token (Always fresh)
                
                // 1. Get Token (Always fresh)
                
                val tokenUrl = "https://api.gofile.io/accounts/guest"
                val json = simpleGet(tokenUrl)
                val resp = com.google.gson.Gson().fromJson(json, GAccountResp::class.java)
                if (resp.status == "ok" && resp.data != null) {
                     lastToken = resp.data.token
                     Logger.d("DesktopLinkExtractor", "Got GoFile token: $lastToken")
                } else {
                    Logger.e("DesktopLinkExtractor", "Failed to get GoFile token: $json")
                    return@withContext null
                }
                
                // 2. Get Content ID
                val idMatch = Regex("gofile\\.io/d/([a-zA-Z0-9]+)").find(url)
                val contentId = idMatch?.groupValues?.get(1)
                if (contentId == null) {
                    Logger.e("DesktopLinkExtractor", "Could not parse Content ID from $url")
                    return@withContext null
                }
                
                // 3. Get Content
                val contentUrl = "https://api.gofile.io/contents/$contentId?wt=4fd6sg89d7s6" // Common website token
                val contentJson = simpleGet(contentUrl, lastToken)
                
                val contentResp = com.google.gson.Gson().fromJson(contentJson, GContentResp::class.java)
                if (contentResp.status == "ok" && contentResp.data != null && contentResp.data.contents != null) {
                    // Start finding files
                    // contents is a Map<String, Item>
                    val items = contentResp.data.contents.values
                    val target = items.find { it.name.endsWith(".mkv", ignoreCase = true) || it.name.endsWith(".mp4", ignoreCase = true) } 
                               ?: items.firstOrNull()
                    
                    if (target != null) {
                        Logger.i("DesktopLinkExtractor", "Found direct link: ${target.link}")
                        return@withContext target.link
                    }
                }
                
                Logger.w("DesktopLinkExtractor", "No suitable file found in GoFile folder.")
                return@withContext null
                
            } catch (e: Exception) {
                Logger.e("DesktopLinkExtractor", "GoFile extraction error: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        }
    }
    
    private fun simpleGet(url: String, token: String? = null): String {
        val u = java.net.URL(url)
        val conn = u.openConnection() as java.net.HttpURLConnection
        Logger.d("DesktopLinkExtractor", "GET $url")
        if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
        // Mimic Browser
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        conn.setRequestProperty("Origin", "https://gofile.io") 
        conn.setRequestProperty("Referer", "https://gofile.io/")
        
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        
        val code = conn.responseCode
        if (code >= 400) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            Logger.e("DesktopLinkExtractor", "GoFile Error $code: $err")
            throw java.io.IOException("HTTP $code: $err")
        }
        
        return conn.inputStream.bufferedReader().readText()
    }
}
