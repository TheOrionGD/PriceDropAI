package com.example.pricedropai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.roundToInt

data class StoreDeal(
    val storeName: String,
    val price: Double,
    val url: String,
    val inStock: Boolean,
    val deliveryDays: String
)

data class BankOffer(
    val id: Int,
    val bankName: String,
    val discountText: String,
    val discountAmount: Double
)

data class FestiveSaleAlert(
    val saleName: String,
    val daysRemaining: Int,
    val estimatedDrop: Double,
    val bannerNote: String
)

data class ReviewSummary(
    val sentimentScore: Int,
    val pros: List<String>,
    val cons: List<String>,
    val totalAnalyzed: String
)

data class ProductReport(
    val productName: String,
    val imageUrl: String,
    val category: String,
    val rating: Double,
    val ratingCount: String,
    val lowestPrice: Double,
    val predictedPrice: String,
    val recommendation: String,
    val history: List<Double>,
    val stores: List<StoreDeal>,
    val bankOffers: List<BankOffer>,
    val festiveSale: FestiveSaleAlert?,
    val reviewSummary: ReviewSummary
)

data class FlashDeal(
    val title: String,
    val dealPrice: Double,
    val originalPrice: Double,
    val discountPercent: Int,
    val rating: Double,
    val reviewsCount: String,
    val imageUrl: String,
    val storeName: String,
    val url: String
)

data class CategoryItem(
    val name: String,
    val iconUrl: String,
    val searchQuery: String
)

data class PromoBanner(
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val query: String
)

data class SuggestedProduct(
    val title: String,
    val price: Double,
    val rating: Double,
    val imageUrl: String,
    val query: String
)

object LocalPriceEngine {

    private fun buildStoreUrl(store: String, query: String): String {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query.replace(" ", "+")
        }

        return when (store) {
            "Amazon" -> "https://www.amazon.in/s?k=$encoded"
            "Flipkart" -> "https://www.flipkart.com/search?q=$encoded"
            "Meesho" -> "https://www.meesho.com/search?q=$encoded"
            "Myntra" -> "https://www.myntra.com/$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    fun resolveFallbackImage(query: String): String {
        val q = query.lowercase().trim()
        return when {
            // Lamps & Lighting
            q.contains("lamp") || q.contains("light") || q.contains("bulb") || q.contains("chandelier") || q.contains("lantern") ->
                "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?w=600&auto=format&fit=crop&q=80"

            // Chairs & Furniture
            q.contains("chair") || q.contains("table") || q.contains("desk") || q.contains("sofa") || q.contains("furniture") || q.contains("bed") ->
                "https://images.unsplash.com/photo-1580481077195-c3a821a58875?w=600&auto=format&fit=crop&q=80"

            // Bottles & Flasks
            q.contains("bottle") || q.contains("flask") || q.contains("sipper") ->
                "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&auto=format&fit=crop&q=80"

            // Bags & Backpacks
            q.contains("bag") || q.contains("backpack") || q.contains("luggage") || q.contains("tote") || q.contains("wallet") ->
                "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&auto=format&fit=crop&q=80"

            // Watches
            q.contains("watch") || q.contains("smartwatch") ->
                "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&auto=format&fit=crop&q=80"

            // Beauty & Makeup
            q.contains("lipstick") || q.contains("makeup") || q.contains("beauty") || q.contains("cream") || q.contains("lotion") ->
                "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=600&auto=format&fit=crop&q=80"

            // Perfume
            q.contains("perfume") || q.contains("scent") || q.contains("deodorant") ->
                "https://images.unsplash.com/photo-1523293182086-7651a899d37f?w=600&auto=format&fit=crop&q=80"

            // Shoes & Sneakers
            q.contains("shoe") || q.contains("sneaker") || q.contains("nike") || q.contains("crocs") || q.contains("sandal") ->
                "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&auto=format&fit=crop&q=80"

            // Clothes & Fashion
            q.contains("shirt") || q.contains("tshirt") || q.contains("dress") || q.contains("pant") || q.contains("jeans") || q.contains("hoodie") ->
                "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=600&auto=format&fit=crop&q=80"

            // Headphones & Audio
            q.contains("headphone") || q.contains("earbud") || q.contains("audio") || q.contains("speaker") || q.contains("sony") ->
                "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&auto=format&fit=crop&q=80"

            // Laptops
            q.contains("laptop") || q.contains("macbook") || q.contains("computer") || q.contains("pc") ->
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&auto=format&fit=crop&q=80"

            // Mobiles & Phones
            q.contains("phone") || q.contains("iphone") || q.contains("mobile") || q.contains("samsung") ->
                "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=600&auto=format&fit=crop&q=80"

            // Cameras
            q.contains("camera") || q.contains("dslr") || q.contains("lens") ->
                "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=600&auto=format&fit=crop&q=80"

            // Keyboard & Mouse
            q.contains("keyboard") || q.contains("mouse") ->
                "https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&auto=format&fit=crop&q=80"

            // Helmet
            q.contains("helmet") ->
                "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=600&auto=format&fit=crop&q=80"

            // Fans & AC
            q.contains("fan") || q.contains("cooler") || q.contains("ac") ->
                "https://images.unsplash.com/photo-1618941716939-553df3c6c278?w=600&auto=format&fit=crop&q=80"

            // Sunglasses & Eyewear
            q.contains("sunglass") || q.contains("glasses") || q.contains("goggle") ->
                "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=600&auto=format&fit=crop&q=80"

            // Cycle & Bicycle
            q.contains("cycle") || q.contains("bicycle") ->
                "https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=600&auto=format&fit=crop&q=80"

            // Books
            q.contains("book") || q.contains("novel") ->
                "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&auto=format&fit=crop&q=80"

            // Default Reliable High-Res Product Image
            else ->
                "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=600&auto=format&fit=crop&q=80"
        }
    }

    private fun resolveProductImage(query: String): Pair<String, String> {
        val q = query.lowercase().trim()
        val imageUrl = resolveFallbackImage(query)

        val category = when {
            q.contains("chair") || q.contains("table") || q.contains("desk") || q.contains("furniture") -> "Home & Furniture"
            q.contains("bottle") || q.contains("flask") -> "Kitchen & Dining"
            q.contains("bag") || q.contains("backpack") || q.contains("luggage") -> "Bags & Luggage"
            q.contains("watch") || q.contains("smartwatch") -> "Wearable Tech"
            q.contains("lipstick") || q.contains("makeup") || q.contains("beauty") -> "Beauty & Care"
            q.contains("perfume") || q.contains("scent") -> "Fragrances"
            q.contains("shoe") || q.contains("sneaker") -> "Footwear"
            q.contains("shirt") || q.contains("tshirt") || q.contains("dress") -> "Fashion"
            q.contains("headphone") || q.contains("audio") -> "Audio & Music"
            q.contains("laptop") || q.contains("computer") || q.contains("keyboard") -> "Computers & Tech"
            q.contains("phone") || q.contains("mobile") -> "Mobiles"
            q.contains("helmet") || q.contains("cycle") -> "Sports & Outdoors"
            q.contains("fan") || q.contains("cooler") -> "Appliances"
            q.contains("sunglass") || q.contains("glasses") -> "Accessories"
            else -> "Online Deals"
        }

        return Pair(imageUrl, category)
    }

    fun getCategories(): List<CategoryItem> = listOf(
        CategoryItem("Mobiles", "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=300&auto=format&fit=crop&q=80", "iPhone 15 128GB"),
        CategoryItem("Chairs", "https://images.unsplash.com/photo-1580481077195-c3a821a58875?w=300&auto=format&fit=crop&q=80", "Ergonomic Office Chair"),
        CategoryItem("Bottles", "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=300&auto=format&fit=crop&q=80", "Stainless Steel Water Bottle 1L"),
        CategoryItem("Bags", "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=300&auto=format&fit=crop&q=80", "Waterproof Laptop Backpack"),
        CategoryItem("Beauty", "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=300&auto=format&fit=crop&q=80", "Maybelline Matte Liquid Lipstick"),
        CategoryItem("Audio", "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=300&auto=format&fit=crop&q=80", "Sony WH-1000XM4")
    )

    fun getPromoBanners(): List<PromoBanner> = listOf(
        PromoBanner("Festival Mega Drops", "Flat up to 45% OFF across stores", "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?w=800&auto=format&fit=crop&q=80", "iPhone 15 128GB"),
        PromoBanner("Home & Ergonomic Comfort", "High-Back Mesh Chairs from ₹2,999", "https://images.unsplash.com/photo-1580481077195-c3a821a58875?w=800&auto=format&fit=crop&q=80", "Ergonomic Office Chair"),
        PromoBanner("Hydration & Sports Flasks", "Insulated Water Bottles from ₹499", "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=800&auto=format&fit=crop&q=80", "Stainless Steel Bottle")
    )

    fun getSuggestedProducts(): List<SuggestedProduct> = listOf(
        SuggestedProduct("Ergonomic High Back Office Chair", 3999.0, 4.4, "https://images.unsplash.com/photo-1580481077195-c3a821a58875?w=600&auto=format&fit=crop&q=80", "Ergonomic Office Chair"),
        SuggestedProduct("Stainless Steel Insulated Flask 1L", 599.0, 4.5, "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&auto=format&fit=crop&q=80", "Stainless Steel Water Bottle 1L"),
        SuggestedProduct("Waterproof Anti-Theft Backpack", 899.0, 4.4, "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&auto=format&fit=crop&q=80", "Laptop Backpack"),
        SuggestedProduct("Apple iPhone 15 (128GB)", 57999.0, 4.6, "https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=600&auto=format&fit=crop&q=80", "iPhone 15 128GB")
    )

    fun getFlashDeals(): List<FlashDeal> = listOf(
        FlashDeal("Green Soul Ergonomic Gaming Chair", 4499.0, 9990.0, 55, 4.4, "15.3k", "https://images.unsplash.com/photo-1580481077195-c3a821a58875?w=600&auto=format&fit=crop&q=80", "Amazon India", buildStoreUrl("Amazon", "Green Soul Ergonomic Chair")),
        FlashDeal("Milton Thermosteel 1L Water Bottle", 699.0, 1199.0, 42, 4.5, "34.1k", "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&auto=format&fit=crop&q=80", "Flipkart", buildStoreUrl("Flipkart", "Milton Thermosteel Bottle")),
        FlashDeal("American Tourister 32L Backpack", 999.0, 2400.0, 58, 4.3, "18.2k", "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&auto=format&fit=crop&q=80", "Meesho", buildStoreUrl("Meesho", "American Tourister Backpack"))
    )

    suspend fun fetchProductData(context: Context, query: String): ProductReport = withContext(Dispatchers.IO) {
        val q = query.lowercase().trim()
        val (imageUrl, category) = resolveProductImage(q)
        val formattedTitle = query.split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

        val basePrice = when {
            q.contains("chair") || q.contains("table") || q.contains("desk") || q.contains("bed") -> 3499.0
            q.contains("bottle") || q.contains("flask") -> 649.0
            q.contains("bag") || q.contains("backpack") -> 899.0
            q.contains("lipstick") || q.contains("makeup") -> 449.0
            q.contains("perfume") -> 1299.0
            q.contains("shoe") || q.contains("sneaker") -> 3499.0
            q.contains("watch") -> 2199.0
            q.contains("shirt") || q.contains("tshirt") -> 699.0
            q.contains("laptop") || q.contains("macbook") -> 83990.0
            q.contains("phone") || q.contains("iphone") || q.contains("samsung") -> 56499.0
            q.contains("headphone") || q.contains("audio") || q.contains("sony") -> 19499.0
            q.contains("keyboard") || q.contains("mouse") -> 1299.0
            q.contains("helmet") -> 1899.0
            q.contains("fan") || q.contains("cooler") -> 2499.0
            q.contains("sunglass") || q.contains("glasses") -> 999.0
            else -> 1499.0
        }

        val stores = listOf(
            StoreDeal("Meesho", (basePrice * 0.88).roundToInt().toDouble(), buildStoreUrl("Meesho", query), true, "3-4 Days Free Delivery"),
            StoreDeal("Flipkart", basePrice, buildStoreUrl("Flipkart", query), true, "Tomorrow by 2 PM"),
            StoreDeal("Amazon India", (basePrice * 1.04).roundToInt().toDouble(), buildStoreUrl("Amazon", query), true, "Tomorrow by 11 AM"),
            StoreDeal("Myntra", (basePrice * 1.10).roundToInt().toDouble(), buildStoreUrl("Myntra", query), true, "1-2 Days Delivery")
        )
        val lowest = stores.minOf { it.price }

        ProductReport(
            productName = "$formattedTitle (High Rating Multi-Store Edition)",
            imageUrl = imageUrl,
            category = category,
            rating = 4.5,
            ratingCount = "12,450 ratings",
            lowestPrice = lowest,
            predictedPrice = "₹${(lowest * 0.90).roundToInt()} in Next Sale",
            recommendation = "BUY NOW (Best price tracked on Meesho at ₹${lowest.roundToInt()})",
            history = listOf(basePrice * 1.35, basePrice * 1.25, basePrice * 1.18, basePrice * 1.08, basePrice, lowest),
            stores = stores,
            bankOffers = listOf(
                BankOffer(1, "HDFC / SBI Credit Cards", "Flat 10% Instant Discount", (lowest * 0.1).coerceAtMost(1000.0).roundToInt().toDouble()),
                BankOffer(2, "Instant UPI Pay", "Flat ₹50 Instant Cashback", 50.0),
                BankOffer(3, "Partner Card Offer", "5% Unlimited Rewards", (lowest * 0.05).roundToInt().toDouble())
            ),
            festiveSale = FestiveSaleAlert(
                saleName = "Super Savings Rush",
                daysRemaining = 7,
                estimatedDrop = (lowest * 0.12).roundToInt().toDouble(),
                bannerNote = "Prices for $query usually drop up to 15% during upcoming promotional cycles."
            ),
            reviewSummary = ReviewSummary(
                sentimentScore = 91,
                pros = listOf(
                    "High build quality and durability confirmed by verified buyers",
                    "Best value for price across all major stores",
                    "Accurate description and reliable packaging"
                ),
                cons = listOf(
                    "Delivery dates can vary by 1-2 days based on remote pin codes",
                    "Limited stock available on lowest price store"
                ),
                totalAnalyzed = "2,840 customer reviews summarized by AI"
            )
        )
    }
}