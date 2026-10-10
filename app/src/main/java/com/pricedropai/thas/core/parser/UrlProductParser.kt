package com.pricedropai.thas.core.parser

import com.pricedropai.thas.core.model.Store
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedProductUrl(
    val originalUrl: String,
    val canonicalUrl: String,
    val store: Store?,
    val productId: String?,
    val searchQuery: String
)

object UrlProductParser {

    private val URL_REGEX = Pattern.compile(
        "https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+",
        Pattern.CASE_INSENSITIVE
    )

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "dclid", "msclkid", "ref", "ref_", "qid", "sr",
        "tag", "affid", "affiliate", "sprefix", "crid", "keywords",
        "pd_rd_r", "pd_rd_w", "pd_rd_wg", "pf_rd_r", "pf_rd_p", "_encoding"
    )

    private val redirectClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun isEcommerceUrl(text: String): Boolean {
        val lower = text.lowercase().trim()
        val hosts = listOf("amazon.", "amzn.to", "amzn.in", "flipkart.com", "fkrt.it", "meesho.com", "myntra.com")
        return hosts.any { lower.contains(it) } && (lower.startsWith("http://") || lower.startsWith("https://") || lower.contains("http"))
    }

    fun extractFirstUrl(rawText: String): String? {
        val matcher = URL_REGEX.matcher(rawText)
        return if (matcher.find()) matcher.group(0) else null
    }

    /**
     * Resolves short links (amzn.to, fkrt.it) if needed, without hanging indefinitely.
     */
    fun resolveRedirectUrl(url: String): String {
        val lower = url.lowercase()
        if (lower.contains("amzn.to") || lower.contains("fkrt.it") || lower.contains("bit.ly")) {
            return try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .head()
                    .build()
                val response = redirectClient.newCall(request).execute()
                val finalUrl = response.request.url.toString()
                response.close()
                finalUrl
            } catch (e: Exception) {
                url
            }
        }
        return url
    }

    /**
     * Normalizes a product URL: strips tracking query params, keeps product-identifying params.
     */
    fun normalizeUrl(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return rawUrl
            val path = uri.path ?: ""
            val rawQuery = uri.rawQuery

            if (rawQuery.isNullOrBlank()) {
                return "$scheme://$host$path"
            }

            val queryPairs = rawQuery.split("&").filter { it.isNotBlank() }
            val cleanPairs = mutableListOf<String>()

            for (pair in queryPairs) {
                val parts = pair.split("=", limit = 2)
                val key = parts[0].trim()
                if (!TRACKING_PARAMS.contains(key.lowercase())) {
                    cleanPairs.add(pair)
                }
            }

            if (cleanPairs.isNotEmpty()) {
                "$scheme://$host$path?${cleanPairs.joinToString("&")}"
            } else {
                "$scheme://$host$path"
            }
        } catch (e: Exception) {
            rawUrl
        }
    }

    /**
     * Extracts structured information from a shared text or URL.
     */
    fun parse(rawInput: String): ParsedProductUrl {
        val cleanInput = rawInput.trim()
        val foundUrl = extractFirstUrl(cleanInput)

        if (foundUrl == null) {
            return ParsedProductUrl(
                originalUrl = "",
                canonicalUrl = "",
                store = null,
                productId = null,
                searchQuery = cleanInput.take(100)
            )
        }

        val resolved = resolveRedirectUrl(foundUrl)
        val canonical = normalizeUrl(resolved)
        val store = Store.fromUrl(resolved)
        val (productId, extractedKeyword) = extractProductIdAndKeyword(resolved, store)

        val finalQuery = if (!extractedKeyword.isNullOrBlank()) {
            extractedKeyword
        } else if (!productId.isNullOrBlank()) {
            productId
        } else {
            cleanInput.replace(foundUrl, "").trim().takeIf { it.isNotBlank() } ?: "Product"
        }

        return ParsedProductUrl(
            originalUrl = foundUrl,
            canonicalUrl = canonical,
            store = store,
            productId = productId,
            searchQuery = finalQuery
        )
    }

    fun extractSearchQuery(rawInput: String): String {
        return parse(rawInput).searchQuery
    }

    private fun extractProductIdAndKeyword(url: String, store: Store?): Pair<String?, String?> {
        return try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val segments = path.split("/").filter { it.isNotBlank() }
            val queryMap = parseQueryParams(uri.rawQuery)

            when (store) {
                Store.AMAZON -> {
                    // Patterns: /dp/B0CHX1W1XY/ or /gp/product/B0CHX1W1XY or /<slug>/dp/B0CHX1W1XY
                    val dpIndex = segments.indexOf("dp")
                    val asin = if (dpIndex >= 0 && dpIndex + 1 < segments.size) {
                        segments[dpIndex + 1]
                    } else {
                        val productIndex = segments.indexOf("product")
                        if (productIndex >= 0 && productIndex + 1 < segments.size) segments[productIndex + 1] else null
                    }
                    val slug = if (dpIndex > 0) segments[dpIndex - 1] else if (segments.isNotEmpty() && segments[0] != "dp" && segments[0] != "gp") segments[0] else null
                    Pair(asin, slug?.let { cleanSlug(it) })
                }
                Store.FLIPKART -> {
                    // Pattern: /<slug>/p/<pid>?pid=...
                    val pIndex = segments.indexOf("p")
                    val pid = queryMap["pid"] ?: if (pIndex >= 0 && pIndex + 1 < segments.size) segments[pIndex + 1] else null
                    val slug = if (pIndex > 0) segments[pIndex - 1] else segments.firstOrNull()
                    Pair(pid, slug?.let { cleanSlug(it) })
                }
                Store.MYNTRA -> {
                    // Pattern: /<category>/<brand>/<slug>/<id>/buy
                    val buyIndex = segments.indexOf("buy")
                    val id = if (buyIndex > 0) segments[buyIndex - 1] else segments.lastOrNull { it.all { c -> c.isDigit() } }
                    val slugIndex = if (buyIndex > 1) buyIndex - 2 else segments.size - 2
                    val slug = if (slugIndex >= 0 && slugIndex < segments.size) segments[slugIndex] else segments.firstOrNull()
                    Pair(id, slug?.let { cleanSlug(it) })
                }
                Store.MEESHO -> {
                    // Pattern: /<slug>/p/<id> or /s/p/<id>
                    val id = if (segments.size >= 3 && segments[1] == "p") segments[2] else segments.lastOrNull()
                    val slug = if (segments.isNotEmpty() && segments[0] != "s") segments[0] else null
                    Pair(id, slug?.let { cleanSlug(it) })
                }
                null -> {
                    Pair(null, segments.lastOrNull()?.let { cleanSlug(it) })
                }
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (pair in rawQuery.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.isNotEmpty()) {
                val key = parts[0]
                val value = if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8.toString()) else ""
                map[key] = value
            }
        }
        return map
    }

    private fun cleanSlug(rawSlug: String): String {
        val decoded = try {
            URLDecoder.decode(rawSlug, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            rawSlug
        }

        val cleaned = decoded
            .replace("-", " ")
            .replace("_", " ")
            .replace("+", " ")
            .replace(Regex("(?i)\\b(ref|qid|sr|keywords|tag|affid|utm_[a-z]+|pd_rd_[a-z]+|source|sprefix)\\b.*"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.length < 3) {
            return ""
        }

        return cleaned.take(70).split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
