package com.pricedropai.myapp.core.model

enum class Store(val displayName: String, val hostPattern: String) {
    AMAZON("Amazon India", "amazon"),
    FLIPKART("Flipkart", "flipkart"),
    MEESHO("Meesho", "meesho"),
    MYNTRA("Myntra", "myntra"),
    CROMA("Croma", "croma"),
    RELIANCE_DIGITAL("Reliance Digital", "reliancedigital"),
    JIOMART("JioMart", "jiomart"),
    TATACLIQ("Tata CLiQ", "tatacliq"),
    AJIO("Ajio", "ajio"),
    NYKAA("Nykaa", "nykaa"),
    BLINKIT("Blinkit", "blinkit"),
    EBAY("eBay", "ebay"),
    WALMART("Walmart", "walmart"),
    TARGET("Target", "target");

    companion object {
        fun fromUrl(url: String): Store? {
            val lower = url.lowercase()
            return when {
                lower.contains("amazon") || lower.contains("amzn") -> AMAZON
                lower.contains("flipkart") || lower.contains("fkrt") -> FLIPKART
                lower.contains("meesho") -> MEESHO
                lower.contains("myntra") -> MYNTRA
                lower.contains("croma") -> CROMA
                lower.contains("reliancedigital") -> RELIANCE_DIGITAL
                lower.contains("jiomart") -> JIOMART
                lower.contains("tatacliq") -> TATACLIQ
                lower.contains("ajio") -> AJIO
                lower.contains("nykaa") -> NYKAA
                lower.contains("blinkit") -> BLINKIT
                lower.contains("ebay") -> EBAY
                lower.contains("walmart") -> WALMART
                lower.contains("target") -> TARGET
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
    val imageUrl: String? = null,
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

enum class PriceCheckInterval(val displayName: String, val hours: Long) {
    EVERY_3_HOURS("Every 3 Hours", 3),
    EVERY_6_HOURS("Every 6 Hours", 6),
    EVERY_12_HOURS("Every 12 Hours", 12),
    ONCE_DAILY("Once Daily", 24);

    companion object {
        fun fromDisplayName(name: String?): PriceCheckInterval {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: EVERY_6_HOURS
        }
    }
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
    val validUntil: Long? = null,
    // Production banking engine fields
    val displayName: String? = null,
    val paymentMethod: String? = null, // "CREDIT_CARD", "DEBIT_CARD", "UPI", "EMI"
    val cardType: String? = null,
    val discountType: String? = null,
    val discountValue: Double? = null,
    val calculatedDiscount: Double? = null,
    val payablePrice: Double? = null,
    val isEligible: Boolean = true,
    val eligibilityStatus: String = "ELIGIBLE",
    val ineligibilityReason: String? = null,
    val terms: String? = null,
    val source: String? = null,
    val sourceUrl: String? = null,
    val lastVerifiedAt: Long? = null,
    val isCashback: Boolean = false,
    val emiEligible: Boolean = false
)

data class EmiPlan(
    val id: String,
    val provider: String,
    val displayName: String,
    val tenureMonths: Int,
    val monthlyInstallment: Double,
    val totalInstallmentAmount: Double,
    val interestAmount: Double = 0.0,
    val subventionDiscount: Double = 0.0,
    val processingFee: Double = 0.0,
    val taxes: Double = 0.0,
    val effectiveTotalCost: Double,
    val isNoCostEmi: Boolean = true,
    val eligibilityStatus: String = "ELIGIBLE",
    val ineligibilityReason: String? = null,
    val source: String? = null,
    val sourceUrl: String? = null,
    val terms: String? = null
)

data class RatingDistributionItem(
    val count: Int,
    val percentage: Double
)

data class RatingDistribution(
    val fiveStar: RatingDistributionItem? = null,
    val fourStar: RatingDistributionItem? = null,
    val threeStar: RatingDistributionItem? = null,
    val twoStar: RatingDistributionItem? = null,
    val oneStar: RatingDistributionItem? = null
)

data class FeatureSentimentScores(
    val buildQuality: Int? = null,
    val valueForMoney: Int? = null,
    val performance: Int? = null,
    val durability: Int? = null
)

data class ReviewSummary(
    val sentimentScore: Int? = null,
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val totalAnalyzed: Int? = null,
    val isAvailable: Boolean = false,
    val ratingDistribution: RatingDistribution? = null,
    val featureScores: FeatureSentimentScores? = null,
    val confidenceScore: Int? = null,
    val evidenceCoverage: String? = null,
    val dataFreshness: Long? = null,
    val analysisStatus: String? = null,
    val sourceMetadata: String? = null
)

data class Product(
    val id: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val asin: String? = null,
    val searchQuery: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val variants: List<ProductVariant> = emptyList(),
    val stores: List<StoreOffer> = emptyList(),
    val reviewSummary: ReviewSummary = ReviewSummary(),
    val paymentOffers: List<PaymentOffer> = emptyList(),
    val emiPlans: List<EmiPlan> = emptyList(),
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
