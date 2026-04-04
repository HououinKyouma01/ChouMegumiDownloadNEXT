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
            // Flexible Regex for whole word replacement handling \N and \n
            // 1. Split the search term by spaces and escape each word individually
            val words = old.trim().split("\\s+".toRegex())
            val escapedWords = words.map { Regex.escape(it) }
            
            // 2. Join words with flexible whitespace/newline matcher
            // Matches: space OR (optional space + (\N or \n) + optional space)
            val flexibleOld = escapedWords.joinToString("(?:\\s+|\\s*(?:\\\\N|\\\\n)\\s*)")
            
            // 3. Lookbehind: Start of string OR Newline/ASS-break OR Non-Word char
            // 4. Lookahead: End of string OR Newline/ASS-break OR Non-Word char
            val pattern = "(?:(?<=^|\\\\N|\\\\n)|(?<!\\w))$flexibleOld(?:(?=$|\\\\N|\\\\n)|(?!\\w))".toRegex(RegexOption.IGNORE_CASE)
            
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
