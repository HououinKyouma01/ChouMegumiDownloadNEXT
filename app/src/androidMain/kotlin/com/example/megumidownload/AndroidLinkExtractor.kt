package com.example.megumidownload

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class AndroidLinkExtractor : LinkExtractor {
    @Composable
    override fun Extract(url: String, onResult: (ExtractionResult?) -> Unit) {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {}
                }.also { webViewRef = it }
            },
            update = { webView ->
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            },
            modifier = Modifier.size(0.dp).alpha(0f)
        )

        LaunchedEffect(url) {
            var attempts = 0
            val maxAttempts = 30 
            var found = false
            
            while (attempts < maxAttempts && !found) {
                kotlinx.coroutines.delay(1000)
                attempts++
                
                val js = """
                    (function() {
                        try {
                            var resultLink = null;
                            if (typeof appdata !== 'undefined' && appdata.fileManager && appdata.fileManager.mainContent && appdata.fileManager.mainContent.data && appdata.fileManager.mainContent.data.children) {
                                var children = appdata.fileManager.mainContent.data.children;
                                var keys = Object.keys(children);
                                for (var i = 0; i < keys.length; i++) {
                                    var item = children[keys[i]];
                                    if (item.type === 'file' && item.link && item.link.match(/\.(mkv|mp4|webm|avi)/i)) {
                                        resultLink = item.link;
                                        break;
                                    }
                                }
                            }
                            if (!resultLink) {
                                 var links = document.getElementsByTagName('a');
                                 for(var i=0; i<links.length; i++) {
                                     if(links[i].href.match(/\.(mkv|mp4|webm|avi)/i)) {
                                         resultLink = links[i].href;
                                         break;
                                     }
                                 }
                            }
                            if (resultLink) {
                                return JSON.stringify({ url: resultLink, cookie: document.cookie, userAgent: navigator.userAgent });
                            }
                        } catch(e) { return null; }
                        return null;
                    })();
                """.trimIndent()
                
                webViewRef?.evaluateJavascript(js) { result ->
                    if (!found && result != null && result != "null" && result != "\"null\"") {
                        try {
                            val cleanJson = result.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
                            val jsonObject = JSONObject(cleanJson)
                            val link = jsonObject.optString("url")
                            val cookie = jsonObject.optString("cookie")
                            val ua = jsonObject.optString("userAgent")
                            if (link.isNotEmpty()) {
                                found = true
                                onResult(ExtractionResult(link, cookie, ua))
                            }
                        } catch (e: Exception) { }
                    }
                }
                
                if (found) break
            }
            if (!found && attempts >= maxAttempts) {
                onResult(null)
            }
        }
    }
}
