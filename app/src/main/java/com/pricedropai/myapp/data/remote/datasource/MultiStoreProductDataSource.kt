package com.pricedropai.myapp.data.remote.datasource

import android.util.Log
import com.pricedropai.myapp.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MultiStoreProductDataSource(
    private val client: OkHttpClient = defaultHttpClient()
) : ProductDataSource {

    companion object {
        private const val TAG = "MultiStoreDataSource"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try {
                Log.e(tag, msg, tr)
            } catch (_: Throwable) {
                System.err.println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
            }
        }

        private fun logD(tag: String, msg: String) {
            try {
                Log.d(tag, msg)
            } catch (_: Throwable) {
                // Safe for JVM execution
            }
        }
    }

    private data class ScrapedOfferResult(
        val offer: StoreOffer,
        val rating: Double? = null,
        val reviewCount: Int? = null,
        val imageUrl: String? = null
    )

    override suspend fun searchProducts(query: String): Result<List<Product>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            coroutineScope {
                val amazonDeferred = async { fetchAmazonLive(cleanQuery) }
                val flipkartDeferred = async { fetchFlipkartLive(cleanQuery) }
                val meeshoDeferred = async { fetchMeeshoLive(cleanQuery) }
                val myntraDeferred = async { fetchMyntraLive(cleanQuery) }

                val amazonScraped = amazonDeferred.await()
                val flipkartScraped = flipkartDeferred.await()
                val meeshoScraped = meeshoDeferred.await()
                val myntraScraped = myntraDeferred.await()

                val scrapedMap = mutableMapOf<Store, ScrapedOfferResult>()
                amazonScraped?.let { scrapedMap[Store.AMAZON] = it }
                flipkartScraped?.let { scrapedMap[Store.FLIPKART] = it }
                meeshoScraped?.let { scrapedMap[Store.MEESHO] = it }
                myntraScraped?.let { scrapedMap[Store.MYNTRA] = it }

                val validScraped = scrapedMap.values.filter { it.offer.price != null && it.offer.price > 10.0 }
                val productId = cleanQuery.lowercase().replace("[^a-z0-9]+".toRegex(), "-").take(60)

                // 1. Establish Title directly from scraped results (Brand-Aware)
                val queryBrand = detectBrand(cleanQuery)
                val primaryScraped = if (queryBrand != null) {
                    val qbLower = queryBrand.lowercase()
                    validScraped.firstOrNull {
                        it.offer.deliveryInfo?.lowercase()?.contains(qbLower) == true
                    } ?: validScraped.firstOrNull {
                        val dInfo = it.offer.deliveryInfo?.lowercase() ?: ""
                        val itemBrand = detectBrand(dInfo)
                        itemBrand == null || itemBrand.equals(queryBrand, ignoreCase = true)
                    }
                } else {
                    validScraped.firstOrNull { !it.offer.deliveryInfo.isNullOrBlank() }
                }

                val title = if (queryBrand != null && (primaryScraped == null || primaryScraped.offer.deliveryInfo?.lowercase()?.contains(queryBrand.lowercase()) != true)) {
                    // Do NOT adopt a competitor brand title if scraped result is for a different brand
                    cleanQuery
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                } else {
                    (primaryScraped?.offer?.deliveryInfo?.takeIf { it.isNotBlank() } ?: cleanQuery)
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                }

                // 2. Restrict to strictly the 4 active marketplaces
                val targetStores = Store.ACTIVE_STORES
                val detectedCategory = detectCategory(cleanQuery)
                val basePriceEstimate = when {
                    cleanQuery.contains("macbook", ignoreCase = true) -> 82990.0
                    cleanQuery.contains("iphone", ignoreCase = true) -> 64990.0
                    cleanQuery.contains("laptop", ignoreCase = true) -> 49990.0
                    cleanQuery.contains("samsung s", ignoreCase = true) -> 74990.0
                    cleanQuery.contains("tv", ignoreCase = true) || cleanQuery.contains("television", ignoreCase = true) -> 28990.0
                    cleanQuery.contains("headphone", ignoreCase = true) || cleanQuery.contains("sony wh", ignoreCase = true) -> 14990.0
                    cleanQuery.contains("chair", ignoreCase = true) -> 4999.0
                    cleanQuery.contains("shoe", ignoreCase = true) || cleanQuery.contains("sneaker", ignoreCase = true) -> 3499.0
                    cleanQuery.contains("watch", ignoreCase = true) -> 2999.0
                    cleanQuery.contains("bottle", ignoreCase = true) -> 699.0
                    else -> 1999.0
                }
                val primaryPrice = validScraped.firstOrNull()?.offer?.price ?: basePriceEstimate
                val encoded = try { URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.toString()) } catch (_: Exception) { cleanQuery }

                // 3. Resolve Dynamic Live Product Image strictly from Amazon product
                val amazonImageCandidate = amazonScraped?.imageUrl?.takeIf { isValidImageUrl(it) }
                val dynamicImage = amazonImageCandidate
                    ?: resolveDynamicProductImage(query = "$cleanQuery amazon", scrapedImageUrl = null)

                val allStoreOffers = targetStores.map { store ->
                    val isSupported = isProductCategorySupportedByStore(store, cleanQuery, detectedCategory)
                    val existingScraped = if (isSupported) validScraped.firstOrNull { it.offer.store == store } else null

                    val hyphenatedQuery = cleanQuery.replace("\\s+".toRegex(), "-").lowercase()
                    val storeUrl = when (store) {
                        Store.AMAZON -> "https://www.amazon.in/s?k=$encoded"
                        Store.FLIPKART -> "https://www.flipkart.com/search?q=$encoded"
                        Store.MEESHO -> "https://www.meesho.com/search?q=$encoded&searchType=manual&searchIdentifier=text_search"
                        Store.MYNTRA -> "https://www.myntra.com/$hyphenatedQuery"
                        else -> ""
                    }

                    if (existingScraped != null) {
                        existingScraped.offer.copy(
                            id = "${productId}_${store.name.lowercase()}",
                            productId = productId,
                            imageUrl = existingScraped.imageUrl ?: dynamicImage,
                            searchUrl = storeUrl,
                            isDirectProductUrl = true,
                            status = MarketplaceStatus.AVAILABLE
                        )
                    } else if (!isSupported) {
                        // Store does NOT carry this product or category: Honest UNSUPPORTED_CATEGORY state
                        StoreOffer(
                            id = "${productId}_${store.name.lowercase()}",
                            productId = productId,
                            store = store,
                            productUrl = storeUrl,
                            searchUrl = storeUrl,
                            isDirectProductUrl = false,
                            status = MarketplaceStatus.UNSUPPORTED_CATEGORY,
                            price = 0.0,
                            originalPrice = 0.0,
                            discountPercentage = 0.0,
                            currency = "INR",
                            availability = Availability.OUT_OF_STOCK,
                            deliveryInfo = "(Product Unavailable..)",
                            imageUrl = dynamicImage,
                            lastUpdated = System.currentTimeMillis()
                        )
                    } else {
                        // Store supports category but no relevant matching product found: Honest NO_RESULTS state
                        StoreOffer(
                            id = "${productId}_${store.name.lowercase()}",
                            productId = productId,
                            store = store,
                            productUrl = storeUrl,
                            searchUrl = storeUrl,
                            isDirectProductUrl = false,
                            status = MarketplaceStatus.NO_RESULTS,
                            price = 0.0,
                            originalPrice = 0.0,
                            discountPercentage = 0.0,
                            currency = "INR",
                            availability = Availability.OUT_OF_STOCK,
                            deliveryInfo = "(Product Unavailable..)",
                            imageUrl = dynamicImage,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                }

                // 4. Live Scraped Ratings & Reviews
                val verifiedRating = validScraped.mapNotNull { it.rating }.firstOrNull()
                val verifiedReviewCount = validScraped.mapNotNull { it.reviewCount }.firstOrNull()

                if (allStoreOffers.isEmpty()) {
                    return@coroutineScope Result.failure(NoSuchElementException("No live offers found for '$cleanQuery'"))
                }

                val product = Product(
                    id = productId,
                    title = title,
                    description = "Live verified pricing across online retail stores.",
                    imageUrl = dynamicImage,
                    category = detectCategory(cleanQuery),
                    brand = detectBrand(cleanQuery),
                    rating = verifiedRating,
                    reviewCount = verifiedReviewCount,
                    variants = emptyList(),
                    stores = allStoreOffers,
                    reviewSummary = ReviewSummary(isAvailable = false),
                    paymentOffers = emptyList(),
                    lastUpdated = System.currentTimeMillis()
                )

                Result.success(listOf(product))
            }
        } catch (e: Exception) {
            logE(TAG, "Search error: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    override suspend fun getProduct(productId: String): Result<Product> {
        val searchResult = searchProducts(productId.replace("-", " "))
        return searchResult.mapCatching { list ->
            list.firstOrNull() ?: throw NoSuchElementException("Product with id '$productId' not found")
        }
    }

    override suspend fun getPrice(productId: String, store: Store): Result<PriceSnapshot> {
        val searchResult = searchProducts(productId.replace("-", " "))
        return searchResult.mapCatching { list ->
            val product = list.firstOrNull() ?: throw NoSuchElementException("Product '$productId' not found")
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
        return Result.success(emptyList())
    }

    // --- DYNAMIC IMAGE RESOLUTION VIA LIVE WEB SEARCH ENGINES ---

    fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (lower.contains("s?k=") || (lower.contains("/dp/") && !lower.contains("/images/")) || lower.contains("/search?q=")) return false
        if (lower.contains("grey-pixel") || lower.contains("transparent-pixel") || lower.contains("1x1") || lower.contains("blank.gif")) return false
        val isImageExtension = lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains(".svg") || lower.contains(".gif") || lower.contains(".avif")
        val isImageHost = lower.contains("media-amazon") || lower.contains("images-amazon") || lower.contains("ssl-images-amazon") ||
                lower.contains("flixcart") || lower.contains("meesho") || lower.contains("myntassets") || lower.contains("myntra") ||
                lower.contains("unsplash") || lower.contains("wikimedia") || lower.contains("wikipedia") ||
                lower.contains("duckduckgo") || lower.contains("bing") || lower.contains("googleusercontent") ||
                lower.contains("openlibrary") || lower.contains("cloudfront") || lower.contains("cdn")
        return isImageExtension || isImageHost
    }

    fun optimizeProductImageUrl(url: String?): String? {
        if (url.isNullOrBlank() || !isValidImageUrl(url)) return null
        var optimized = url.trim()
        if (optimized.startsWith("http://")) {
            optimized = optimized.replaceFirst("http://", "https://")
        }
        if (optimized.contains("media-amazon.com") || optimized.contains("images-amazon.com")) {
            optimized = optimized.replace(Regex("\\._[A-Za-z0-9_,-]+_\\.jpg", RegexOption.IGNORE_CASE), "._AC_SL1000_.jpg")
            optimized = optimized.replace(Regex("\\._AC_[A-Za-z0-9_,-]+_\\.jpg", RegexOption.IGNORE_CASE), "._AC_SL1000_.jpg")
        } else if (optimized.contains("flixcart.com")) {
            optimized = optimized.replace(Regex("/image/[0-9]+/[0-9]+/", RegexOption.IGNORE_CASE), "/image/832/832/")
        } else if (optimized.contains("meesho.com") || optimized.contains("meeshosupply.com")) {
            optimized = optimized.replace(Regex("/(128|256)/", RegexOption.IGNORE_CASE), "/512/")
        }
        return optimized
    }

    fun resolveDynamicProductImage(query: String, scrapedImageUrl: String? = null): String? {
        // 1. Prioritize direct scraped product image from store listing
        if (!scrapedImageUrl.isNullOrBlank() && isValidImageUrl(scrapedImageUrl)) {
            return optimizeProductImageUrl(scrapedImageUrl) ?: scrapedImageUrl
        }

        // 2. Fallback to live web search image engines if no scraped image is available
        val webImage = fetchDynamicImageFromWeb(query)
        if (!webImage.isNullOrBlank() && isValidImageUrl(webImage)) {
            return optimizeProductImageUrl(webImage) ?: webImage
        }

        // 3. Guaranteed high-resolution category product image fallback
        val q = query.lowercase().trim()
        return when {
            q.contains("shoe") || q.contains("sneaker") || q.contains("footwear") ->
                "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&auto=format&fit=crop&q=80"
            q.contains("phone") || q.contains("iphone") || q.contains("mobile") || q.contains("samsung") ->
                "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=600&auto=format&fit=crop&q=80"
            q.contains("laptop") || q.contains("macbook") || q.contains("computer") ->
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&auto=format&fit=crop&q=80"
            q.contains("headphone") || q.contains("earbud") || q.contains("audio") || q.contains("speaker") ->
                "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&auto=format&fit=crop&q=80"
            q.contains("watch") || q.contains("smartwatch") ->
                "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&auto=format&fit=crop&q=80"
            q.contains("bottle") || q.contains("flask") ->
                "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&auto=format&fit=crop&q=80"
            q.contains("bag") || q.contains("backpack") || q.contains("luggage") ->
                "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&auto=format&fit=crop&q=80"
            q.contains("chair") || q.contains("table") || q.contains("furniture") ->
                "https://images.unsplash.com/photo-1580481077195-c3a821a58875?w=600&auto=format&fit=crop&q=80"
            else ->
                "https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&auto=format&fit=crop&q=80"
        }
    }

    private fun fetchDynamicImageFromWeb(query: String): String? {
        val encoded = try {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            return null
        }

        // Tier 1: Bing Live Image Engine (Direct High-Resolution Web Product Scraper)
        try {
            val bingUrl = "https://www.bing.com/images/search?q=$encoded&FORM=HDRSC2"
            val request = Request.Builder()
                .url(bingUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en-US;q=0.9,en;q=0.8")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                response.close()
                val regex = Regex("""murl&quot;:&quot;(https?:[^&]+)&quot;""")
                val match = regex.find(body)
                if (match != null) {
                    val candidate = match.groupValues[1]
                    if (isValidImageUrl(candidate) && !candidate.contains("placeholder")) {
                        return candidate
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            logD(TAG, "Bing image fetch notice: ${e.message}")
        }

        // Tier 2: DuckDuckGo Live Search API (Checks direct Image only if not an encyclopedia/static image)
        try {
            val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_redirect=1&no_html=1"
            val request = Request.Builder().url(ddgUrl).header("User-Agent", USER_AGENT).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                response.close()
                if (body.isNotEmpty()) {
                    val json = JSONObject(body)
                    var img = json.optString("Image")
                    if (img.isNotBlank() && !img.contains("wikipedia")) {
                        if (img.startsWith("/")) img = "https://duckduckgo.com$img"
                        if (isValidImageUrl(img)) return img
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            logD(TAG, "DDG image fetch notice: ${e.message}")
        }

        // Strictly avoid Wikipedia/Wikimedia or random static picture lookups
        // Return null if real product image could not be verified

        // Tier 4: OpenLibrary API for Books
        if (query.lowercase().contains("book") || query.lowercase().contains("novel") || query.lowercase().contains("author")) {
            try {
                val openLibUrl = "https://openlibrary.org/search.json?q=$encoded&limit=1"
                val request = Request.Builder().url(openLibUrl).header("User-Agent", USER_AGENT).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    response.close()
                    val json = JSONObject(body)
                    val docs = json.optJSONArray("docs")
                    if (docs != null && docs.length() > 0) {
                        val firstDoc = docs.optJSONObject(0)
                        val coverI = firstDoc?.optInt("cover_i", -1) ?: -1
                        if (coverI > 0) {
                            val coverUrl = "https://covers.openlibrary.org/b/id/$coverI-L.jpg"
                            if (isValidImageUrl(coverUrl)) return coverUrl
                        }
                    }
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                logD(TAG, "OpenLibrary fetch notice: ${e.message}")
            }
        }

        return null
    }

    // --- STORE IMPLEMENTATIONS (Direct Live Web Scraping) ---

    private fun fetchAmazonLive(query: String): ScrapedOfferResult? {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val targetUrl = "https://www.amazon.in/s?k=$encoded"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en-US;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)

            val queryBrand = detectBrand(query)?.lowercase()
            val cards = doc.select("div[data-component-type=\"s-search-result\"]")

            var bestOffer: ScrapedOfferResult? = null

            for (card in cards) {
                val isCardSponsored = card.select(".puis-sponsored-label-text, .s-sponsored-label-info-icon, .s-label-popover-default, .puis-label-popover-default").isNotEmpty()
                    || card.hasClass("AdHolder")
                    || card.select("span:contains(Sponsored)").isNotEmpty()

                val priceWhole = card.select("span.a-price-whole").firstOrNull()?.text()
                    ?.replace("[^0-9]".toRegex(), "")?.toDoubleOrNull() ?: continue
                if (priceWhole <= 10.0) continue

                val linkEl = card.select("h2 a, a.a-link-normal.s-no-outline").firstOrNull()
                val titleEl = card.select("h2 span, span.a-size-medium, span.a-size-base-plus").firstOrNull()
                val titleText = titleEl?.text() ?: query
                val titleLower = titleText.lowercase()

                val cardBrand = detectBrand(titleLower)
                val matchesQueryBrand = queryBrand != null && (titleLower.contains(queryBrand) || cardBrand?.lowercase() == queryBrand)
                val isCompetitorBrand = queryBrand != null && cardBrand != null && cardBrand.lowercase() != queryBrand

                // Strictly skip competitor brand ads/cards when user explicitly requested a brand
                if (isCompetitorBrand) {
                    continue
                }

                val originalPrice = card.select("span.a-price.a-text-price span.a-offscreen").firstOrNull()?.text()
                    ?.replace("[^0-9]".toRegex(), "")?.toDoubleOrNull()
                val relHref = linkEl?.attr("href") ?: ""
                val productUrl = if (relHref.startsWith("http")) relHref else "https://www.amazon.in$relHref"

                val imgEl = card.select("img.s-image, img.a-dynamic-image, img[data-image-latency]").firstOrNull()
                val rawImg = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
                    ?: imgEl?.attr("srcset")?.split(" ")?.firstOrNull { it.startsWith("http") }
                val imageUrl = rawImg?.takeIf { isValidImageUrl(it) }

                val ratingText = card.select("span.a-icon-alt, i.a-icon-star-small span").firstOrNull()?.text()
                val reviewCountText = card.select("span.a-size-base.s-underline-text, span.s-underline-text").firstOrNull()?.text()
                val rating = ratingText?.split(" ")?.firstOrNull()?.toDoubleOrNull()
                val reviewCount = reviewCountText?.replace("[^0-9]".toRegex(), "")?.toIntOrNull()
                val discountPercent = if (originalPrice != null && originalPrice > priceWhole) {
                    ((originalPrice - priceWhole) / originalPrice * 100.0)
                } else null

                val offer = StoreOffer(
                    id = "amazon_${query.hashCode().toUInt().toString(16)}",
                    productId = "",
                    store = Store.AMAZON,
                    productUrl = productUrl,
                    price = priceWhole,
                    originalPrice = originalPrice,
                    discountPercentage = discountPercent,
                    availability = Availability.IN_STOCK,
                    deliveryInfo = titleText,
                    lastUpdated = System.currentTimeMillis()
                )

                val candidate = ScrapedOfferResult(
                    offer = offer,
                    rating = rating,
                    reviewCount = reviewCount,
                    imageUrl = imageUrl
                )

                // 1. Best: Requested brand match AND organic (not sponsored)
                if (matchesQueryBrand && !isCardSponsored) {
                    return candidate
                }

                // 2. Second best: Requested brand match even if sponsored
                if (matchesQueryBrand && bestOffer == null) {
                    bestOffer = candidate
                }

                // 3. Fallback when query has no specific brand: first organic result
                if (queryBrand == null && !isCardSponsored && bestOffer == null) {
                    bestOffer = candidate
                }
            }

            bestOffer
        } catch (e: Exception) {
            logD(TAG, "Amazon fetch notice: ${e.message}")
            null
        }
    }

    private fun fetchFlipkartLive(query: String): ScrapedOfferResult? {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val targetUrl = "https://www.flipkart.com/search?q=$encoded"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)

            val priceEl = doc.select("div.Nx9bqj, div._30jeq3, div.hl05eU div._25b18c div").firstOrNull()
            val origPriceEl = doc.select("div.yRaY8j, div._3I9_wc").firstOrNull()
            val discountEl = doc.select("div.UkUFwK, div._3Ay6Sb").firstOrNull()
            val titleEl = doc.select("div.KzDlHZ, div._4rR01T, a.s1Q9rs, a.wjcEIp").firstOrNull()
            val linkEl = doc.select("a.CGtC5Q, a._1fQZEK, a.s1Q9rs, a.VJA3rP, a[href*=\"/p/\"]").firstOrNull()
            val imgEl = doc.select("img._53qgcR, img.DByuf4, img._396cs4, img[src*=\"flixcart\"]").firstOrNull()
            val ratingEl = doc.select("div.XQDdHH, div._3LWZlK").firstOrNull()
            val reviewCountEl = doc.select("span.WJhBDe, span._2_R_DZ").firstOrNull()

            if (priceEl != null) {
                val rawPrice = priceEl.text().replace("[^0-9]".toRegex(), "").toDoubleOrNull()
                val origPrice = origPriceEl?.text()?.replace("[^0-9]".toRegex(), "")?.toDoubleOrNull()
                val discountPct = discountEl?.text()?.replace("[^0-9]".toRegex(), "")?.toDoubleOrNull()

                if (rawPrice != null && rawPrice > 10.0) {
                    val relHref = linkEl?.attr("href") ?: ""
                    val finalUrl = if (relHref.startsWith("http")) relHref else "https://www.flipkart.com$relHref"
                    val title = titleEl?.text() ?: query
                    val rawImg = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                        ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
                    val imageUrl = rawImg?.takeIf { isValidImageUrl(it) }

                    val rating = ratingEl?.text()?.toDoubleOrNull()
                    val reviewCount = reviewCountEl?.text()?.split(" ")?.firstOrNull()?.replace("[^0-9]".toRegex(), "")?.toIntOrNull()

                    val offer = StoreOffer(
                        id = "flipkart_${query.hashCode().toUInt().toString(16)}",
                        productId = "",
                        store = Store.FLIPKART,
                        productUrl = finalUrl,
                        price = rawPrice,
                        originalPrice = origPrice,
                        discountPercentage = discountPct,
                        availability = Availability.IN_STOCK,
                        deliveryInfo = title,
                        lastUpdated = System.currentTimeMillis()
                    )

                    return ScrapedOfferResult(
                        offer = offer,
                        rating = rating,
                        reviewCount = reviewCount,
                        imageUrl = imageUrl
                    )
                }
            }
            null
        } catch (e: Exception) {
            logD(TAG, "Flipkart fetch notice: ${e.message}")
            null
        }
    }

    private fun fetchMeeshoLive(query: String): ScrapedOfferResult? {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val targetUrl = "https://www.meesho.com/search?q=$encoded"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)

            val priceEl = doc.select("h5:contains(₹), span:contains(₹), p:contains(₹)").firstOrNull()
            if (priceEl != null) {
                val rawPrice = priceEl.text().replace("[^0-9]".toRegex(), "").toDoubleOrNull()
                if (rawPrice != null && rawPrice > 10.0) {
                    val linkEl = doc.select("a[href*=\"/p/\"]").firstOrNull()
                    val relHref = linkEl?.attr("href") ?: ""
                    val finalUrl = if (relHref.startsWith("http")) relHref else "https://www.meesho.com$relHref"
                    val imgEl = doc.select("img[src*=\"meesho\"], img[alt*=\"$query\"]").firstOrNull()
                    val rawImg = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    val imageUrl = rawImg?.takeIf { isValidImageUrl(it) }

                    // Verify title relevance: if query is for electronics/phone and result has no matching keywords, do not use it
                    val candidateTitle = imgEl?.attr("alt")?.takeIf { it.isNotBlank() } ?: query
                    val queryKeywords = query.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }
                    val hasKeywordMatch = queryKeywords.isEmpty() || queryKeywords.any { candidateTitle.lowercase().contains(it) }

                    if (!hasKeywordMatch) {
                        return null
                    }

                    val offer = StoreOffer(
                        id = "meesho_${query.hashCode().toUInt().toString(16)}",
                        productId = "",
                        store = Store.MEESHO,
                        productUrl = if (finalUrl.length > 25) finalUrl else targetUrl,
                        price = rawPrice,
                        originalPrice = null,
                        discountPercentage = null,
                        availability = Availability.IN_STOCK,
                        deliveryInfo = candidateTitle,
                        lastUpdated = System.currentTimeMillis()
                    )

                    return ScrapedOfferResult(
                        offer = offer,
                        imageUrl = imageUrl
                    )
                }
            }
            null
        } catch (e: Exception) {
            logD(TAG, "Meesho fetch notice: ${e.message}")
            null
        }
    }

    private fun fetchMyntraLive(query: String): ScrapedOfferResult? {
        return try {
            val hyphenated = query.trim().replace("\\s+".toRegex(), "-").lowercase()
            val targetUrl = "https://www.myntra.com/$hyphenated"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)

            val priceEl = doc.select("span.product-discountedPrice, span.product-price").firstOrNull()
            val origPriceEl = doc.select("span.product-strike").firstOrNull()
            val titleEl = doc.select("h3.product-brand, h4.product-product").firstOrNull()
            val linkEl = doc.select("li.product-base a").firstOrNull()
            val imgEl = doc.select("picture.img-responsive img, img.product-image").firstOrNull()

            if (priceEl != null) {
                val rawPrice = priceEl.text().replace("[^0-9]".toRegex(), "").toDoubleOrNull()
                val origPrice = origPriceEl?.text()?.replace("[^0-9]".toRegex(), "")?.toDoubleOrNull()

                if (rawPrice != null && rawPrice > 10.0) {
                    val candidateTitle = titleEl?.text() ?: query
                    // Check keyword relevance: drop if unrelated fashion items returned for non-apparel searches
                    val queryKeywords = query.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }
                    val hasKeywordMatch = queryKeywords.isEmpty() || queryKeywords.any { candidateTitle.lowercase().contains(it) }
                    if (!hasKeywordMatch) {
                        return null
                    }

                    val relHref = linkEl?.attr("href") ?: ""
                    val finalUrl = if (relHref.startsWith("http")) relHref else "https://www.myntra.com/$relHref"
                    val rawImg = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    val imageUrl = rawImg?.takeIf { isValidImageUrl(it) }

                    val offer = StoreOffer(
                        id = "myntra_${query.hashCode().toUInt().toString(16)}",
                        productId = "",
                        store = Store.MYNTRA,
                        productUrl = if (finalUrl.length > 25) finalUrl else targetUrl,
                        price = rawPrice,
                        originalPrice = origPrice,
                        discountPercentage = if (origPrice != null && origPrice > rawPrice) ((origPrice - rawPrice) / origPrice * 100.0) else null,
                        availability = Availability.IN_STOCK,
                        deliveryInfo = candidateTitle,
                        lastUpdated = System.currentTimeMillis()
                    )

                    return ScrapedOfferResult(
                        offer = offer,
                        imageUrl = imageUrl
                    )
                }
            }
            null
        } catch (e: Exception) {
            logD(TAG, "Myntra fetch notice: ${e.message}")
            null
        }
    }

    private fun detectCategory(q: String): String {
        val lower = q.lowercase()
        return when {
            // Sports & Fitness prioritized before Furniture so "adjustable" does not match "table"
            lower.contains("gripper") || lower.contains("wrist") || lower.contains("forearm") || lower.contains("finger exerciser") ||
                lower.contains("cycle") || lower.contains("bicycle") || lower.contains("gym") || lower.contains("dumbbell") ||
                lower.contains("barbell") || lower.contains("kettlebell") || lower.contains("resistance band") || lower.contains("fitness") -> "Sports & Fitness"
            lower.contains("phone") || lower.contains("iphone") || lower.contains("samsung") || lower.contains("pixel") || lower.contains("redmi") || lower.contains("oneplus") -> "Mobiles"
            lower.contains("laptop") || lower.contains("macbook") || lower.contains("computer") || lower.contains("pc") || lower.contains("dell") || lower.contains("hp") -> "Computers"
            lower.contains("tablet") || lower.contains("ipad") -> "Tablets"
            lower.contains("tv") || lower.contains("television") || lower.contains("soundbar") || lower.contains("speaker") || lower.contains("audio") || lower.contains("headphone") || lower.contains("earbud") -> "Audio & Video"
            lower.contains("shoe") || lower.contains("sneaker") || lower.contains("boot") || lower.contains("sandal") || lower.contains("heel") || lower.contains("slipper") -> "Footwear"
            lower.contains("shirt") || lower.contains("t-shirt") || lower.contains("pant") || lower.contains("jeans") || lower.contains("saree") || lower.contains("kurti") || lower.contains("dress") || lower.contains("jacket") -> "Fashion"
            lower.contains("perfume") || lower.contains("fragrance") || lower.contains("shampoo") || lower.contains("skincare") || lower.contains("cream") || lower.contains("makeup") || lower.contains("serum") -> "Beauty & Personal Care"
            lower.contains("bottle") || lower.contains("flask") || lower.contains("cooker") || lower.contains("kettle") || lower.contains("pan") || lower.contains("induction") || lower.contains("blender") -> "Kitchen"
            lower.contains("chair") || (lower.contains("table") && !lower.contains("adjustable")) || lower.contains("desk") || lower.contains("sofa") || lower.contains("bed") || lower.contains("lamp") -> "Furniture & Home"
            lower.contains("bag") || lower.contains("backpack") || lower.contains("luggage") || lower.contains("suitcase") || lower.contains("wallet") -> "Bags & Luggage"
            lower.contains("watch") || lower.contains("smartwatch") || lower.contains("fitbit") -> "Wearables"
            lower.contains("book") || lower.contains("novel") -> "Books"
            lower.contains("toy") || lower.contains("game") || lower.contains("puzzle") -> "Toys & Games"
            else -> "General E-Commerce"
        }
    }

    private fun detectBrand(q: String): String? {
        val lower = q.lowercase()
        val brands = listOf(
            // Food, Flours, Grains & Groceries
            "aashirvaad", "fortune", "pillsbury", "nature fresh", "shakti bhog", "rajdhani",
            "patanjali", "tata sampann", "tata", "daawat", "india gate", "kohinoor",
            "slurrp farm", "true elements", "organic tattva", "24 mantra", "catch", "everest",
            "mdh", "badshah", "suhana", "saffola", "dhara", "amul", "mother dairy",
            "milky mist", "nestle", "britannia", "sunfeast", "parle", "cadbury", "haldiram",
            "bikaji", "bikano", "balaji", "lays", "kurkure", "bingo", "quaker", "kellogg",
            "kissan", "maggi", "horlicks", "boost", "complan", "pediasure", "ensure",
            "protinex", "dabur", "tropicana", "paper boat", "bournvita",
            // Tech & Electronics
            "apple", "iphone", "ipad", "macbook", "airpods", "samsung", "sony", "google", "pixel",
            "oneplus", "asus", "dell", "hp", "lenovo", "acer", "redmi", "realme", "xiaomi", "poco",
            "motorola", "iqoo", "vivo", "oppo", "nothing", "infinix", "tecno", "logitech",
            // Audio & Wearables
            "boat", "noise", "boult", "fire-boltt", "zebronics", "jbl", "bose", "sennheiser",
            "fastrack", "titan", "casio", "timex", "fossil", "amazfit",
            // Home, Kitchen, Appliances
            "milton", "pexpo", "prestige", "hawkins", "pigeon", "cello", "borosil", "philips", "bajaj", "havells", "crompton", "voltas", "godrej", "whirlpool", "lg", "ifb", "bosch",
            // Footwear & Fashion
            "nike", "adidas", "puma", "reebok", "skechers", "asics", "woodland", "bata", "sparx", "campus", "crocs", "levis", "zara", "h&m", "allen solly", "van heusen",
            // Personal Care & Beauty
            "mamaearth", "lakme", "nivea", "dove", "garnier", "loreal", "maybelline", "plum", "biotique", "himalaya", "dettol",
            // Bags & Travel
            "wildcraft", "safari", "american tourister", "skybags", "vip", "mokobara"
        )
        return brands.firstOrNull { lower.contains(it) }?.split(" ")?.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /**
     * Determines whether an online store platform carries the searched product category in its catalog.
     * When false, the platform returns price 0.0 with "(Product Unavailable..)".
     */
    fun isProductCategorySupportedByStore(store: Store, query: String, category: String): Boolean {
        val q = query.lowercase()
        val cat = category.lowercase()

        val isSportsOrFitness = cat.contains("sports") || cat.contains("fitness") ||
                q.contains("gripper") || q.contains("hand gripper") || q.contains("wrist") || q.contains("forearm") ||
                q.contains("gym") || q.contains("dumbbell") || q.contains("barbell") || q.contains("kettlebell") ||
                q.contains("resistance band") || q.contains("treadmill") || q.contains("exercise bike")

        val isTechOrElectronics = cat.contains("mobile") || cat.contains("computer") || cat.contains("tablet") ||
                cat.contains("tv") || cat.contains("audio") || cat.contains("video") ||
                q.contains("iphone") || q.contains("macbook") || q.contains("laptop") || q.contains("phone") ||
                q.contains("television") || q.contains("samsung galaxy") || q.contains("refrigerator") ||
                q.contains("washing machine") || q.contains("air conditioner") || q.contains("ac") ||
                q.contains("microwave") || q.contains("desktop") || q.contains("monitor") || q.contains("ipad")

        val isFashionOrFootwear = cat.contains("fashion") || cat.contains("footwear") ||
                q.contains("shirt") || q.contains("t-shirt") || q.contains("pant") || q.contains("jeans") ||
                q.contains("saree") || q.contains("kurti") || q.contains("dress") || q.contains("jacket") ||
                q.contains("shoe") || q.contains("sneaker") || q.contains("boot") || q.contains("sandal") ||
                q.contains("heel") || q.contains("slipper") || q.contains("lehenga") || q.contains("kurta")

        val isBeautyOrCosmetics = cat.contains("beauty") || cat.contains("personal care") ||
                q.contains("lipstick") || q.contains("makeup") || q.contains("skincare") || q.contains("serum") ||
                q.contains("sunscreen") || q.contains("moisturizer") || q.contains("shampoo") || q.contains("perfume") ||
                q.contains("fragrance") || q.contains("foundation") || q.contains("eyeliner") || q.contains("cream")

        val isGrocery = cat.contains("grocer") || q.contains("atta") || q.contains("rice") || q.contains("milk") ||
                q.contains("dal") || q.contains("oil") || q.contains("ghee") || q.contains("sugar") || q.contains("tea") ||
                q.contains("coffee") || q.contains("biscuit") || q.contains("snack") || q.contains("chips") ||
                q.contains("vegetable") || q.contains("fruit")

        val isHeavyFurniture = cat.contains("furniture") || q.contains("sofa") || q.contains("bed") ||
                q.contains("wardrobe") || q.contains("mattress") || q.contains("dining table")

        val isHighEndFlagship = q.contains("macbook") || q.contains("iphone 15") || q.contains("iphone 16") ||
                q.contains("iphone 14") || q.contains("s24") || q.contains("s23") || q.contains("fold") ||
                q.contains("playstation 5") || q.contains("ps5") || q.contains("xbox series") || q.contains("rtx")

        val isAudioOrWearables = cat.contains("audio") || cat.contains("wearable") ||
                q.contains("earbud") || q.contains("earphone") || q.contains("headphone") ||
                q.contains("neckband") || q.contains("tws") || q.contains("smartwatch") ||
                q.contains("watch") || q.contains("truke") || q.contains("boat") ||
                q.contains("noise") || q.contains("boult") || q.contains("airpod")

        return when (store) {
            Store.CROMA, Store.RELIANCE_DIGITAL -> {
                // Electronics & Appliances specialist: Does NOT sell sports gear, clothes, shoes, cosmetics, groceries or heavy furniture
                !isSportsOrFitness && !isFashionOrFootwear && !isBeautyOrCosmetics && !isGrocery && !isHeavyFurniture
            }
            Store.MYNTRA -> {
                // Fashion, Footwear, Lifestyle, Personal Care, Audio & Wearables
                !isSportsOrFitness && !isGrocery && !isHeavyFurniture && (!isTechOrElectronics || isAudioOrWearables)
            }
            Store.AJIO -> {
                // Fashion, Footwear, Lifestyle & Personal Care: Does NOT sell gym equipment, laptops, mobiles, TVs, large appliances or groceries
                !isSportsOrFitness && !isTechOrElectronics && !isGrocery && !isHeavyFurniture
            }
            Store.NYKAA -> {
                // Cosmetics, Skincare, Fragrances & Personal Care: Does NOT sell gym gear, electronics, groceries or furniture
                !isSportsOrFitness && !isTechOrElectronics && !isGrocery && !isHeavyFurniture && (isBeautyOrCosmetics || isFashionOrFootwear)
            }
            Store.BLINKIT -> {
                // Quick groceries, daily essentials & small emergency accessories: Does NOT sell gym weights/grippers, laptops, large TVs, appliances or flagship phones
                !isSportsOrFitness && !isHighEndFlagship && !isHeavyFurniture && (isGrocery || isBeautyOrCosmetics || q.contains("cable") || q.contains("charger") || q.contains("bulb") || q.contains("earphone"))
            }
            Store.MEESHO -> {
                // Budget fashion, basic accessories & home goods
                !isHighEndFlagship && !q.contains("macbook") && !q.contains("playstation") && !q.contains("oled")
            }
            Store.JIOMART -> {
                // Open horizontal marketplace with Groceries, Electronics, Fashion, Home & Sports department
                !q.contains("zara") && !q.contains("h&m") && !q.contains("gucci")
            }
            Store.TATACLIQ -> {
                // Electronics, Lifestyle & Fashion: Does NOT carry gym fitness equipment or perishable groceries
                !isSportsOrFitness && !isGrocery
            }
            Store.WALMART, Store.TARGET -> {
                // US Retailers: Do NOT carry domestic Indian FMCG food brands or traditional Indian ethnic wear
                !q.contains("amul") && !q.contains("patanjali") && !q.contains("haldiram") && !q.contains("saree") && !q.contains("kurti")
            }
            Store.EBAY -> {
                // Global marketplace: Does NOT sell perishable groceries
                !isGrocery
            }
            Store.AMAZON, Store.FLIPKART -> {
                // Universal horizontal e-commerce giants: Support all categories
                true
            }
        }
    }
}
