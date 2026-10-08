package com.example.pricedropai

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object UrlProductParser {

    fun isEcommerceUrl(text: String): Boolean {
        val lower = text.lowercase().trim()
        return (lower.contains("amazon.in") ||
                lower.contains("amzn.to") ||
                lower.contains("amzn.in") ||
                lower.contains("flipkart.com") ||
                lower.contains("fkrt.it")) &&
                (lower.startsWith("http://") || lower.startsWith("https://"))
    }

    fun extractSearchQuery(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val path = uri.path ?: ""

            when {
                // Amazon standard format: /Apple-iPhone-15-128-GB/dp/B0CHX1W1XY/
                url.contains("amazon") -> {
                    val segments = uri.pathSegments
                    val dpIndex = segments.indexOf("dp")
                    if (dpIndex > 0) {
                        cleanSlug(segments[dpIndex - 1])
                    } else if (segments.isNotEmpty()) {
                        cleanSlug(segments[0])
                    } else "Amazon Product"
                }

                // Flipkart format: /apple-iphone-15-blue-128-gb/p/itm...
                url.contains("flipkart") -> {
                    val segments = uri.pathSegments
                    val pIndex = segments.indexOf("p")
                    if (pIndex > 0) {
                        cleanSlug(segments[pIndex - 1])
                    } else if (segments.isNotEmpty()) {
                        cleanSlug(segments[0])
                    } else "Flipkart Product"
                }

                else -> "Tracked Product"
            }
        } catch (e: Exception) {
            "Tracked Product"
        }
    }

    private fun cleanSlug(rawSlug: String): String {
        val decoded = try {
            URLDecoder.decode(rawSlug, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            rawSlug
        }

        // Slug-la irukra hyphens & underscores-ai space aakki clean panrom
        return decoded
            .replace("-", " ")
            .replace("_", " ")
            .replace(Regex("(?i)\\b(ref|qid|sr|keywords|tag)\\b.*"), "")
            .trim()
            .take(60)
    }
}