package com.example.pricedropai

import java.util.regex.Pattern

object ShareLinkExtractor {

    fun extractQueryFromSharedText(sharedText: String): String {
        val lower = sharedText.lowercase()

        // 1. Direct keywords check
        val cleanKeyword = when {
            lower.contains("iphone 15") -> "iPhone 15 128GB"
            lower.contains("iphone 14") -> "iPhone 14 128GB"
            lower.contains("samsung") || lower.contains("s24") -> "Samsung Galaxy S24"
            lower.contains("macbook") -> "MacBook Air M2"
            lower.contains("sony") || lower.contains("wh-1000xm4") -> "Sony WH-1000XM4"
            lower.contains("ipad") -> "Apple iPad 10th Gen"
            lower.contains("nike") -> "Nike Running Shoes"
            else -> ""
        }
        if (cleanKeyword.isNotEmpty()) return cleanKeyword

        // 2. Extract product title from Flipkart / Amazon shared text
        val urlRegex = "https?://[\\w-]+(\\.[\\w-]+)+(/\\S*)?"
        val pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(sharedText)
        val url = if (matcher.find()) matcher.group(0) else ""

        // Extract words from Flipkart / Amazon URLs like flipkart.com/apple-iphone-15-black/p/...
        if (url.isNotEmpty()) {
            val segments = url.split("/").filter { it.isNotBlank() }
            val productSlug = segments.find { it.contains("-") && !it.contains("flipkart") && !it.contains("amazon") }
            if (!productSlug.isNullOrBlank()) {
                return productSlug.replace("-", " ").take(30).trim()
            }
        }

        // 3. Fallback: take clean non-URL words
        val nonUrlText = sharedText.replace(Regex("https?://\\S+"), "").trim()
        return if (nonUrlText.length > 3) nonUrlText.take(30).trim() else "iPhone 15 128GB"
    }
}