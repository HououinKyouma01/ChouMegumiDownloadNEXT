package com.example.megumidownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

class DesktopLinkExtractor : LinkExtractor {
    @Composable
    override fun Extract(url: String, onResult: (ExtractionResult?) -> Unit) {
        // Desktop implementation extraction not yet supported (requires Headless Browser or JCEF)
        LaunchedEffect(url) {
            Logger.w("DesktopLinkExtractor", "Auto-extraction not supported on Desktop for $url")
            onResult(null)
        }
    }
}
