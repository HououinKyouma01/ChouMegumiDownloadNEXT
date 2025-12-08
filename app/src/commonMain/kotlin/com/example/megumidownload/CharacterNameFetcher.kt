package com.example.megumidownload

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object CharacterNameFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Data class to hold replacement pairs
    data class NameReplacement(val original: String, val replacement: String)

    fun fetchNames(inputUrl: String): List<NameReplacement> {
        val url = normalizeUrl(inputUrl)
        if (url == null) throw IllegalArgumentException("Unsupported URL format")

        val html = fetchHtml(url)
        val names = extractNames(html, url)
        
        return names
    }

    private fun normalizeUrl(url: String): String? {
        // MyAnimeList handling
        if (url.contains("myanimelist.net/anime/", ignoreCase = true)) {
            // Check if it already has /characters
            if (url.contains("/characters", ignoreCase = true)) {
                return url
            }
            // If it ends with / (e.g. .../anime/12345/) -> append characters
            // If it ends with ID (e.g. .../anime/12345) -> append /characters
            // If it has title slug .../anime/12345/Title -> append /characters
            
            // Heuristic: remove trailing slash, append /characters
            return if (url.endsWith("/")) {
                url + "characters"
            } else {
                url + "/characters"
            }
        }
        // AniDB handling (future proofing as requested)
        if (url.contains("anidb.net", ignoreCase = true)) {
            // AniDB logic (placeholder/basic pass-through if valid)
             return url
        }
        // Anilist handling
        if (url.contains("anilist.co", ignoreCase = true)) {
             return url
        }
        return null
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch page: HTTP ${response.code}")
            val html = response.body?.string() ?: ""
            com.example.megumidownload.Logger.d("CharacterNameFetcher", "Fetched HTML from $url. Length: ${html.length}")
            if (html.length > 500) {
                 com.example.megumidownload.Logger.d("CharacterNameFetcher", "Snippet: ${html.substring(0, 500)}")
            } else {
                 com.example.megumidownload.Logger.d("CharacterNameFetcher", "Content: $html")
            }
            return html
        }
    }

    private fun extractNames(html: String, url: String): List<NameReplacement> {
        val replacements = mutableListOf<NameReplacement>()

        if (url.contains("myanimelist.net")) {
            // Regex for MAL: Matches both 'h3_character_name' (old/some pages) and 'h3_characters_voice_actors' (current).
            val pattern = Pattern.compile("<h3[^>]*class=[\"'][^\"']*(?:h3_character_name|h3_characters_voice_actors)[^\"']*[\"'][^>]*>([\\s\\S]*?)</h3>")
            val matcher = pattern.matcher(html)
            
            while (matcher.find()) {
                val rawContent = matcher.group(1) ?: continue
                // Step 2: Strip tags (<a> or others)
                val fullName = rawContent.replace(Regex("<[^>]*>"), "").trim()
                
                if (fullName.contains(",")) {
                    val parts = fullName.split(",").map { it.trim() }
                    if (parts.size >= 2) {
                        val surname = parts[0]
                        val givenName = parts[1]
                        
                        val original = "$givenName $surname"
                        val replacement = "$surname $givenName"
                        
                        replacements.add(NameReplacement(original, replacement))
                        
                        // Add uppercase variant
                        replacements.add(NameReplacement(original.uppercase(), replacement.uppercase()))
                    }
                }
            }
        } else if (url.contains("anidb.net")) {
             // AniDB: Look for <td class="name char character"> context.
             // Regex matches class containing "character" inside a td.
             val pattern = Pattern.compile("<td[^>]*class=[\"'][^\"']*character[^\"']*[\"'][^>]*>([\\s\\S]*?)</td>")
             val matcher = pattern.matcher(html)
             while (matcher.find()) {
                 val rawContent = matcher.group(1) ?: continue
                 // Strip tags to get name
                 val fullName = rawContent.replace(Regex("<[^>]*>"), "").trim()
                 
                 // Filter out empty or garbage matches if regex was too loose (though 'character' class is specific enough usually)
                 if (fullName.isBlank()) continue

                 if (fullName.contains(" ")) {
                     val parts = fullName.split(" ")
                     if (parts.size >= 2) {
                         val surname = parts[0]
                         val givenName = parts.drop(1).joinToString(" ")
                         
                         val original = "$givenName $surname"
                         val replacement = "$surname $givenName"
                         replacements.add(NameReplacement(original, replacement))
                         
                         // Add uppercase variant
                         replacements.add(NameReplacement(original.uppercase(), replacement.uppercase()))
                     }
                 }
             }
        } else if (url.contains("anilist.co")) {
             return fetchAnilistNames(url)
        }
        
        return replacements.distinct()
    }

    private fun fetchAnilistNames(url: String): List<NameReplacement> {
        // Extract ID: https://anilist.co/anime/169420/...
        val idRegex = Pattern.compile("anilist\\.co/anime/(\\d+)")
        val matcher = idRegex.matcher(url)
        if (!matcher.find()) return emptyList()
        val animeId = matcher.group(1).toInt()

        val query = """
            query {
              Media(id: $animeId) {
                characters(sort: [ROLE, RELEVANCE, ID], perPage: 50) {
                  nodes {
                    name {
                      full
                      native
                    }
                  }
                }
              }
            }
        """.trimIndent()

        // Simple JSON construction to avoid massive Gson boilerplate for REQUEST
        // We use Gson for RESPONSE parsing as it checks types.
        val jsonBody = com.google.gson.JsonObject()
        jsonBody.addProperty("query", query)
        
        val requestBody = okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(), 
            jsonBody.toString()
        )

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody)
            .build()
            
        val replacements = mutableListOf<NameReplacement>()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            
            // Parse with Gson
            val gson = com.google.gson.Gson()
            val result = gson.fromJson(body, AnilistResponse::class.java)
            
            result?.data?.Media?.characters?.nodes?.forEach { node ->
                val fullName = node.name?.full ?: return@forEach
                if (fullName.contains(" ")) {
                     val parts = fullName.split(" ")
                     if (parts.size >= 2) {
                         val surname = parts.last()
                         val givenName = parts.dropLast(1).joinToString(" ")
                         
                         // Anilist 'full' is mostly "Given Surname" (Western)
                         // But for some Japanese entries it complicates.
                         // User said "Western name order by default".
                         // So we assume "First Last".
                         // original = First Last
                         // replacement = Last First
                         
                         val original = fullName
                         val replacement = "$surname $givenName"
                         
                         replacements.add(NameReplacement(original, replacement))
                         replacements.add(NameReplacement(original.uppercase(), replacement.uppercase()))
                     }
                }
            }
        }
        return replacements
    }
    
    // GSON Data Classes
    private data class AnilistResponse(val data: AnilistData? = null)
    private data class AnilistData(val Media: AnilistMedia? = null)
    private data class AnilistMedia(val characters: AnilistCharacterConnection? = null)
    private data class AnilistCharacterConnection(val nodes: List<AnilistCharacterNode>? = null)
    private data class AnilistCharacterNode(val name: AnilistName? = null)
    private data class AnilistName(val full: String? = null, val native: String? = null)
}
