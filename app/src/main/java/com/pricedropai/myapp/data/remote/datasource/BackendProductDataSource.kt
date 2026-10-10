package com.pricedropai.myapp.data.remote.datasource

import android.util.Log
import com.pricedropai.myapp.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * High-performance remote data source that queries the PriceDropAI Node.js backend
 * hosted on Render (with automatic multi-store live scraping, proxy routing, and HD image resolution).
 */
class BackendProductDataSource(
    private val baseUrl: String = BASE_URL,
    private val client: OkHttpClient = defaultHttpClient(),
    private val fallbackDataSource: ProductDataSource = MultiStoreProductDataSource(client)
) : ProductDataSource {

    companion object {
        private const val TAG = "BackendDataSource"
        const val BASE_URL = "https://pricedropai-backend.onrender.com"

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.toString())
            val targetUrl = "$baseUrl/api/search?q=$encoded"

            val request = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "PriceDropAI-Android/1.3")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonArray = JSONArray(responseBody)
                val products = mutableListOf<Product>()

                for (i in 0 until jsonArray.length()) {
                    val itemObj = jsonArray.optJSONObject(i) ?: continue
                    val product = parseProductFromJson(itemObj)
                    if (product != null) {
                        products.add(product)
                    }
                }

                if (products.isNotEmpty()) {
                    return@withContext Result.success(products)
                }
            } else {
                Log.w(TAG, "Backend search returned status ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend search connection error: ${e.message}. Attempting on-device fallback.", e)
        }

        // Fallback to on-device scraper if backend is unreachable / starting up
        fallbackDataSource.searchProducts(cleanQuery)
    }

    override suspend fun getProduct(productId: String): Result<Product> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = "$baseUrl/api/product/$productId"
            val request = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonObj = JSONObject(responseBody)
                val product = parseProductFromJson(jsonObj)
                if (product != null) {
                    return@withContext Result.success(product)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend getProduct failed: ${e.message}")
        }

        fallbackDataSource.getProduct(productId)
    }

    override suspend fun getPrice(productId: String, store: Store): Result<PriceSnapshot> {
        val productResult = getProduct(productId)
        return productResult.mapCatching { product ->
            val offer = product.stores.firstOrNull { it.store == store && it.price != null }
                ?: throw NoSuchElementException("No verified price found for store ${store.displayName}")
            PriceSnapshot(
                productId = product.id,
                store = store,
                price = offer.price!!,
                timestamp = System.currentTimeMillis(),
                availability = offer.availability
            )
        }
    }

    override suspend fun getVariants(productId: String): Result<List<ProductVariant>> {
        val productResult = getProduct(productId)
        return productResult.mapCatching { it.variants }
    }

    private fun parseProductFromJson(json: JSONObject): Product? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null

        val category = json.optString("category").takeIf { it.isNotBlank() }
        
        val rawImageStr = (
            json.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
                ?: json.optString("image").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
                ?: json.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
        )?.trim()

        var sanitizedImageUrl: String? = when {
            rawImageStr == null -> null
            rawImageStr.startsWith("//") -> "https:$rawImageStr"
            rawImageStr.startsWith("http://") -> rawImageStr.replaceFirst("http://", "https://")
            rawImageStr.startsWith("https://") -> rawImageStr
            else -> null
        }

        if (sanitizedImageUrl.isNullOrBlank()) {
            sanitizedImageUrl = com.pricedropai.myapp.LocalPriceEngine.resolveFallbackImage(title)
        }
        val description = json.optString("description").takeIf { it.isNotBlank() }
        val brand = json.optString("brand").takeIf { it.isNotBlank() }
        val rating = if (json.has("rating") && !json.isNull("rating")) json.optDouble("rating") else null
        val reviewCount = if (json.has("reviewCount") && !json.isNull("reviewCount")) json.optInt("reviewCount") else null

        // Parse Store Offers
        val storesList = mutableListOf<StoreOffer>()
        val storesArray = json.optJSONArray("stores")
        if (storesArray != null) {
            for (i in 0 until storesArray.length()) {
                val storeObj = storesArray.optJSONObject(i) ?: continue
                val storeName = storeObj.optString("store")
                val storeEnum = try {
                    Store.valueOf(storeName.uppercase())
                } catch (_: Exception) {
                    continue
                }

                val price = if (storeObj.has("price") && !storeObj.isNull("price")) storeObj.optDouble("price") else null
                val origPrice = if (storeObj.has("originalPrice") && !storeObj.isNull("originalPrice")) storeObj.optDouble("originalPrice") else null
                val discountPct = if (storeObj.has("discountPercentage") && !storeObj.isNull("discountPercentage")) storeObj.optDouble("discountPercentage") else null
                val productUrl = storeObj.optString("productUrl")
                val availStr = storeObj.optString("availability", "IN_STOCK")
                val availEnum = try {
                    Availability.valueOf(availStr.uppercase())
                } catch (_: Exception) {
                    Availability.IN_STOCK
                }
                val deliveryInfo = storeObj.optString("deliveryInfo").takeIf { it.isNotBlank() }

                storesList.add(
                    StoreOffer(
                        id = storeObj.optString("id", "${id}_${storeEnum.name.lowercase()}"),
                        productId = id,
                        store = storeEnum,
                        productUrl = productUrl,
                        price = price,
                        originalPrice = origPrice,
                        discountPercentage = discountPct,
                        currency = storeObj.optString("currency", "INR"),
                        availability = availEnum,
                        deliveryInfo = deliveryInfo,
                        lastUpdated = storeObj.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
        }

        // Parse Variants
        val variantsList = mutableListOf<ProductVariant>()
        val variantsArray = json.optJSONArray("variants")
        if (variantsArray != null) {
            for (i in 0 until variantsArray.length()) {
                val varObj = variantsArray.optJSONObject(i) ?: continue
                variantsList.add(
                    ProductVariant(
                        id = varObj.optString("id", "${id}_var_$i"),
                        productId = id,
                        name = varObj.optString("name", "Option"),
                        type = varObj.optString("type", "Option"),
                        price = if (varObj.has("price") && !varObj.isNull("price")) varObj.optDouble("price") else null,
                        isAvailable = varObj.optBoolean("isAvailable", true)
                    )
                )
            }
        }

        // Parse Payment Offers
        val paymentOffersList = mutableListOf<PaymentOffer>()
        val paymentOffersArray = json.optJSONArray("paymentOffers")
        if (paymentOffersArray != null) {
            for (i in 0 until paymentOffersArray.length()) {
                val payObj = paymentOffersArray.optJSONObject(i) ?: continue
                val typeStr = payObj.optString("type", "INSTANT_DISCOUNT")
                val typeEnum = try {
                    PaymentOfferType.valueOf(typeStr.uppercase())
                } catch (_: Exception) {
                    PaymentOfferType.INSTANT_DISCOUNT
                }

                paymentOffersList.add(
                    PaymentOffer(
                        id = payObj.optString("id", "offer_$i"),
                        provider = payObj.optString("provider", "Bank Offer"),
                        type = typeEnum,
                        description = payObj.optString("description", ""),
                        amount = if (payObj.has("amount") && !payObj.isNull("amount")) payObj.optDouble("amount") else null,
                        percentage = if (payObj.has("percentage") && !payObj.isNull("percentage")) payObj.optDouble("percentage") else null,
                        minimumPurchase = if (payObj.has("minimumPurchase") && !payObj.isNull("minimumPurchase")) payObj.optDouble("minimumPurchase") else null,
                        maximumDiscount = if (payObj.has("maximumDiscount") && !payObj.isNull("maximumDiscount")) payObj.optDouble("maximumDiscount") else null,
                        validUntil = if (payObj.has("validUntil") && !payObj.isNull("validUntil")) payObj.optLong("validUntil") else null
                    )
                )
            }
        }

        // Parse Review Summary
        val reviewSummaryObj = json.optJSONObject("reviewSummary")
        val reviewSummary = if (reviewSummaryObj != null) {
            val prosList = mutableListOf<String>()
            val prosArr = reviewSummaryObj.optJSONArray("pros")
            if (prosArr != null) {
                for (j in 0 until prosArr.length()) prosList.add(prosArr.optString(j))
            }

            val consList = mutableListOf<String>()
            val consArr = reviewSummaryObj.optJSONArray("cons")
            if (consArr != null) {
                for (j in 0 until consArr.length()) consList.add(consArr.optString(j))
            }

            ReviewSummary(
                sentimentScore = if (reviewSummaryObj.has("sentimentScore") && !reviewSummaryObj.isNull("sentimentScore")) reviewSummaryObj.optInt("sentimentScore") else null,
                pros = prosList,
                cons = consList,
                totalAnalyzed = if (reviewSummaryObj.has("totalAnalyzed") && !reviewSummaryObj.isNull("totalAnalyzed")) reviewSummaryObj.optInt("totalAnalyzed") else null,
                isAvailable = reviewSummaryObj.optBoolean("isAvailable", false)
            )
        } else {
            ReviewSummary(isAvailable = false)
        }

        return Product(
            id = id,
            title = title,
            description = description,
            imageUrl = sanitizedImageUrl,
            category = category,
            brand = brand,
            rating = rating,
            reviewCount = reviewCount,
            variants = variantsList,
            stores = storesList,
            reviewSummary = reviewSummary,
            paymentOffers = paymentOffersList,
            lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
        )
    }
}
