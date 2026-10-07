package com.example.pricedropai.data.mapper

import com.example.pricedropai.core.model.*
import com.example.pricedropai.data.local.dao.ProductWithOffers
import com.example.pricedropai.data.local.entity.Converters
import com.example.pricedropai.data.local.entity.PriceSnapshotEntity
import com.example.pricedropai.data.local.entity.ProductEntity
import com.example.pricedropai.data.local.entity.StoreOfferEntity
import com.example.pricedropai.data.local.entity.TrackedProductEntity

object Mappers {
    private val converters = Converters()

    fun ProductWithOffers.toDomain(): Product {
        val variants = converters.toProductVariants(product.variantsJson) ?: emptyList()
        val reviewSummary = converters.toReviewSummary(product.reviewSummaryJson) ?: ReviewSummary()
        val paymentOffers = converters.toPaymentOffers(product.paymentOffersJson) ?: emptyList()
        val domainOffers = offers.map { it.toDomain() }

        return Product(
            id = product.id,
            title = product.title,
            description = product.description,
            imageUrl = product.imageUrl,
            category = product.category,
            brand = product.brand,
            rating = product.rating,
            reviewCount = product.reviewCount,
            variants = variants,
            stores = domainOffers,
            reviewSummary = reviewSummary,
            paymentOffers = paymentOffers,
            lastUpdated = product.lastUpdated
        )
    }

    fun Product.toEntity(): ProductEntity {
        return ProductEntity(
            id = id,
            title = title,
            description = description,
            imageUrl = imageUrl,
            category = category,
            brand = brand,
            rating = rating,
            reviewCount = reviewCount,
            reviewSummaryJson = converters.fromReviewSummary(reviewSummary),
            paymentOffersJson = converters.fromPaymentOffers(paymentOffers),
            variantsJson = converters.fromProductVariants(variants),
            lastUpdated = lastUpdated
        )
    }

    fun StoreOfferEntity.toDomain(): StoreOffer {
        return StoreOffer(
            id = id,
            productId = productId,
            store = store,
            productUrl = productUrl,
            price = price,
            originalPrice = originalPrice,
            discountPercentage = discountPercentage,
            currency = currency,
            availability = availability,
            deliveryInfo = deliveryInfo,
            lastUpdated = lastUpdated
        )
    }

    fun StoreOffer.toEntity(): StoreOfferEntity {
        return StoreOfferEntity(
            id = id,
            productId = productId,
            store = store,
            productUrl = productUrl,
            price = price,
            originalPrice = originalPrice,
            discountPercentage = discountPercentage,
            currency = currency,
            availability = availability,
            deliveryInfo = deliveryInfo,
            lastUpdated = lastUpdated
        )
    }

    fun PriceSnapshotEntity.toDomain(): PriceSnapshot {
        return PriceSnapshot(
            id = id,
            productId = productId,
            store = store,
            price = price,
            timestamp = timestamp,
            availability = availability
        )
    }

    fun PriceSnapshot.toEntity(): PriceSnapshotEntity {
        return PriceSnapshotEntity(
            id = id,
            productId = productId,
            store = store,
            price = price,
            timestamp = timestamp,
            availability = availability
        )
    }

    fun TrackedProductEntity.toDomain(): TrackedProduct {
        return TrackedProduct(
            id = id,
            productId = productId,
            productTitle = productTitle,
            imageUrl = imageUrl,
            store = store,
            currentPrice = currentPrice,
            targetPrice = targetPrice,
            lastNotifiedPrice = lastNotifiedPrice,
            lastNotificationTimestamp = lastNotificationTimestamp,
            createdAt = createdAt,
            lastChecked = lastChecked,
            enabled = enabled
        )
    }

    fun TrackedProduct.toEntity(): TrackedProductEntity {
        return TrackedProductEntity(
            id = id,
            productId = productId,
            productTitle = productTitle,
            imageUrl = imageUrl,
            store = store,
            currentPrice = currentPrice,
            targetPrice = targetPrice,
            lastNotifiedPrice = lastNotifiedPrice,
            lastNotificationTimestamp = lastNotificationTimestamp,
            createdAt = createdAt,
            lastChecked = lastChecked,
            enabled = enabled
        )
    }
}
