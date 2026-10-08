package com.example.pricedropai.data.remote.datasource

import android.util.Log
import com.example.pricedropai.core.model.Product
import com.example.pricedropai.core.model.PriceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiCopilotResponse(
    val replyText: String,
    val suggestedSearchQuery: String? = null,
    val isVerifiedFact: Boolean = true
)

class AiCopilotDataSource(
    private val baseUrl: String = BackendProductDataSource.BASE_URL,
    private val client: OkHttpClient = defaultHttpClient()
) {

    companion object {
        private const val TAG = "AiCopilotDataSource"

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    suspend fun generateResponse(
        userPrompt: String,
        currentProduct: Product? = null,
        priceHistory: List<PriceSnapshot> = emptyList()
    ): AiCopilotResponse = withContext(Dispatchers.IO) {
        val query = userPrompt.trim().lowercase()

        // 1. Try querying Gemini AI Copilot on the backend server
        try {
            val jsonPayload = JSONObject().apply {
                put("userPrompt", userPrompt)
                if (currentProduct != null) {
                    put("productTitle", currentProduct.title)
                    put("lowestPrice", currentProduct.lowestPrice)
                }
                put("priceHistoryCount", priceHistory.size)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/copilot")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonObj = JSONObject(responseBody)
                val replyText = jsonObj.optString("replyText")
                val suggestedSearch = jsonObj.optString("suggestedSearchQuery").takeIf { it.isNotBlank() }

                if (replyText.isNotBlank()) {
                    return@withContext AiCopilotResponse(
                        replyText = replyText,
                        suggestedSearchQuery = suggestedSearch,
                        isVerifiedFact = jsonObj.optBoolean("isVerifiedFact", true)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend Gemini Copilot unavailable, using local rules: ${e.message}")
        }

        // 2. Local intelligent fallback response if backend is offline
        if (currentProduct != null) {
            val lowestOffer = currentProduct.lowestOffer
            val lowestPrice = currentProduct.lowestPrice
            val storeCount = currentProduct.stores.size

            when {
                query.contains("cheapest") || query.contains("lowest") || query.contains("best price") || query.contains("which store") -> {
                    if (lowestOffer != null && lowestPrice != null) {
                        return@withContext AiCopilotResponse(
                            replyText = "Based on verified data, ${lowestOffer.store.displayName} has the lowest price for '${currentProduct.title}' at ₹${"%,.0f".format(lowestPrice)}. ($storeCount stores compared).",
                            suggestedSearchQuery = currentProduct.title
                        )
                    }
                }
                query.contains("history") || query.contains("trend") || query.contains("drop") -> {
                    if (priceHistory.size >= 2) {
                        val firstPrice = priceHistory.first().price
                        val latestPrice = priceHistory.last().price
                        val diff = latestPrice - firstPrice
                        val trendText = if (diff < 0) "dropped by ₹${"%,.0f".format(-diff)}" else "increased by ₹${"%,.0f".format(diff)}"
                        return@withContext AiCopilotResponse(
                            replyText = "Price for '${currentProduct.title}' has $trendText across ${priceHistory.size} recorded snapshots.",
                            suggestedSearchQuery = currentProduct.title
                        )
                    }
                }
                query.contains("should i buy") || query.contains("buy now") -> {
                    if (lowestPrice != null) {
                        return@withContext AiCopilotResponse(
                            replyText = "Current best verified price is ₹${"%,.0f".format(lowestPrice)} on ${lowestOffer?.store?.displayName}. Track this product in your Watchlist for price drop notifications.",
                            suggestedSearchQuery = currentProduct.title
                        )
                    }
                }
            }
        }

        AiCopilotResponse(
            replyText = "I analyze live pricing and price drop trends across Amazon, Flipkart, Meesho, and Myntra. Ask me about best deals, store comparisons, or target price alerts!",
            suggestedSearchQuery = userPrompt.takeIf { it.length in 3..40 }
        )
    }
}
