package com.example.megumidownload

import java.io.File

interface VideoProcessor {
    suspend fun processVideo(
        inputMkv: File,
        outputMkv: File,
        subtitleOffsetMs: Long = 0,
        replaceFile: File? = null
    ): Boolean
    
    suspend fun reencodeVideo(inputPath: String, outputPath: String): Boolean
}
