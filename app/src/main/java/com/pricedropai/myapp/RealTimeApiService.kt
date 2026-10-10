package com.pricedropai.myapp

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object RealTimeApiService {

    const val RAPID_API_KEY = "473e6d847amsh749bec1b1fe78efp113554jsn17f75da2b680"
    const val SERP_API_KEY = "7fddcada4d50a22bdee3d1293d7cd6c6cc90fc74e278da84375ab1a8ef39ea53"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    data class LiveApiProduct(
        val title: String,
        val price: Double,
        val originalPrice: Double,
        val productUrl: String,
        val imageUrl: String
    )

    // 1. Amazon India via RapidAPI
    fun fetchAmazonIndiaLive(query: String): LiveApiProduct? {
        if (RAPID_API_KEY.isBlank()) return null

        return try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val request = Request.Builder()
                .url("https://real-time-amazon-data.p.rapidapi.com/search?query=$encodedQuery&country=IN")
                .get()
                .addHeader("x-rapidapi-key", RAPID_API_KEY)
                .addHeader("x-rapidapi-host", "real-time-amazon-data.p.rapidapi.com")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return null
            val products = data.optJSONArray("products") ?: return null

            if (products.length() > 0) {
                val firstProduct = products.getJSONObject(0)
                val title = firstProduct.optString("product_title", query)
                val url = firstProduct.optString("product_url", "")
                val photo = firstProduct.optString("product_photo", "")

                val rawPriceStr = firstProduct.optString("product_price", "0")
                    .replace("₹", "")
                    .replace(",", "")
                    .trim()
                val price = rawPriceStr.toDoubleOrNull() ?: 0.0

                val rawOriginalStr = firstProduct.optString("product_original_price", "0")
                    .replace("₹", "")
                    .replace(",", "")
                    .trim()
                val originalPrice = rawOriginalStr.toDoubleOrNull() ?: (price * 1.08)

                if (price > 100.0 && url.isNotEmpty()) {
                    LiveApiProduct(title, price, originalPrice, url, photo)
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("RealTimeApi", "Amazon API Error: ${e.localizedMessage}")
            null
        }
    }

    // 2. Flipkart India via SerpAPI (Accurate JSON Parser)
    fun fetchFlipkartLive(query: String): LiveApiProduct? {
        if (SERP_API_KEY.isBlank()) {
            Log.e("RealTimeApi", "SERP_API_KEY is blank - cannot call SerpAPI")
            return null
        }

        return try {
            val encodedQuery = URLEncoder.encode("$query flipkart", StandardCharsets.UTF_8.toString())
            val url = "https://serpapi.com/search.json?engine=google_shopping&q=$encodedQuery&gl=in&hl=en&api_key=$SERP_API_KEY"

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            // DEBUG: log raw response so we can see exactly what SerpAPI returned
            Log.d("RealTimeApi", "Flipkart HTTP status: ${response.code}")
            Log.d("RealTimeApi", "Flipkart raw response (first 800 chars): ${body.take(800)}")

            val json = JSONObject(body)

            // SerpAPI returns a JSON body with an "error" field (still HTTP 200) when the
            // key is invalid, expired, or the monthly quota is exhausted. Catch that first.
            val errorMsg = json.optString("error")
            if (errorMsg.isNotEmpty()) {
                Log.e("RealTimeApi", "SerpAPI returned error: $errorMsg")
                return null
            }

            val shoppingResults = json.optJSONArray("shopping_results")
            if (shoppingResults == null) {
                Log.e("RealTimeApi", "No 'shopping_results' array in SerpAPI response. Full keys: ${json.keys().asSequence().toList()}")
                return null
            }

            if (shoppingResults.length() == 0) {
                Log.e("RealTimeApi", "'shopping_results' array is empty for query: $query flipkart")
                return null
            }

            var fallbackProduct: LiveApiProduct? = null

            for (i in 0 until shoppingResults.length()) {
                val item = shoppingResults.getJSONObject(i)
                val source = item.optString("source", "").lowercase()
                val itemLink = item.optString("link", "")
                val productLink = item.optString("product_link", itemLink)

                // SerpAPI extracted_price number or formatted string price
                val extractedPrice = item.optDouble("extracted_price", 0.0)
                val rawPriceStr = item.optString("price", "")
                    .replace("₹", "")
                    .replace(",", "")
                    .replace("INR", "")
                    .trim()
                val price = if (extractedPrice > 100.0) extractedPrice else (rawPriceStr.toDoubleOrNull() ?: 0.0)

                if (price < 100.0) continue

                val title = item.optString("title", query)
                val thumbnail = item.optString("thumbnail", "")
                val finalUrl = if (productLink.isNotEmpty()) productLink else itemLink

                val productCandidate = LiveApiProduct(
                    title = title,
                    price = price,
                    originalPrice = price * 1.08,
                    productUrl = finalUrl,
                    imageUrl = thumbnail
                )

                // Exact match for Flipkart
                if (source.contains("flipkart") || itemLink.contains("flipkart") || finalUrl.contains("flipkart")) {
                    Log.d("RealTimeApi", "Flipkart Live Match Found: $title -> ₹$price")
                    return productCandidate
                }

                if (fallbackProduct == null) {
                    fallbackProduct = productCandidate
                }
            }

            if (fallbackProduct == null) {
                Log.e("RealTimeApi", "No Flipkart match and no fallback product found among ${shoppingResults.length()} results")
            } else {
                Log.d("RealTimeApi", "No exact Flipkart match; using fallback product: ${fallbackProduct.title}")
            }

            fallbackProduct
        } catch (e: Exception) {
            Log.e("RealTimeApi", "Flipkart SerpAPI Error: ${e.javaClass.simpleName} - ${e.localizedMessage}")
            null
        }
    }
}