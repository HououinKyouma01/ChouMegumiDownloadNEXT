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
        
        val globalRules = mutableListOf<Pair<String, String>>()
        data class ActorRule(val actor: String, val oldText: String, val newText: String)
        val actorRules = mutableListOf<ActorRule>()
        
        for ((old, new) in replacements) {
            val trimmedOld = old.trim()
            if (trimmedOld.startsWith("[") && trimmedOld.contains("]")) {
                val endIndex = trimmedOld.indexOf("]")
                val actor = trimmedOld.substring(1, endIndex).trim()
                val oldText = trimmedOld.substring(endIndex + 1).trim()
                if (oldText.isNotEmpty()) {
                    actorRules.add(ActorRule(actor, oldText, new))
                }
            } else {
                globalRules.add(old to new)
            }
        }

        // Helper function for the regex replacement logic
        fun replaceText(input: String, oldText: String, newText: String): String {
            // 1. Split the search term by spaces and escape each word individually
            val words = oldText.trim().split("\\s+".toRegex())
            val escapedWords = words.map { Regex.escape(it) }
            
            // 2. Join words with flexible whitespace/newline matcher
            // Matches: space OR (optional space + (\N or \n) + optional space)
            val flexibleOld = escapedWords.joinToString("(?:\\s+|\\s*(?:\\\\N|\\\\n)\\s*)")
            
            // 3. Lookbehind: Start of string OR Newline/ASS-break OR Non-Word char
            // 4. Lookahead: End of string OR Newline/ASS-break OR Non-Word char
            val pattern = "(?:(?<=^|\\\\N|\\\\n)|(?<!\\w))$flexibleOld(?:(?=$|\\\\N|\\\\n)|(?!\\w))".toRegex(RegexOption.IGNORE_CASE)
            
            var modified = input
            val matches = pattern.findAll(modified).count()
            if (matches > 0) {
                Logger.d("SubtitleTextReplacer", "Replaced '$oldText' with '$newText' ($matches times)")
                modified = modified.replace(pattern, newText)
                count += matches
            }
            return modified
        }

        // Apply Global and Actor Rules line by line (only lines starting with Dialogue:)
        val lines = result.split("\n")
        val newLines = lines.map { line ->
            if (line.startsWith("Dialogue:")) {
                // Dialogue: Layer, Start, End, Style, Actor, MarginL, MarginR, MarginV, Effect, Text
                val parts = line.split(",", limit = 10)
                if (parts.size == 10) {
                    val lineActor = parts[4].trim()
                    var lineText = parts[9]
                    
                    // Apply all global rules
                    for ((oldText, newText) in globalRules) {
                        if (!lineText.contains(newText, ignoreCase = true)) {
                            lineText = replaceText(lineText, oldText, newText)
                        }
                    }
                    
                    // Apply all matching actor rules inside this line's text
                    for (rule in actorRules) {
                        if (lineActor.contains(rule.actor, ignoreCase = true)) {
                            if (!lineText.contains(rule.newText, ignoreCase = true)) {
                                lineText = replaceText(lineText, rule.oldText, rule.newText)
                            }
                        }
                    }
                    
                    // Reassemble the parts
                    parts.subList(0, 9).joinToString(",") + "," + lineText
                } else {
                    line
                }
            } else {
                line
            }
        }
        result = newLines.joinToString("\n")

        Logger.d("SubtitleTextReplacer", "Total custom replacements: $count")
        return result
    }
}
