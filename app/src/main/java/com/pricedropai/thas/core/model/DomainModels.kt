package com.pricedropai.thas.core.model

enum class Store(val displayName: String, val hostPattern: String) {
    AMAZON("Amazon India", "amazon"),
    FLIPKART("Flipkart", "flipkart"),
    MEESHO("Meesho", "meesho"),
    MYNTRA("Myntra", "myntra");

    companion object {
        fun fromUrl(url: String): Store? {
            val lower = url.lowercase()
            return when {
                lower.contains("amazon") || lower.contains("amzn") -> AMAZON
                lower.contains("flipkart") || lower.contains("fkrt") -> FLIPKART
                lower.contains("meesho") -> MEESHO
                lower.contains("myntra") -> MYNTRA
                else -> null
            }
        }
    }
}

enum class Availability {
    IN_STOCK,
    OUT_OF_STOCK,
    LIMITED_STOCK,
    UNKNOWN
}

data class ProductVariant(
    val id: String,
    val productId: String,
    val name: String,
    val type: String = "Option", // e.g. "Storage", "Color", "Size"
    val price: Double? = null,
    val isAvailable: Boolean = true
)

data class StoreOffer(
    val id: String,
    val productId: String,
    val store: Store,
    val productUrl: String,
    val price: Double?,
    val originalPrice: Double?,
    val discountPercentage: Double?,
    val currency: String = "INR",
    val availability: Availability = Availability.IN_STOCK,
    val deliveryInfo: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PriceSnapshot(
    val id: Long = 0,
    val productId: String,
    val store: Store,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val availability: Availability = Availability.IN_STOCK
)

data class TrackedProduct(
    val id: Long = 0,
    val productId: String,
    val productTitle: String,
    val imageUrl: String? = null,
    val store: Store? = null,
    val currentPrice: Double,
    val targetPrice: Double,
    val lastNotifiedPrice: Double? = null,
    val lastNotificationTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastChecked: Long = System.currentTimeMillis(),
    val enabled: Boolean = true
)

enum class PaymentOfferType {
    INSTANT_DISCOUNT,
    CASHBACK,
    EMI_DISCOUNT,
    REWARDS
}

data class PaymentOffer(
    val id: String,
    val provider: String,
    val type: PaymentOfferType,
    val description: String,
    val amount: Double? = null,
    val percentage: Double? = null,
    val minimumPurchase: Double? = null,
    val maximumDiscount: Double? = null,
    val validUntil: Long? = null
)

data class ReviewSummary(
    val sentimentScore: Int? = null,
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val totalAnalyzed: Int? = null,
    val isAvailable: Boolean = false
)

data class Product(
    val id: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val variants: List<ProductVariant> = emptyList(),
    val stores: List<StoreOffer> = emptyList(),
    val reviewSummary: ReviewSummary = ReviewSummary(),
    val paymentOffers: List<PaymentOffer> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val lowestOffer: StoreOffer?
        get() = stores.filter { it.price != null && it.price > 0 && it.availability != Availability.OUT_OF_STOCK }
            .minByOrNull { it.price!! }

    val highestOffer: StoreOffer?
        get() = stores.filter { it.price != null && it.price > 0 }
            .maxByOrNull { it.price!! }

    val lowestPrice: Double?
        get() = lowestOffer?.price

    val maxDiscountPercent: Double?
        get() {
            val offersWithDiscount = stores.mapNotNull { it.discountPercentage }.filter { it > 0 }
            return offersWithDiscount.maxOrNull()
        }
}
