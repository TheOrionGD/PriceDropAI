package com.pricedropai.myapp

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ScrapedStoreProduct(
    val title: String,
    val price: Double,
    val productUrl: String,
    val imageUrl: String
)

object FlipkartDirectScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun scrapeFlipkartDirect(query: String): ScrapedStoreProduct? {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val targetUrl = "https://www.flipkart.com/search?q=$encoded"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .header("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            // Method A: Direct DOM Extraction if static markup exists
            val doc = Jsoup.parse(html)
            val priceEl = doc.select("div.Nx9bqj, div._30jeq3, div.hl05eU div._25b18c div").firstOrNull()
            val titleEl = doc.select("div.KzDlHZ, div._4rR01T, a.s1Q9rs, a.wjcEIp").firstOrNull()
            val linkEl = doc.select("a.CGtC5Q, a._1fQZEK, a.s1Q9rs, a.VJA3rP, a[href*=\"/p/\"]").firstOrNull()
            val imgEl = doc.select("img._53qgcR, img.DByuf4, img._396cs4").firstOrNull()

            if (priceEl != null && titleEl != null) {
                val rawPrice = priceEl.text().replace("[^0-9]".toRegex(), "").toDoubleOrNull()
                if (rawPrice != null && rawPrice > 100.0) {
                    val relHref = linkEl?.attr("href") ?: ""
                    val finalUrl = if (relHref.startsWith("http")) relHref else "https://www.flipkart.com$relHref"
                    return ScrapedStoreProduct(
                        title = titleEl.text(),
                        price = rawPrice,
                        productUrl = finalUrl,
                        imageUrl = imgEl?.attr("src") ?: ""
                    )
                }
            }

            // Method B: Parse Flipkart's embedded __NEXT_DATA__ / Redux JSON state
            val pattern = Pattern.compile("(?<=<script id=\"__NEXT_DATA__\" type=\"application/json\">)(.*?)(?=</script>)", Pattern.DOTALL)
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val jsonRaw = matcher.group(1)
                val root = JSONObject(jsonRaw)
                val slots = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("initialData")
                    ?.optJSONObject("slots")

                // Extract product info from data payload
                val slotsStr = slots?.toString() ?: ""
                val pricePattern = Pattern.compile("\"price\":([0-9]{3,7})")
                val priceMatch = pricePattern.matcher(slotsStr)
                if (priceMatch.find()) {
                    val foundPrice = priceMatch.group(1)?.toDoubleOrNull()
                    if (foundPrice != null) {
                        return ScrapedStoreProduct(
                            title = query.uppercase(),
                            price = foundPrice,
                            productUrl = targetUrl,
                            imageUrl = ""
                        )
                    }
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}