package com.example.megumidownload

import androidx.compose.runtime.Composable

data class ExtractionResult(
    val url: String,
    val cookie: String?,
    val userAgent: String?
)

interface LinkExtractor {
    @Composable
    fun Extract(url: String, onResult: (ExtractionResult?) -> Unit)
}
