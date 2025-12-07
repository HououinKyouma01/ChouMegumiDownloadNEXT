package com.example.megumidownload

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

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
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            // Handle encoding issues? Usually InputStream better for XML than StringReader
            val inputStream = ByteArrayInputStream(xmlData.toByteArray())
            val doc = builder.parse(inputStream)
            doc.documentElement.normalize()

            val nodeList = doc.getElementsByTagName("item")
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    
                    val title = getTagValue("title", element)
                    val link = getTagValue("link", element)
                    val description = getTagValue("description", element)
                    val pubDate = getTagValue("pubDate", element)
                    
                    items.add(RssItem(title, link, description, pubDate, extractLinks(description)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun getTagValue(tag: String, element: Element): String {
        val nodeList = element.getElementsByTagName(tag).item(0)?.childNodes
        val node = nodeList?.item(0)
        return node?.nodeValue?.trim() ?: ""
    }

    private fun extractLinks(html: String): Map<String, String> {
        val links = mutableMapOf<String, String>()
        
        // Extract hrefs with labels
        // Pattern to find <a href="...">Label</a>
        // We look for specific hosts we care about: GoFile, AkiraBox, BuzzHeavier, etc.
        val knownHosts = listOf("GoFile", "AkiraBox", "BuzzHeavier", "KrakenFiles", "MultiUp", "MdiaLoad", "Torrent", "Magnet")
        
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
