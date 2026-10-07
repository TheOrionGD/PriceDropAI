package com.example.pricedropai.data.remote.datasource

import com.example.pricedropai.core.model.PriceSnapshot
import com.example.pricedropai.core.model.Product
import com.example.pricedropai.core.model.ProductVariant
import com.example.pricedropai.core.model.Store

interface ProductDataSource {
    suspend fun searchProducts(query: String): Result<List<Product>>
    suspend fun getProduct(productId: String): Result<Product>
    suspend fun getPrice(productId: String, store: Store): Result<PriceSnapshot>
    suspend fun getVariants(productId: String): Result<List<ProductVariant>>
}
