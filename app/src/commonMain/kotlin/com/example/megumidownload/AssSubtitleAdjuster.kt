package com.example.megumidownload

import java.io.File
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileWriter
import java.util.regex.Pattern

object AssSubtitleAdjuster {

    // Format: h:mm:ss.cc (allow multiple digits for hour)
    private val TIME_PATTERN = Pattern.compile("(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})")

    data class AssEvent(
        var startMs: Long,
        var endMs: Long,
        val rawLine: String,
        val parts: MutableList<String>
    )

    fun adjustTiming(inputFile: File, outputFile: File, offsetMs: Long, keyframesMs: List<Long> = emptyList()) {
        val events = mutableListOf<AssEvent>()
        val headerLines = mutableListOf<String>()
        val reader = BufferedReader(FileReader(inputFile))
        
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line!!.startsWith("Dialogue:")) {
                val parts = line!!.split(",", limit = 10).toMutableList()
                if (parts.size >= 10) {
                    val startMs = parseTimeToMs(parts[1].trim())
                    val endMs = parseTimeToMs(parts[2].trim())
                    events.add(AssEvent(startMs + offsetMs, endMs + offsetMs, line!!, parts))
                } else {
                    headerLines.add(line!!)
                }
            } else {
                headerLines.add(line!!)
            }
        }
        reader.close()

        if (keyframesMs.isNotEmpty()) {
            Logger.d("AssSubtitleAdjuster", "Applying keyframe snapping with ${keyframesMs.size} keyframes.")
            applyKeyframeSnapping(events, keyframesMs)
        } else {
            Logger.d("AssSubtitleAdjuster", "No keyframes provided. Applying simple offset only.")
        }

        val writer = FileWriter(outputFile)
        for (header in headerLines) {
            writer.write(header + "\n")
        }
        
        for (event in events) {
            event.parts[1] = formatMsToAss(event.startMs)
            event.parts[2] = formatMsToAss(event.endMs)
            writer.write(event.parts.joinToString(",") + "\n")
        }
        writer.close()
    }

    private fun applyKeyframeSnapping(events: MutableList<AssEvent>, keyframes: List<Long>) {
        val minDurationMs = 100L
        val maxGapMs = 650L
        val maxOverlapMs = 0L

        // Pass 1: Nearest Keyframe
        for (event in events) {
            val nearestStart = keyframes.minByOrNull { kotlin.math.abs(it - event.startMs) } ?: event.startMs
            val nearestEnd = keyframes.minByOrNull { kotlin.math.abs(it - event.endMs) } ?: event.endMs

            if (kotlin.math.abs(nearestStart - event.startMs) <= 500) event.startMs = nearestStart
            if (kotlin.math.abs(nearestEnd - event.endMs) <= 500) event.endMs = nearestEnd
            
            if (event.endMs <= event.startMs) event.endMs = event.startMs + minDurationMs
        }

        // Pass 2: End Bias
        for (event in events) {
            val possibleStarts = keyframes.filter { it <= event.startMs }
            val nearestStartBias = possibleStarts.maxOrNull() ?: event.startMs
            
            val possibleEnds = keyframes.filter { it >= event.endMs }
            val nearestEndBias = possibleEnds.minOrNull() ?: event.endMs

            if (kotlin.math.abs(nearestStartBias - event.startMs) <= 500) event.startMs = nearestStartBias
            if (kotlin.math.abs(nearestEndBias - event.endMs) <= 500) event.endMs = nearestEndBias
            
            if (event.endMs <= event.startMs) event.endMs = event.startMs + minDurationMs
        }

        // Pass 3: Continuity
        for (i in 0 until events.size - 1) {
            val current = events[i]
            val next = events[i + 1]
            val gap = next.startMs - current.endMs
            
            if (gap > 0 && gap <= maxGapMs) {
                next.startMs = current.endMs
            } else if (gap < 0 && kotlin.math.abs(gap) <= maxOverlapMs) {
                current.endMs = next.startMs
            }
        }
        
        // Final Duration Check
        for (event in events) {
             if (event.endMs <= event.startMs) event.endMs = event.startMs + minDurationMs
        }
    }

    private fun parseTimeToMs(timeStr: String): Long {
        val matcher = TIME_PATTERN.matcher(timeStr)
        if (matcher.matches()) {
            val h = matcher.group(1).toLong()
            val m = matcher.group(2).toLong()
            val s = matcher.group(3).toLong()
            val cs = matcher.group(4).toLong()
            return (h * 3600000) + (m * 60000) + (s * 1000) + (cs * 10)
        }
        return 0L
    }

    private fun formatMsToAss(totalMs: Long): String {
        var ms = totalMs
        if (ms < 0) ms = 0
        val h = ms / 3600000
        val rem1 = ms % 3600000
        val m = rem1 / 60000
        val rem2 = rem1 % 60000
        val s = rem2 / 1000
        val cs = (rem2 % 1000) / 10
        return String.format("%d:%02d:%02d.%02d", h, m, s, cs)
    }
    
    // Deprecated simple shift, kept for reference or simple usage if needed, but main logic is above
    private fun shiftTime(timeStr: String, offsetMs: Long): String {
        return formatMsToAss(parseTimeToMs(timeStr) + offsetMs)
    }
}
