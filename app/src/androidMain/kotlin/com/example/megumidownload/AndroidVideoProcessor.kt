package com.example.megumidownload

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ports the logic from FixTiming.py
 */
class AndroidVideoProcessor(private val context: Context) : VideoProcessor {
    private val TAG = "VideoProcessor"

    override suspend fun processVideo(
        inputMkv: File,
        outputMkv: File,
        subtitleOffsetMs: Long,
        replaceFile: File?,
        fixTiming: Boolean,
        subtitleLanguage: String
    ): Boolean = withContext(Dispatchers.IO) {
        val filesToDelete = mutableListOf<File>()
        try {
            // 0. Get Duration for Progress Calculation
            var durationMs = 0L
            if (fixTiming) {
                val durationSession = com.arthenica.ffmpegkit.FFprobeKit.execute("-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"${inputMkv.absolutePath}\"")
                if (ReturnCode.isSuccess(durationSession.returnCode)) {
                     durationMs = (durationSession.allLogsAsString.trim().toDoubleOrNull()?.times(1000))?.toLong() ?: 0L
                     Logger.d(TAG, "Video duration: ${durationMs}ms")
                }
            }
    
            // 1. Extract Subtitles using FFmpeg
            ProgressRepository.updateProgress(inputMkv.name, "Extracting Subtitles", 0.1f)
            val subtitleFile = File(context.cacheDir, "extracted_${inputMkv.name}.ass")
            filesToDelete.add(subtitleFile)
            if (subtitleFile.exists()) subtitleFile.delete()
            
            Logger.d(TAG, "Extracting subtitles from ${inputMkv.absolutePath} to ${subtitleFile.absolutePath}")
            val extractSession = FFmpegKit.execute("-i \"${inputMkv.absolutePath}\" -map 0:s:0 \"${subtitleFile.absolutePath}\" -y")
            
            if (!ReturnCode.isSuccess(extractSession.returnCode)) {
                Logger.e(TAG, "Failed to extract subtitles. Output: ${extractSession.allLogsAsString}")
                return@withContext false
            } else {
                Logger.d(TAG, "Extraction success.")
            }
            
            // 2. Process Subtitles (Replace Text)
            ProgressRepository.updateProgress(inputMkv.name, "Replacing Text", 0.2f)
            if (!processSubtitles(subtitleFile, replaceFile)) {
                 Logger.w(TAG, "Subtitle text replacement failed or skipped.")
            }
    
            var finalSubtitleFile = subtitleFile
    
            if (fixTiming) {
                // 3. Re-encode Video for Keyframes (Heavy Operation)
                // This mimics the Python script's logic to force a specific GOP structure for accurate timing snapping.
                val reencodedVideoFile = File(context.cacheDir, "temp_reencoded_${inputMkv.name}.mkv")
                filesToDelete.add(reencodedVideoFile)
                if (reencodedVideoFile.exists()) reencodedVideoFile.delete()
        
                Logger.d(TAG, "Re-encoding video to generate keyframes (this may take a while)...")
                ProgressRepository.updateProgress(inputMkv.name, "Re-encoding (Heavy)", 0.25f)
                
                // Enable statistics callback for progress
                if (durationMs > 0) {
                    com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback { stats ->
                        val timeMs = stats.time.toLong()
                        val progress = (timeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        // Map re-encoding progress to 25% -> 85% of total task
                        val totalProgress = 0.25f + (progress * 0.6f)
                        ProgressRepository.updateProgress(inputMkv.name, "Re-encoding: ${(progress * 100).toInt()}%", totalProgress)
                    }
                }
        
                // ffmpeg -y -i input -vf scale=-2:720 -c:v libx264 -c:a copy -preset medium -g 250 -keyint_min 24 -sc_threshold 40 -crf 23 -x264-params keyint=250:min-keyint=24:scenecut=40:no-mbtree=0 -f matroska output
                val reencodeCmd = "-y -i \"${inputMkv.absolutePath}\" -vf scale=-2:720 -c:v libx264 -c:a copy -preset medium -g 250 -keyint_min 24 -sc_threshold 40 -crf 23 -x264-params keyint=250:min-keyint=24:scenecut=40:no-mbtree=0 -f matroska \"${reencodedVideoFile.absolutePath}\""
                val reencodeSession = FFmpegKit.execute(reencodeCmd)
        
                // Disable callback after re-encoding
                com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback(null)
        
                if (!ReturnCode.isSuccess(reencodeSession.returnCode)) {
                    Logger.e(TAG, "Failed to re-encode video. Output: ${reencodeSession.allLogsAsString}")
                    Logger.w(TAG, "Falling back to original video for keyframes.")
                } else {
                    Logger.d(TAG, "Re-encoding success.")
                }
                
                val videoForKeyframes = if (reencodedVideoFile.exists() && reencodedVideoFile.length() > 0) reencodedVideoFile else inputMkv
        
                // 4. Extract Keyframes
                ProgressRepository.updateProgress(inputMkv.name, "Extracting Keyframes", 0.85f)
                val keyframes = mutableListOf<Long>()
                Logger.d(TAG, "Extracting keyframes from ${videoForKeyframes.absolutePath}")
                val probeSession = com.arthenica.ffmpegkit.FFprobeKit.execute("-v error -select_streams v:0 -show_entries packet=pts_time,flags -of csv=p=0 -skip_frame nokey \"${videoForKeyframes.absolutePath}\"")
                
                if (ReturnCode.isSuccess(probeSession.returnCode)) {
                    val output = probeSession.allLogsAsString
                    output.lines().forEach { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2 && parts[1].contains("K")) {
                            try {
                                val timestamp = (parts[0].toDouble() * 1000).toLong()
                                keyframes.add(timestamp)
                            } catch (e: Exception) { }
                        }
                    }
                    Logger.d(TAG, "Found ${keyframes.size} keyframes.")
                } else {
                    Logger.e(TAG, "Failed to extract keyframes. Output: ${probeSession.allLogsAsString}")
                }
        
                // Clean up re-encoded file
                if (reencodedVideoFile.exists()) reencodedVideoFile.delete()
        
                // 5. Adjust Timing
                ProgressRepository.updateProgress(inputMkv.name, "Adjusting Timing", 0.9f)
                val adjustedSubtitleFile = File(context.cacheDir, "adjusted_${inputMkv.name}.ass")
                filesToDelete.add(adjustedSubtitleFile)
                AssSubtitleAdjuster.adjustTiming(subtitleFile, adjustedSubtitleFile, subtitleOffsetMs, keyframes)
                finalSubtitleFile = adjustedSubtitleFile // Use the adjusted one
            }
    
            // 6. Re-mux using FFmpeg
            ProgressRepository.updateProgress(inputMkv.name, "Muxing", 0.95f)
            Logger.d(TAG, "Muxing to ${outputMkv.absolutePath}")
            // Changed order: Map subtitles (1) before attachments (0:t?) to avoid player issues
            // Added -c:t copy to explicitly copy attachments
            val muxSession = FFmpegKit.execute("-i \"${inputMkv.absolutePath}\" -i \"${finalSubtitleFile.absolutePath}\" -map 0:v -map 0:a -map 1 -map 0:t? -c copy -c:s ass -c:t copy -metadata:s:s:0 language=$subtitleLanguage -max_interleave_delta 0 -disposition:s:0 default \"${outputMkv.absolutePath}\" -y")
            
            if (!ReturnCode.isSuccess(muxSession.returnCode)) {
                Logger.e(TAG, "Failed to mux. Output: ${muxSession.allLogsAsString}")
                return@withContext false
            } else {
                Logger.d(TAG, "Muxing success.")
            }
            
            ProgressRepository.updateProgress(inputMkv.name, "Complete", 1.0f)
            return@withContext true
            
        } finally {
            filesToDelete.forEach {
                try {
                    if (it.exists()) it.delete()
                } catch(e: Exception) {
                    Logger.w(TAG, "Failed to delete temp file: ${it.name}")
                }
            }
        }
    }

    suspend fun processSubtitles(
        subtitleFile: File,
        replaceFile: File?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Read content
            var content = subtitleFile.readText() // TODO: Handle encodings like python script
            
            // Custom replacements (Series specific) - Run FIRST
            if (replaceFile != null && replaceFile.exists()) {
                val replacements = mutableListOf<Pair<String, String>>()
                replaceFile.forEachLine { line ->
                    if (line.isNotBlank() && !line.startsWith("#") && line.contains("|")) {
                        val parts = line.split("|", limit = 2)
                        if (parts[0].isNotBlank()) {
                            replacements.add(parts[0] to parts[1])
                        }
                    }
                }
                content = SubtitleTextReplacer.applyCustomReplacements(content, replacements)
            }
            
            // Standard replacements (Global) - Run SECOND
            content = SubtitleTextReplacer.applyStandardReplacements(content)
            
            subtitleFile.writeText(content)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Error processing subtitles", e)
            false
        }
    }
    
    override suspend fun reencodeVideo(inputPath: String, outputPath: String) = withContext(Dispatchers.IO) {
        // ffmpeg -i input -c:v libx264 -crf 23 -c:a copy output.mkv
        val session = FFmpegKit.execute("-i $inputPath -c:v libx264 -crf 23 -c:a copy $outputPath")
        if (ReturnCode.isSuccess(session.returnCode)) {
            Logger.d(TAG, "FFmpeg success")
            true
        } else {
            Logger.e(TAG, "FFmpeg failed: ${session.failStackTrace}")
            false
        }
    }
}
