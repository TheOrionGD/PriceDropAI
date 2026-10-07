package com.example.pricedropai.data.remote.datasource

import com.example.pricedropai.core.model.Product
import com.example.pricedropai.core.model.PriceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AiCopilotResponse(
    val replyText: String,
    val suggestedSearchQuery: String? = null,
    val isVerifiedFact: Boolean = true
)

class AiCopilotDataSource {

    suspend fun generateResponse(
        userPrompt: String,
        currentProduct: Product? = null,
        priceHistory: List<PriceSnapshot> = emptyList()
    ): AiCopilotResponse = withContext(Dispatchers.Default) {
        val query = userPrompt.trim().lowercase()

        // 1. If currently inspecting a product, answer based strictly on verified product facts
        if (currentProduct != null) {
            val lowestOffer = currentProduct.lowestOffer
            val lowestPrice = currentProduct.lowestPrice
            val storeCount = currentProduct.stores.size
            val availableStores = currentProduct.stores.filter { it.price != null }

            when {
                query.contains("cheapest") || query.contains("lowest") || query.contains("best price") || query.contains("which store") -> {
                    if (lowestOffer != null && lowestPrice != null) {
                        return@withContext AiCopilotResponse(
                            replyText = "Based on live verified data, ${lowestOffer.store.displayName} has the lowest verified price for '${currentProduct.title}' at ₹${"%,.0f".format(lowestPrice)}. ($storeCount stores checked).",
                            suggestedSearchQuery = currentProduct.title
                        )
                    } else {
                        return@withContext AiCopilotResponse(
                            replyText = "Currently, no verified in-stock price is available for '${currentProduct.title}'.",
                            suggestedSearchQuery = currentProduct.title
                        )
                    }
                }

                query.contains("history") || query.contains("trend") || query.contains("drop") || query.contains("drop time") -> {
                    if (priceHistory.size >= 2) {
                        val firstPrice = priceHistory.first().price
                        val latestPrice = priceHistory.last().price
                        val minHistorical = priceHistory.minOf { it.price }
                        val diff = latestPrice - firstPrice

                        val trendText = when {
                            diff < 0 -> "The price has decreased by ₹${"%,.0f".format(-diff)} across ${priceHistory.size} recorded snapshots."
                            diff > 0 -> "The price has increased by ₹${"%,.0f".format(diff)} compared to earlier snapshots."
                            else -> "The price has remained steady across ${priceHistory.size} recorded snapshots."
                        }

                        return@withContext AiCopilotResponse(
                            replyText = "$trendText The lowest snapshot recorded in your app is ₹${"%,.0f".format(minHistorical)}.",
                            suggestedSearchQuery = currentProduct.title
                        )
                    } else {
                        return@withContext AiCopilotResponse(
                            replyText = "Not enough historical price snapshots have been recorded yet for '${currentProduct.title}'. PriceDrop AI records real snapshots on each check.",
                            suggestedSearchQuery = currentProduct.title
                        )
                    }
                }

                query.contains("should i buy") || query.contains("buy now") || query.contains("wait") -> {
                    if (lowestPrice != null && priceHistory.isNotEmpty()) {
                        val minHistorical = priceHistory.minOf { it.price }
                        val isAtLow = lowestPrice <= minHistorical
                        val advice = if (isAtLow) {
                            "The current price of ₹${"%,.0f".format(lowestPrice)} on ${lowestOffer?.store?.displayName} is at or below all recorded snapshots. If you need it now, it is a competitive price."
                        } else {
                            "The current price is ₹${"%,.0f".format(lowestPrice)}, whereas the lowest recorded snapshot was ₹${"%,.0f".format(minHistorical)}. Consider adding a Watchlist target alert to monitor drops."
                        }
                        return@withContext AiCopilotResponse(
                            replyText = advice,
                            suggestedSearchQuery = currentProduct.title
                        )
                    } else if (lowestPrice != null) {
                        return@withContext AiCopilotResponse(
                            replyText = "Current best verified price is ₹${"%,.0f".format(lowestPrice)} on ${lowestOffer?.store?.displayName}. Track this product in your Watchlist to monitor for price drops.",
                            suggestedSearchQuery = currentProduct.title
                        )
                    }
                }

                query.contains("review") || query.contains("pros") || query.contains("cons") || query.contains("rating") -> {
                    if (currentProduct.reviewSummary.isAvailable) {
                        val prosStr = currentProduct.reviewSummary.pros.joinToString("\n• ", prefix = "• ")
                        val consStr = currentProduct.reviewSummary.cons.joinToString("\n• ", prefix = "• ")
                        return@withContext AiCopilotResponse(
                            replyText = "Verified Review Summary:\n\nPros:\n$prosStr\n\nCons:\n$consStr"
                        )
                    } else {
                        return@withContext AiCopilotResponse(
                            replyText = "Verified customer review analysis is not yet available for this item."
                        )
                    }
                }
            }
        }

        // 2. General Assistant Queries
        when {
            query.contains("how does it work") || query.contains("what can you do") || query.contains("help") -> {
                AiCopilotResponse(
                    replyText = "I help you find real-time prices across Amazon India, Flipkart, Meesho, and Myntra. You can:\n• Search any product name or paste an e-commerce link\n• Compare live multi-store pricing\n• Set price-drop target alerts in your Watchlist\n• View real recorded price history charts"
                )
            }
            query.contains("iphone") || query.contains("apple") -> {
                AiCopilotResponse(
                    replyText = "You can search for iPhone models to compare live prices across Amazon, Flipkart, Meesho, and Myntra, and add it to your Watchlist for price drop notifications.",
                    suggestedSearchQuery = "iPhone 15 128GB"
                )
            }
            query.contains("headphone") || query.contains("earphone") || query.contains("sony") -> {
                AiCopilotResponse(
                    replyText = "Search for your preferred headphones to check multi-store deals and set target price alerts.",
                    suggestedSearchQuery = "Sony WH-1000XM4"
                )
            }
            query.contains("chair") || query.contains("desk") || query.contains("furniture") -> {
                AiCopilotResponse(
                    replyText = "Search office and ergonomic furniture to check live availability and delivery timelines across retailers.",
                    suggestedSearchQuery = "Ergonomic Office Chair"
                )
            }
            else -> {
                AiCopilotResponse(
                    replyText = "Ask me to compare prices for any product, inspect recorded historical price drops, or evaluate multi-store offers. You can also paste an Amazon, Flipkart, Meesho, or Myntra link directly!",
                    suggestedSearchQuery = userPrompt.takeIf { it.length in 3..40 }
                )
            }
        }
    }
}
