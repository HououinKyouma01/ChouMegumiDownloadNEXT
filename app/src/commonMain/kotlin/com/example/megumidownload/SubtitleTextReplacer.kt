package com.example.megumidownload

object SubtitleTextReplacer {
    
    private val STANDARD_REPLACEMENTS = listOf(
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

    fun applyStandardReplacements(text: String): String {
        var result = text
        // Add A-a -> A-A logic
        for (c in 'a'..'z') {
            result = result.replace("${c.uppercase()}-${c}", "${c.uppercase()}-${c.uppercase()}")
        }
        
        for ((old, new) in STANDARD_REPLACEMENTS) {
            result = result.replace(old, new)
        }
        return result
    }
    
    fun applyCustomReplacements(text: String, replacements: List<Pair<String, String>>): String {
        var result = text
        var count = 0
        for ((old, new) in replacements) {
            // Regex for whole word replacement as per python script
            // (?<!\w)old(?!\w)
            val pattern = "(?<!\\w)${Regex.escape(old)}(?!\\w)".toRegex()
            val matches = pattern.findAll(result).count()
            if (matches > 0) {
                Logger.d("SubtitleTextReplacer", "Replaced '$old' with '$new' ($matches times)")
                result = result.replace(pattern, new)
                count += matches
            }
        }
        Logger.d("SubtitleTextReplacer", "Total custom replacements: $count")
        return result
    }
}
