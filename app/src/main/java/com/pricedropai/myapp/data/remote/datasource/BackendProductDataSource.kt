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
    private val fallbackDataSource: ProductDataSource? = null
) : ProductDataSource {

    companion object {
        private const val TAG = "BackendDataSource"
        const val BASE_URL = "https://pricedropai-backend.onrender.com"

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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

        val requestId = "and_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"

        try {
            val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.toString())
            val targetUrl = "$baseUrl/api/search?q=$encoded"

            val request = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "PriceDropAI-Android/1.3")
                .header("X-Request-Id", requestId)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val products = mutableListOf<Product>()
                val trimmed = responseBody.trim()
                if (trimmed.startsWith("[")) {
                    val jsonArray = JSONArray(trimmed)
                    for (i in 0 until jsonArray.length()) {
                        val itemObj = jsonArray.optJSONObject(i) ?: continue
                        val product = parseProductFromJson(itemObj)
                        if (product != null) products.add(product)
                    }
                } else if (trimmed.startsWith("{")) {
                    val jsonObj = JSONObject(trimmed)
                    val product = parseProductFromJson(jsonObj)
                    if (product != null) products.add(product)
                }

                if (products.isNotEmpty()) {
                    val top = products.first()
                    Log.d(TAG, "[Audit] [reqId: $requestId] Backend returned ${products.size} products for '$cleanQuery' | Top: '${top.title.take(40)}' | ASIN: ${top.asin ?: "N/A"} | Image: ${top.imageUrl ?: "null"}")
                    return@withContext Result.success(products)
                }
            } else {
                Log.w(TAG, "[Audit] [reqId: $requestId] Backend search returned status ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Audit] [reqId: $requestId] Backend search error: ${e.message}. Attempting on-device fallback.", e)
        }

        // Fallback to on-device scraper if configured, otherwise return error without faking data
        fallbackDataSource?.searchProducts(cleanQuery)
            ?: Result.failure(Exception("Live price lookup returned no results from backend"))
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

        fallbackDataSource?.getProduct(productId)
            ?: Result.failure(Exception("Product lookup for $productId returned no data from backend"))
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

        val sanitizedImageUrl: String? = when {
            rawImageStr == null -> null
            rawImageStr.startsWith("//") -> "https:$rawImageStr"
            rawImageStr.startsWith("http://") -> rawImageStr.replaceFirst("http://", "https://")
            rawImageStr.startsWith("https://") -> rawImageStr
            else -> null
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

                val rawPrice = if (storeObj.has("price") && !storeObj.isNull("price")) storeObj.optDouble("price") else null
                val origPrice = if (storeObj.has("originalPrice") && !storeObj.isNull("originalPrice")) storeObj.optDouble("originalPrice") else null
                val discountPct = if (storeObj.has("discountPercentage") && !storeObj.isNull("discountPercentage")) storeObj.optDouble("discountPercentage") else null
                val productUrl = storeObj.optString("productUrl")
                val availStr = storeObj.optString("availability", "IN_STOCK")
                val availEnum = try {
                    Availability.valueOf(availStr.uppercase())
                } catch (_: Exception) {
                    Availability.IN_STOCK
                }
                val rawDelivery = storeObj.optString("deliveryInfo").takeIf { it.isNotBlank() }

                val isUnavailable = rawPrice == null || rawPrice <= 0.0 ||
                        availEnum == Availability.OUT_OF_STOCK ||
                        rawDelivery?.contains("Unavailable", ignoreCase = true) == true

                val finalPrice = if (isUnavailable) 0.0 else rawPrice
                val finalOrigPrice = if (isUnavailable) 0.0 else origPrice
                val finalDiscount = if (isUnavailable) 0.0 else discountPct
                val finalAvail = if (isUnavailable) Availability.OUT_OF_STOCK else availEnum
                val finalDelivery = if (isUnavailable) "(Product Unavailable..)" else rawDelivery

                val offerRawImg = (
                    storeObj.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
                        ?: storeObj.optString("image").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
                )?.trim()
                val offerImg = when {
                    offerRawImg == null -> null
                    offerRawImg.startsWith("//") -> "https:$offerRawImg"
                    offerRawImg.startsWith("http://") -> offerRawImg.replaceFirst("http://", "https://")
                    offerRawImg.startsWith("https://") -> offerRawImg
                    else -> null
                }

                storesList.add(
                    StoreOffer(
                        id = storeObj.optString("id", "${id}_${storeEnum.name.lowercase()}"),
                        productId = id,
                        store = storeEnum,
                        productUrl = productUrl,
                        price = finalPrice,
                        originalPrice = finalOrigPrice,
                        discountPercentage = finalDiscount,
                        currency = storeObj.optString("currency", "INR"),
                        availability = finalAvail,
                        deliveryInfo = finalDelivery,
                        imageUrl = offerImg,
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
                        validUntil = if (payObj.has("validUntil") && !payObj.isNull("validUntil")) payObj.optLong("validUntil") else null,
                        displayName = payObj.optString("displayName").takeIf { it.isNotBlank() },
                        paymentMethod = payObj.optString("paymentMethod").takeIf { it.isNotBlank() },
                        cardType = payObj.optString("cardType").takeIf { it.isNotBlank() },
                        discountType = payObj.optString("discountType").takeIf { it.isNotBlank() },
                        discountValue = if (payObj.has("discountValue") && !payObj.isNull("discountValue")) payObj.optDouble("discountValue") else null,
                        calculatedDiscount = if (payObj.has("calculatedDiscount") && !payObj.isNull("calculatedDiscount")) payObj.optDouble("calculatedDiscount") else null,
                        payablePrice = if (payObj.has("payablePrice") && !payObj.isNull("payablePrice")) payObj.optDouble("payablePrice") else null,
                        isEligible = payObj.optBoolean("isEligible", true),
                        eligibilityStatus = payObj.optString("eligibilityStatus", "ELIGIBLE"),
                        ineligibilityReason = payObj.optString("ineligibilityReason").takeIf { it.isNotBlank() },
                        terms = payObj.optString("terms").takeIf { it.isNotBlank() },
                        source = payObj.optString("source").takeIf { it.isNotBlank() },
                        sourceUrl = payObj.optString("sourceUrl").takeIf { it.isNotBlank() },
                        lastVerifiedAt = if (payObj.has("lastVerifiedAt") && !payObj.isNull("lastVerifiedAt")) payObj.optLong("lastVerifiedAt") else null,
                        isCashback = payObj.optBoolean("isCashback", false),
                        emiEligible = payObj.optBoolean("emiEligible", false)
                    )
                )
            }
        }

        // Parse EMI Plans
        val emiPlansList = mutableListOf<EmiPlan>()
        val emiArray = json.optJSONArray("emiPlans")
        if (emiArray != null) {
            for (k in 0 until emiArray.length()) {
                val emiObj = emiArray.optJSONObject(k) ?: continue
                emiPlansList.add(
                    EmiPlan(
                        id = emiObj.optString("id", "emi_$k"),
                        provider = emiObj.optString("provider", "Bank EMI"),
                        displayName = emiObj.optString("displayName", "EMI Plan"),
                        tenureMonths = emiObj.optInt("tenureMonths", 3),
                        monthlyInstallment = emiObj.optDouble("monthlyInstallment", 0.0),
                        totalInstallmentAmount = emiObj.optDouble("totalInstallmentAmount", 0.0),
                        interestAmount = emiObj.optDouble("interestAmount", 0.0),
                        subventionDiscount = emiObj.optDouble("subventionDiscount", 0.0),
                        processingFee = emiObj.optDouble("processingFee", 0.0),
                        taxes = emiObj.optDouble("taxes", 0.0),
                        effectiveTotalCost = emiObj.optDouble("effectiveTotalCost", 0.0),
                        isNoCostEmi = emiObj.optBoolean("isNoCostEmi", true),
                        eligibilityStatus = emiObj.optString("eligibilityStatus", "ELIGIBLE"),
                        ineligibilityReason = emiObj.optString("ineligibilityReason").takeIf { it.isNotBlank() },
                        source = emiObj.optString("source").takeIf { it.isNotBlank() },
                        sourceUrl = emiObj.optString("sourceUrl").takeIf { it.isNotBlank() },
                        terms = emiObj.optString("terms").takeIf { it.isNotBlank() }
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

            // Rating Distribution
            val distObj = reviewSummaryObj.optJSONObject("ratingDistribution")
            val ratingDistribution = if (distObj != null) {
                fun parseItem(key: String): RatingDistributionItem? {
                    val item = distObj.optJSONObject(key) ?: return null
                    return RatingDistributionItem(
                        count = item.optInt("count", 0),
                        percentage = item.optDouble("percentage", 0.0)
                    )
                }
                RatingDistribution(
                    fiveStar = parseItem("fiveStar"),
                    fourStar = parseItem("fourStar"),
                    threeStar = parseItem("threeStar"),
                    twoStar = parseItem("twoStar"),
                    oneStar = parseItem("oneStar")
                )
            } else null

            // Feature Scores
            val featObj = reviewSummaryObj.optJSONObject("featureScores")
            val featureScores = if (featObj != null) {
                FeatureSentimentScores(
                    buildQuality = if (featObj.has("buildQuality") && !featObj.isNull("buildQuality")) featObj.optInt("buildQuality") else null,
                    valueForMoney = if (featObj.has("valueForMoney") && !featObj.isNull("valueForMoney")) featObj.optInt("valueForMoney") else null,
                    performance = if (featObj.has("performance") && !featObj.isNull("performance")) featObj.optInt("performance") else null,
                    durability = if (featObj.has("durability") && !featObj.isNull("durability")) featObj.optInt("durability") else null
                )
            } else null

            ReviewSummary(
                sentimentScore = if (reviewSummaryObj.has("sentimentScore") && !reviewSummaryObj.isNull("sentimentScore")) reviewSummaryObj.optInt("sentimentScore") else null,
                pros = prosList,
                cons = consList,
                totalAnalyzed = if (reviewSummaryObj.has("totalAnalyzed") && !reviewSummaryObj.isNull("totalAnalyzed")) reviewSummaryObj.optInt("totalAnalyzed") else null,
                isAvailable = reviewSummaryObj.optBoolean("isAvailable", false),
                ratingDistribution = ratingDistribution,
                featureScores = featureScores,
                confidenceScore = if (reviewSummaryObj.has("confidenceScore") && !reviewSummaryObj.isNull("confidenceScore")) reviewSummaryObj.optInt("confidenceScore") else null,
                evidenceCoverage = reviewSummaryObj.optString("evidenceCoverage").takeIf { it.isNotBlank() },
                dataFreshness = if (reviewSummaryObj.has("dataFreshness") && !reviewSummaryObj.isNull("dataFreshness")) reviewSummaryObj.optLong("dataFreshness") else null,
                analysisStatus = reviewSummaryObj.optString("analysisStatus").takeIf { it.isNotBlank() },
                sourceMetadata = reviewSummaryObj.optString("sourceMetadata").takeIf { it.isNotBlank() }
            )
        } else {
            ReviewSummary(isAvailable = false)
        }

        val resolvedProductImage = sanitizedImageUrl
            ?: storesList.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl

        val asin = json.optString("asin").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
        val searchQuery = json.optString("searchQuery").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }

        return Product(
            id = id,
            title = title,
            description = description,
            imageUrl = resolvedProductImage,
            asin = asin,
            searchQuery = searchQuery,
            category = category,
            brand = brand,
            rating = rating,
            reviewCount = reviewCount,
            variants = variantsList,
            stores = storesList,
            reviewSummary = reviewSummary,
            paymentOffers = paymentOffersList,
            emiPlans = emiPlansList,
            lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
        )
    }
}
