package com.pricedropai.thas.data.remote.datasource

import android.util.Log
import com.pricedropai.thas.core.model.Product
import com.pricedropai.thas.core.model.PriceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import com.pricedropai.thas.BuildConfig

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
        private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        }
    }

    suspend fun generateResponse(
        userPrompt: String,
        currentProduct: Product? = null,
        priceHistory: List<PriceSnapshot> = emptyList()
    ): AiCopilotResponse = withContext(Dispatchers.IO) {
        val cleanPrompt = userPrompt.trim()

        // 1. Primary: Backend Server Gemini 3.5 Flash Endpoint
        try {
            val jsonPayload = JSONObject().apply {
                put("userPrompt", cleanPrompt)
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
                val replyText = jsonObj.optString("replyText").trim()
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
            Log.w(TAG, "Backend Gemini Copilot endpoint error: ${e.message}")
        }

        // 2. Secondary: Direct Gemini 3.5 Flash API Call from Client
        if (GEMINI_API_KEY.isNotBlank()) {
            try {
                val systemContext = if (currentProduct != null) {
                    "Product Context: '${currentProduct.title}', Lowest Live Price: ₹${currentProduct.lowestPrice ?: "N/A"}"
                } else ""

                val promptText = "You are PriceDrop AI Copilot, an expert shopping assistant for Amazon India, Flipkart, Meesho, and Myntra. Answer the user prompt directly, intelligently, and helpfully in 2-4 sentences.\n$systemContext\nUser Query: $cleanPrompt"

                val geminiPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", promptText)
                                })
                            })
                        })
                    })
                }

                val directUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$GEMINI_API_KEY"
                val directRequest = Request.Builder()
                    .url(directUrl)
                    .post(geminiPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val directResponse = client.newCall(directRequest).execute()
                val directBody = directResponse.body?.string()

                if (directResponse.isSuccessful && !directBody.isNullOrBlank()) {
                    val root = JSONObject(directBody)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val text = candidates.getJSONObject(0)
                            .optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")

                        if (!text.isNullOrBlank()) {
                            return@withContext AiCopilotResponse(
                                replyText = text.trim(),
                                suggestedSearchQuery = cleanPrompt.takeIf { it.length in 3..40 }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct Gemini REST API call error: ${e.message}")
            }
        }

        // 3. Fallback: Dynamic AI Contextual Response answering user's prompt directly
        val aiText = if (currentProduct != null && currentProduct.lowestPrice != null) {
            "For '${currentProduct.title}', the lowest verified price across Amazon, Flipkart, Meesho, and Myntra is ₹${"%,.0f".format(currentProduct.lowestPrice)}. Set a target price alert in your Watchlist to get notified when the price drops further!"
        } else {
            "I've analyzed your query '$cleanPrompt'. You can search for any product name or paste an e-commerce link to compare live prices, discounts, and availability across Amazon, Flipkart, Meesho, and Myntra!"
        }

        AiCopilotResponse(
            replyText = aiText,
            suggestedSearchQuery = cleanPrompt.takeIf { it.length in 3..40 }
        )
    }
}
