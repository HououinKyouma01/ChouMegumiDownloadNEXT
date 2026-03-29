package com.example.megumidownload

import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopVideoProcessor : VideoProcessor {
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
            // 0. Get Duration
            val durationMs = getDurationMs(inputMkv)
            Logger.d(TAG, "Video duration: ${durationMs}ms")
    
            // 1. Extract Subtitles
            ProgressRepository.updateProgress(inputMkv.name, "Extracting Subtitles", 0.1f)
            val subtitleFile = File(inputMkv.parent, "extracted_${inputMkv.name}.ass")
            filesToDelete.add(subtitleFile)
            
            // ffmpeg -i input -map 0:s:0 output.ass -y
            val extractCmd = listOf("ffmpeg", "-i", inputMkv.absolutePath, "-map", "0:s:0", subtitleFile.absolutePath, "-y")
            if (!runCommand(extractCmd)) {
                 Logger.e(TAG, "Failed to extract subtitles")
                 return@withContext false
            }
            
            // 2. Process Subtitles
            ProgressRepository.updateProgress(inputMkv.name, "Replacing Text", 0.2f)
            if (!processSubtitles(subtitleFile, replaceFile)) {
                 Logger.w(TAG, "Subtitle replacement failed/skipped")
            }
    
            var finalSubtitleFile = subtitleFile
    
            if (fixTiming) {
                // 3. Re-encode for Keyframes
                val reencodedVideoFile = File(inputMkv.parent, "temp_reencoded_${inputMkv.name}.mkv")
                filesToDelete.add(reencodedVideoFile)
                Logger.d(TAG, "Re-encoding video to generate keyframes...")
                ProgressRepository.updateProgress(inputMkv.name, "Re-encoding (Heavy)", 0.25f)
                
                // Simulating progress logic is harder with ProcessBuilder without parsing stderr line by line.
                // For now, we will just block waiting for completion or parse simple logic if needed.
                // To keep it simple for Desktop: just run it.
                val reencodeCmd = listOrArgs("ffmpeg", "-y", "-i", inputMkv.absolutePath, "-vf", "scale=-2:720", "-c:v", "libx264", "-c:a", "copy", 
                    "-preset", "medium", "-g", "250", "-keyint_min", "24", "-sc_threshold", "40", "-crf", "23", 
                    "-x264-params", "keyint=250:min-keyint=24:scenecut=40:no-mbtree=0", "-f", "matroska", reencodedVideoFile.absolutePath)
                    
                if (!runCommand(reencodeCmd)) {
                     Logger.e(TAG, "Failed to re-encode")
                }
                
                val videoForKeyframes = if (reencodedVideoFile.exists() && reencodedVideoFile.length() > 0) reencodedVideoFile else inputMkv
        
                // 4. Extract Keyframes
                ProgressRepository.updateProgress(inputMkv.name, "Extracting Keyframes", 0.85f)
                val keyframes = getKeyframes(videoForKeyframes)
                Logger.d(TAG, "Found ${keyframes.size} keyframes")
                
                if (reencodedVideoFile.exists()) reencodedVideoFile.delete()
        
                // 5. Adjust Timing
                ProgressRepository.updateProgress(inputMkv.name, "Adjusting Timing", 0.9f)
                val adjustedSubtitleFile = File(inputMkv.parent, "adjusted_${inputMkv.name}.ass")
                filesToDelete.add(adjustedSubtitleFile)
                AssSubtitleAdjuster.adjustTiming(subtitleFile, adjustedSubtitleFile, subtitleOffsetMs, keyframes)
                finalSubtitleFile = adjustedSubtitleFile
            }
    
            // 6. Muxing
            ProgressRepository.updateProgress(inputMkv.name, "Muxing", 0.95f)
            Logger.d(TAG, "Muxing to ${outputMkv.absolutePath}")
            // ffmpeg -i input -i adjusted -map 0:v -map 0:a -map 1 -c copy -c:s ass -disposition:s:0 default output -y
            // Changed order: Map subtitles (1) before attachments (0:t?)
            // Added -c:t copy
            val muxCmd = listOf("ffmpeg", "-i", inputMkv.absolutePath, "-i", finalSubtitleFile.absolutePath, 
                "-map", "0:v", "-map", "0:a", "-map", "1", "-map", "0:t?", "-c", "copy", "-c:s", "ass", "-c:t", "copy",
                "-metadata:s:s:0", "language=$subtitleLanguage", "-max_interleave_delta", "0",
                "-disposition:s:0", "default", outputMkv.absolutePath, "-y")
                
            if (!runCommand(muxCmd)) {
                 Logger.e(TAG, "Failed to mux")
                 return@withContext false
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

    override suspend fun reencodeVideo(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = listOf("ffmpeg", "-i", inputPath, "-c:v", "libx264", "-crf", "23", "-c:a", "copy", outputPath)
        if (runCommand(cmd)) {
             Logger.d(TAG, "FFmpeg success")
             true
        } else {
             Logger.e(TAG, "FFmpeg failed")
             false
        }
    }

    private fun getDurationMs(file: File): Long {
         // ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 input
         val cmd = listOf("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", file.absolutePath)
         val output = runCommandWithOutput(cmd)
         return (output.trim().toDoubleOrNull()?.times(1000))?.toLong() ?: 0L
    }
    
    private fun getKeyframes(file: File): List<Long> {
        // ffprobe -v error -select_streams v:0 -show_entries packet=pts_time,flags -of csv=p=0 -skip_frame nokey input
        val cmd = listOf("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "packet=pts_time,flags", "-of", "csv=p=0", "-skip_frame", "nokey", file.absolutePath)
        val output = runCommandWithOutput(cmd)
        val keyframes = mutableListOf<Long>()
        output.lines().forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 2 && parts[1].contains("K")) {
                try {
                    val timestamp = (parts[0].toDouble() * 1000).toLong()
                    keyframes.add(timestamp)
                } catch (e: Exception) {}
            }
        }
        return keyframes
    }

    private fun runCommand(command: List<String>): Boolean {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            // Consume output to prevent blocking
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Optionally log output verbose
                // println(line) 
            }
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun runCommandWithOutput(command: List<String>): String {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            return output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    // Helper for command list construction if needed
    private fun listOrArgs(vararg args: String): List<String> = args.toList()
    
    private suspend fun processSubtitles(subtitleFile: File, replaceFile: File?): Boolean {
         // Same logic as Android, using SubtitleTextReplacer from commonMain
         try {
             var content = subtitleFile.readText()
             // Custom replacements (Series specific) - Run FIRST
             if (replaceFile != null && replaceFile.exists()) {
                 val replacements = mutableListOf<Pair<String, String>>()
                 replaceFile.forEachLine { line ->
                     if (line.isNotBlank() && !line.startsWith("#") && line.contains("|")) {
                        val parts = line.split("|", limit = 2)
                        if (parts[0].isNotBlank()) replacements.add(parts[0] to parts[1])
                     }
                 }
                 content = SubtitleTextReplacer.applyCustomReplacements(content, replacements)
             }
             
             // Standard replacements (Global) - Run SECOND
             content = SubtitleTextReplacer.applyStandardReplacements(content)
             subtitleFile.writeText(content)
             return true
         } catch(e: Exception) {
             e.printStackTrace()
             return false
         }
    }
}
