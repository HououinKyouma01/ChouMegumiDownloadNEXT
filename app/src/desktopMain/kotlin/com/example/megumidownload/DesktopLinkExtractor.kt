package com.example.megumidownload

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.gson.JsonParser

class DesktopLinkExtractor : LinkExtractor {

    @Composable
    override fun Extract(url: String, onResult: (ExtractionResult?) -> Unit) {
        val engineState by DesktopWebEngine.state.collectAsState()
        val scope = rememberCoroutineScope()
        val navigator = rememberWebViewNavigator()
        
        var showDownloadDialog by remember { mutableStateOf(false) }

        // Check engine status on entry
        LaunchedEffect(Unit) {
            if (!DesktopWebEngine.isReady()) {
                showDownloadDialog = true
            }
        }

        if (showDownloadDialog) {
             if (engineState is DesktopWebEngine.State.NotInitialized || engineState is DesktopWebEngine.State.Error) {
                 AlertDialog(
                     onDismissRequest = { 
                         showDownloadDialog = false 
                         onResult(null) 
                     },
                     title = { Text("Enable GoFile Support?") },
                     text = { 
                         Column {
                             Text("To download from GoFile on Desktop, an additional browser engine component (Chromium) is required (~150MB).")
                             if (engineState is DesktopWebEngine.State.Error) {
                                 Spacer(Modifier.height(8.dp))
                                 Text("Previous Error: ${(engineState as DesktopWebEngine.State.Error).message}")
                             }
                         }
                     },
                     confirmButton = {
                         Button(onClick = {
                             scope.launch { DesktopWebEngine.init() }
                         }) {
                             Text("Download & Install")
                         }
                     },
                     dismissButton = {
                         TextButton(onClick = {
                             showDownloadDialog = false
                             onResult(null)
                         }) {
                             Text("Cancel")
                         }
                     }
                 )
             } else if (engineState is DesktopWebEngine.State.Downloading || engineState is DesktopWebEngine.State.Initializing) {
                 AlertDialog(
                     onDismissRequest = { /* Prevent dismiss during download */ },
                     title = { Text("Initializing Engine...") },
                     text = {
                         Column {
                             Text("Please wait while the browser engine is set up.")
                             Spacer(Modifier.height(16.dp))
                             if (engineState is DesktopWebEngine.State.Downloading) {
                                 val progress = (engineState as DesktopWebEngine.State.Downloading).progress
                                 LinearProgressIndicator(progress = progress)
                                 Text("${(progress * 100).toInt()}%")
                             } else {
                                 LinearProgressIndicator()
                             }
                         }
                     },
                     confirmButton = {}
                 )
             } else if (engineState is DesktopWebEngine.State.RestartRequired) {
                 AlertDialog(
                     onDismissRequest = { 
                         showDownloadDialog = false 
                         onResult(null) 
                     },
                     title = { Text("Restart Required") },
                     text = { Text("The browser engine has been installed. Please restart the application to enable GoFile support.") },
                     confirmButton = {
                         Button(onClick = {
                             showDownloadDialog = false
                             onResult(null)
                             // Optional: System.exit(0)
                         }) {
                             Text("OK")
                         }
                     }
                 )
             } else if (engineState is DesktopWebEngine.State.Ready) {
                 // Close dialog once ready
                 LaunchedEffect(Unit) {
                     showDownloadDialog = false
                 }
             }
        }

        // Only render WebView if Ready and NOT showing dialog (to avoid flicker)
        // Add a small delay to ensure the Composable is fully attached to the window/peer before creating the generic SwingPanel
        // This helps prevent 'createContext(...) must not be null' AWT errors.
        var readyToRender by remember { mutableStateOf(false) }
        LaunchedEffect(engineState, showDownloadDialog) {
            val stateName = engineState::class.simpleName
            Logger.d("DesktopLinkExtractor", "State Update: $stateName, ShowDialog: $showDownloadDialog")
            
            if (engineState is DesktopWebEngine.State.Ready && !showDownloadDialog) {
                Logger.d("DesktopLinkExtractor", "Engine Ready. Waiting delay...")
                delay(200) // Increased to 200ms
                Logger.d("DesktopLinkExtractor", "Delay finished. Setting readyToRender = true")
                readyToRender = true
            } else {
                if (readyToRender) Logger.d("DesktopLinkExtractor", "Engine not ready or dialog shown. Hiding WebView.")
                readyToRender = false
            }
        }

        if (readyToRender) {
            Logger.d("DesktopLinkExtractor", "Rendering WebView now.")
            val webViewState = rememberWebViewState(url)
            
            // Invisible WebView (Must be non-zero size for CEF to tick)
            // Removed alpha() as it causes 'createContext' crashes on some Linux/Swing setups due to transparency context issues.
            // Size must be small but visible to layout.
            WebView(
                state = webViewState,
                navigator = navigator,
                modifier = Modifier.size(1.dp)
            )
            
            // Logic to check page load and inject JS
            LaunchedEffect(webViewState.pageTitle) {
                val title = webViewState.pageTitle
                // Logger.d("DesktopLinkExtractor", "Page Title Changed: $title")
                if (title != null && title.startsWith("MEGUMI:")) {
                    Logger.i("DesktopLinkExtractor", "Title-based extraction success!")
                    val json = title.removePrefix("MEGUMI:")
                    try {
                        val result = com.google.gson.JsonParser.parseString(json).asJsonObject
                        val extractedUrl = result.get("url").asString
                        val cookie = if (result.has("cookie")) result.get("cookie").asString else null
                        val userAgent = if (result.has("userAgent")) result.get("userAgent").asString else null
                        
                        onResult(ExtractionResult(extractedUrl, cookie, userAgent))
                    } catch(e: Exception) {
                        Logger.e("DesktopLinkExtractor", "Title JSON Error: ${e.message}")
                    }
                }
            }
            
            LaunchedEffect(webViewState.loadingState) {
                if (url.isBlank()) return@LaunchedEffect
                if (webViewState.loadingState is LoadingState.Finished) {
                     // Attempt JS Extraction loop
                     var attempts = 0
                     val maxAttempts = 30
                     var found = false
                     
                     while (attempts < maxAttempts && !found) {
                         delay(1000)
                          attempts++
                          Logger.d("DesktopLinkExtractor", "Extraction Attempt $attempts/$maxAttempts. Title: ${webViewState.pageTitle}, URL: ${webViewState.lastLoadedUrl}")
                         
                         val js = """
                            (function() {
                                try {
                                    var resultLink = null;
                                    // 1. Check native appdata object (fastest)
                                    if (typeof appdata !== 'undefined' && appdata.fileManager?.mainContent?.data?.children) {
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
                                    // 2. Fallback: Check window.go.appdata which is common in GoFile
                                    if (!resultLink && typeof window.go !== 'undefined' && window.go.appdata && window.go.appdata.fileManager?.mainContent?.data?.children) {
                                        var children = window.go.appdata.fileManager.mainContent.data.children;
                                         var keys = Object.keys(children);
                                         for (var i = 0; i < keys.length; i++) {
                                             var item = children[keys[i]];
                                             if (item.type === 'file' && item.link && item.link.match(/\.(mkv|mp4|webm|avi)/i)) {
                                                 resultLink = item.link;
                                                 break;
                                             }
                                         }
                                    }
                                    // 3. Last resort DOM scraping
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
                                        var json = JSON.stringify({ url: resultLink, cookie: document.cookie, userAgent: navigator.userAgent });
                                        document.title = "MEGUMI:" + json;
                                        return json;
                                    }
                                } catch(e) { return null; }
                                return null;
                            })();
                         """.trimIndent()
                         
                         try {
                              navigator.evaluateJavaScript(js) { result: String ->
                                   Logger.d("DesktopLinkExtractor", "JS Result: $result")
                                   if (!found && result != "null" && result != "\"null\"" && result != "undefined" && result != "\"undefined\"") {
                                       try {
                                           // Result might be double-escaped JSON string
                                           val cleanJson = result.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
                                           
                                           val jsonObject = JsonParser.parseString(cleanJson).asJsonObject
                                           val link = if (jsonObject.has("url")) jsonObject.get("url").asString else ""
                                           val cookie = if (jsonObject.has("cookie")) jsonObject.get("cookie").asString else ""
                                           val ua = if (jsonObject.has("userAgent")) jsonObject.get("userAgent").asString else ""
                                           
                                           if (link.isNotEmpty()) {
                                               found = true
                                               onResult(ExtractionResult(link, cookie, ua))
                                           }
                                       } catch (e: Exception) {
                                            Logger.e("DesktopLinkExtractor", "JSON Parse error: ${e.message}")
                                       }
                                   }
                              }
                         } catch (e: Exception) {
                             Logger.e("DesktopLinkExtractor", "JS Eval failed: ${e.message}")
                         }
                         
                         if (found) break
                     }
                     
                     if (!found && attempts >= maxAttempts) {
                         onResult(null)
                     }
                }
            }
        }
    }
}
