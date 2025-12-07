package com.example.megumidownload

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.regex.Pattern

data class RssItem(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String,
    val hostLinks: Map<String, String> // e.g. "GoFile" -> "https://gofile.io/..."
)

object RssParser {
    fun parse(xmlData: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlData))

        var eventType = parser.eventType
        var insideItem = false
        
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "item") {
                        insideItem = true
                        title = ""
                        link = ""
                        description = ""
                        pubDate = ""
                    } else if (insideItem) {
                        when (parser.name) {
                            "title" -> title = parser.nextText()
                            "link" -> link = parser.nextText()
                            "description" -> description = parser.nextText()
                            "pubDate" -> pubDate = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        insideItem = false
                        items.add(RssItem(title.trim(), link.trim(), description.trim(), pubDate.trim(), extractLinks(description)))
                    }
                }
            }
            eventType = parser.next()
        }
        return items
    }

    private fun extractLinks(html: String): Map<String, String> {
        val links = mutableMapOf<String, String>()
        
        // Extract hrefs with labels
        // Pattern to find <a href="...">Label</a>
        // We look for specific hosts we care about: GoFile, AkiraBox, BuzzHeavier, etc.
        val knownHosts = listOf("GoFile", "AkiraBox", "BuzzHeavier", "KrakenFiles", "MultiUp", "MdiaLoad", "Torrent", "Magnet")
        
        // Regex: <a href="([^"]+)"[^>]*>([^<]+)</a>
        // Simplified approach: scan for known host names and grab the preceding href?
        // Or generic "find all links" then filter.
        
        val linkPattern = Pattern.compile("<a href=\"([^\"]+)\"[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE)
        val matcher = linkPattern.matcher(html)
        
        while (matcher.find()) {
            val url = matcher.group(1) ?: continue
            val label = matcher.group(2) ?: "Unknown"
            
            // Check if label matches known hosts or if URL matches
            val cleanLabel = knownHosts.find { label.contains(it, ignoreCase = true) }
            
            if (cleanLabel != null) {
                links[cleanLabel] = url
            } else if (url.contains("magnet:", ignoreCase = true)) {
                 links["Magnet"] = url
            }
        }
        
        return links
    }
}
