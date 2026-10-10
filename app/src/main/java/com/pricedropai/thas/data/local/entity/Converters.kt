package com.pricedropai.thas.data.local.entity

import androidx.room.TypeConverter
import com.pricedropai.thas.core.model.Availability
import com.pricedropai.thas.core.model.PaymentOffer
import com.pricedropai.thas.core.model.ProductVariant
import com.pricedropai.thas.core.model.ReviewSummary
import com.pricedropai.thas.core.model.Store
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStore(store: Store?): String? = store?.name

    @TypeConverter
    fun toStore(name: String?): Store? = name?.let {
        try { Store.valueOf(it) } catch (e: Exception) { null }
    }

    @TypeConverter
    fun fromAvailability(availability: Availability?): String? = availability?.name

    @TypeConverter
    fun toAvailability(name: String?): Availability? = name?.let {
        try { Availability.valueOf(it) } catch (e: Exception) { Availability.UNKNOWN }
    }

    @TypeConverter
    fun fromReviewSummary(summary: ReviewSummary?): String? = summary?.let { gson.toJson(it) }

    @TypeConverter
    fun toReviewSummary(json: String?): ReviewSummary? = json?.let {
        try { gson.fromJson(it, ReviewSummary::class.java) } catch (e: Exception) { null }
    }

    @TypeConverter
    fun fromPaymentOffers(offers: List<PaymentOffer>?): String? = offers?.let { gson.toJson(it) }

    @TypeConverter
    fun toPaymentOffers(json: String?): List<PaymentOffer>? = json?.let {
        try {
            val type = object : TypeToken<List<PaymentOffer>>() {}.type
            gson.fromJson<List<PaymentOffer>>(it, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromProductVariants(variants: List<ProductVariant>?): String? = variants?.let { gson.toJson(it) }

    @TypeConverter
    fun toProductVariants(json: String?): List<ProductVariant>? = json?.let {
        try {
            val type = object : TypeToken<List<ProductVariant>>() {}.type
            gson.fromJson<List<ProductVariant>>(it, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
