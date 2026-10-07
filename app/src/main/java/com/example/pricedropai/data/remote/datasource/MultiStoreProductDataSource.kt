package com.example.pricedropai.data.remote.datasource

import android.util.Log
import com.example.pricedropai.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

                val validScraped = listOfNotNull(amazonScraped, flipkartScraped, meeshoScraped, myntraScraped)

                if (validScraped.isEmpty()) {
                    return@coroutineScope Result.success(emptyList())
                }

                val productId = cleanQuery.lowercase().replace("[^a-z0-9]+".toRegex(), "-").take(60)

                // Normalize offers with consistent deterministic IDs
                val normalizedOffers = validScraped.map { scraped ->
                    scraped.offer.copy(
                        id = "${productId}_${scraped.offer.store.name.lowercase()}",
                        productId = productId
                    )
                }

                val primaryScraped = validScraped.minByOrNull { it.offer.price ?: Double.MAX_VALUE } ?: validScraped.first()
                val primaryOffer = primaryScraped.offer

                val title = (primaryOffer.deliveryInfo?.takeIf { it.isNotBlank() } ?: cleanQuery)
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

                // Pick verified rating if present from any scraped store
                val verifiedRating = validScraped.mapNotNull { it.rating }.firstOrNull()
                val verifiedReviewCount = validScraped.mapNotNull { it.reviewCount }.firstOrNull()
                val primaryImage = validScraped.mapNotNull { it.imageUrl }.firstOrNull { it.startsWith("http") }

                val product = Product(
                    id = productId,
                    title = title,
                    description = "Live multi-store verified pricing across ${normalizedOffers.size} available retail stores.",
                    imageUrl = primaryImage ?: primaryOffer.productUrl,
                    category = detectCategory(cleanQuery),
                    brand = detectBrand(cleanQuery),
                    rating = verifiedRating,
                    reviewCount = verifiedReviewCount,
                    variants = emptyList(),
                    stores = normalizedOffers,
                    reviewSummary = ReviewSummary(isAvailable = false),
                    paymentOffers = emptyList(),
                    lastUpdated = System.currentTimeMillis()
                )

                Result.success(listOf(product))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.localizedMessage}", e)
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

    // --- STORE IMPLEMENTATIONS (HTML Extractors) ---

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

            val resultCard = doc.select("div[data-component-type=\"s-search-result\"]").firstOrNull()
            if (resultCard != null) {
                val priceWhole = resultCard.select("span.a-price-whole").firstOrNull()?.text()
                    ?.replace("[^0-9]".toRegex(), "")?.toDoubleOrNull()
                val originalPrice = resultCard.select("span.a-price.a-text-price span.a-offscreen").firstOrNull()?.text()
                    ?.replace("[^0-9]".toRegex(), "")?.toDoubleOrNull()
                val linkEl = resultCard.select("h2 a, a.a-link-normal.s-no-outline").firstOrNull()
                val titleEl = resultCard.select("h2 span, span.a-size-medium, span.a-size-base-plus").firstOrNull()
                val imgEl = resultCard.select("img.s-image").firstOrNull()
                val ratingText = resultCard.select("span.a-icon-alt, i.a-icon-star-small span").firstOrNull()?.text()
                val reviewCountText = resultCard.select("span.a-size-base.s-underline-text, span.s-underline-text").firstOrNull()?.text()

                if (priceWhole != null && priceWhole > 10.0) {
                    val relHref = linkEl?.attr("href") ?: ""
                    val productUrl = if (relHref.startsWith("http")) relHref else "https://www.amazon.in$relHref"
                    val title = titleEl?.text() ?: query
                    val imageUrl = imgEl?.attr("src")?.takeIf { it.startsWith("http") }

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
            Log.d(TAG, "Amazon fetch notice: ${e.message}")
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
            val imgEl = doc.select("img._53qgcR, img.DByuf4, img._396cs4").firstOrNull()
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
                    val imageUrl = imgEl?.attr("src")?.takeIf { it.startsWith("http") }

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
            Log.d(TAG, "Flipkart fetch notice: ${e.message}")
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

                    val offer = StoreOffer(
                        id = "meesho_${query.hashCode().toUInt().toString(16)}",
                        productId = "",
                        store = Store.MEESHO,
                        productUrl = if (finalUrl.length > 25) finalUrl else targetUrl,
                        price = rawPrice,
                        originalPrice = null,
                        discountPercentage = null,
                        availability = Availability.IN_STOCK,
                        deliveryInfo = query,
                        lastUpdated = System.currentTimeMillis()
                    )

                    return ScrapedOfferResult(
                        offer = offer,
                        imageUrl = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "Meesho fetch notice: ${e.message}")
            null
        }
    }

    private fun fetchMyntraLive(query: String): ScrapedOfferResult? {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val targetUrl = "https://www.myntra.com/$encoded"

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
                    val relHref = linkEl?.attr("href") ?: ""
                    val finalUrl = if (relHref.startsWith("http")) relHref else "https://www.myntra.com/$relHref"

                    val offer = StoreOffer(
                        id = "myntra_${query.hashCode().toUInt().toString(16)}",
                        productId = "",
                        store = Store.MYNTRA,
                        productUrl = if (finalUrl.length > 25) finalUrl else targetUrl,
                        price = rawPrice,
                        originalPrice = origPrice,
                        discountPercentage = if (origPrice != null && origPrice > rawPrice) ((origPrice - rawPrice) / origPrice * 100.0) else null,
                        availability = Availability.IN_STOCK,
                        deliveryInfo = titleEl?.text() ?: query,
                        lastUpdated = System.currentTimeMillis()
                    )

                    return ScrapedOfferResult(
                        offer = offer,
                        imageUrl = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "Myntra fetch notice: ${e.message}")
            null
        }
    }

    private fun detectCategory(q: String): String {
        val lower = q.lowercase()
        return when {
            lower.contains("phone") || lower.contains("iphone") || lower.contains("samsung") || lower.contains("pixel") -> "Mobiles"
            lower.contains("laptop") || lower.contains("macbook") || lower.contains("computer") -> "Computers"
            lower.contains("headphone") || lower.contains("earbud") || lower.contains("audio") || lower.contains("speaker") -> "Audio"
            lower.contains("shoe") || lower.contains("sneaker") || lower.contains("boot") -> "Footwear"
            lower.contains("chair") || lower.contains("table") || lower.contains("desk") || lower.contains("sofa") -> "Furniture"
            lower.contains("bottle") || lower.contains("flask") -> "Kitchen"
            lower.contains("bag") || lower.contains("backpack") -> "Bags"
            lower.contains("watch") -> "Wearables"
            else -> "General E-Commerce"
        }
    }

    private fun detectBrand(q: String): String? {
        val lower = q.lowercase()
        val brands = listOf("apple", "samsung", "sony", "nike", "adidas", "puma", "boat", "noise", "oneplus", "asus", "dell", "hp", "lenovo", "milton")
        return brands.firstOrNull { lower.contains(it) }?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
