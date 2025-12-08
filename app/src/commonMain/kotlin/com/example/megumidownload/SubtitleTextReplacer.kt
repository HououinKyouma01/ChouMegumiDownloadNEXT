package com.example.megumidownload

object SubtitleTextReplacer {
    
    fun applyStandardReplacements(text: String): String {
        var result = text
        // Add A-a -> A-A logic
        for (c in 'a'..'z') {
            result = result.replace("${c.uppercase()}-${c}", "${c.uppercase()}-${c.uppercase()}")
        }
        
        val replacements = GlobalReplacementManager.getReplacements()
        for ((old, new) in replacements) {
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
