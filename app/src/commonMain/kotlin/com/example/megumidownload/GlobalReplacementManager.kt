package com.example.megumidownload

import java.io.File

object GlobalReplacementManager {
    private var configFile: File? = null
    private val replacements = mutableListOf<Pair<String, String>>()
    
    // Moved from SubtitleTextReplacer
    private val STANDARD_DEFAULTS = listOf(
        "  " to " ", "Wh-wh" to "W-Wh", "Wh-Wh" to "W-Wh", "Th-th" to "T-Th", "Th-Th" to "T-Th",
        "\\N" to " \\N ", "\\h" to "\\h ",
        "pigtails" to "twintails", "Pigtails" to "Twintails", "Pigtail" to "Twintail",
        "P-Pigtails" to "T-Twintails", "pigtail" to "twintail",
        "pop idol" to "idol", "Pop idol" to "Idol", "Pop Idol" to "Idol", "P-Pop idol" to "I-Idol",
        "rice ball" to "onigiri", "rice balls" to "onigiri", "Rice ball" to "Onigiri",
        "kohai" to "kouhai", "Kohai" to "Kouhai",
        "Holy shit" to "Wow", "holy shit" to "wow",
        "C'mon" to "Come on",
        "M-Meow" to "N-Nyan", "Meow" to "Nyan",
        "Little Sister" to "Younger Sister", "L-Little sister" to "Y-Younger sister",
        "Little sister" to "Younger sister", "little sister" to "younger sister",
        "Big Sister" to "Older Sister", "B-Big sister" to "O-Older sister",
        "Big sister" to "Older sister", "big sister" to "older sister",
        "Big Brother" to "Older Brother", "B-Big brother" to "O-Older brother",
        "Big brother" to "Older brother", "big brother" to "older brother",
        "Pin-Up Girl" to "Gravure Idol", "pin-up girl" to "gravure idol",
        "P-Pin-up girl" to "G-Gravure idol"
    )

    fun init(configDir: File) {
        if (!configDir.exists()) configDir.mkdirs()
        configFile = File(configDir, "global_replacements.txt")
        loadOrInitialize()
    }
    
    fun getReplacements(): List<Pair<String, String>> {
        if (replacements.isEmpty() && configFile != null) {
            loadOrInitialize()
        }
        return replacements
    }
    
    fun saveReplacements(newReplacements: List<Pair<String, String>>) {
        replacements.clear()
        replacements.addAll(newReplacements)
        saveToFile()
    }
    
    private fun loadOrInitialize() {
        val file = configFile ?: return
        replacements.clear()
        
        if (file.exists()) {
            val existingKeys = mutableSetOf<String>()
            file.forEachLine { line ->
                if (line.isNotBlank() && !line.startsWith("#") && line.contains("|")) {
                    val parts = line.split("|", limit = 2)
                    if (parts[0].isNotBlank()) {
                        replacements.add(parts[0] to parts[1])
                        existingKeys.add(parts[0])
                    }
                }
            }
            
            // Merge Defaults: Append only if key doesn't exist
            var modified = false
            STANDARD_DEFAULTS.forEach { (key, value) ->
                if (!existingKeys.contains(key)) {
                    replacements.add(key to value)
                    existingKeys.add(key)
                    modified = true
                }
            }
            
            if (modified) saveToFile()
            
        } else {
            // First run: Use defaults
            replacements.addAll(STANDARD_DEFAULTS)
            saveToFile()
        }
    }
    
    private fun saveToFile() {
        val file = configFile ?: return
        file.bufferedWriter().use { writer ->
            replacements.forEach { (key, value) ->
                writer.write("$key|$value\n")
            }
        }
    }
}
