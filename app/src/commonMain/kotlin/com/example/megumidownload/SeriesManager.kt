package com.example.megumidownload


import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class SeriesEntry(
    val fileNameMatch: String,
    val folderName: String,
    val seasonNumber: String,
    val replaceUrl: String = "",
    val fixTiming: Boolean = false,
    val notify: Boolean = true,
    val overrideGroup: String? = null // Null = Auto/Default
)

class SeriesManager(val dataDir: File) {
    private val gson = Gson()
    private val seriesFile = File(dataDir, "series_list.json")

    fun getSeriesList(): List<SeriesEntry> {
        if (!seriesFile.exists()) return emptyList()
        val json = seriesFile.readText()
        val type = object : TypeToken<List<SeriesEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveSeriesList(list: List<SeriesEntry>) {
        val json = gson.toJson(list)
        seriesFile.writeText(json)
    }
    
    fun addSeries(entry: SeriesEntry) {
        val list = getSeriesList().toMutableList()
        // Check for duplicates based on folderName (as per requirement)
        if (list.none { it.folderName == entry.folderName }) {
            list.add(entry)
            saveSeriesList(list)
        }
    }

    fun updateSeries(oldEntry: SeriesEntry, newEntry: SeriesEntry) {
        val list = getSeriesList().toMutableList()
        val index = list.indexOfFirst { it.folderName == oldEntry.folderName && it.seasonNumber == oldEntry.seasonNumber }
        if (index != -1) {
            list[index] = newEntry
            saveSeriesList(list)
        }
    }

    fun deleteSeries(entry: SeriesEntry) {
        val list = getSeriesList().toMutableList()
        val index = list.indexOfFirst { it.folderName == entry.folderName && it.seasonNumber == entry.seasonNumber }
        if (index != -1) {
            list.removeAt(index)
            saveSeriesList(list)
        }
    }

    fun importSeries(file: File): Int {
        if (!file.exists()) return 0
        val lines = file.readLines()
        val currentList = getSeriesList().toMutableList()
        var addedCount = 0

        for (line in lines) {
            if (line.isBlank() || line.trim().startsWith("#") || !line.contains("|")) continue
            
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 3) continue

            val fileNameMatch = parts[0]
            val folderName = parts[1]
            val seasonNumber = parts[2]
            var replaceUrl = ""
            var fixTiming = false

            if (parts.size > 3) {
                val part4 = parts[3]
                if (part4.startsWith("http://") || part4.startsWith("https://")) {
                    replaceUrl = part4
                    if (parts.size > 4 && parts[4].uppercase() == "FIXTIMING") {
                        fixTiming = true
                    }
                } else if (part4.uppercase() == "FIXTIMING") {
                    fixTiming = true
                }
            }

            // Duplicate check by folderName
            if (currentList.none { it.folderName == folderName }) {
                currentList.add(SeriesEntry(fileNameMatch, folderName, seasonNumber, replaceUrl, fixTiming, true, null))
                addedCount++
            }
        }
        saveSeriesList(currentList)
        return addedCount
    }

    fun exportSeries(file: File) {
        val list = getSeriesList()
        val sb = StringBuilder()
        for (entry in list) {
            sb.append("${entry.fileNameMatch} | ${entry.folderName} | ${entry.seasonNumber}")
            if (entry.replaceUrl.isNotEmpty()) {
                sb.append(" | ${entry.replaceUrl}")
            }
            if (entry.fixTiming) {
                sb.append(" | FIXTIMING")
            }
            sb.append("\n")
        }
        file.writeText(sb.toString())
    }
}
