package com.pricedropai.thas.data.remote.datasource

import com.pricedropai.thas.core.model.PriceSnapshot
import com.pricedropai.thas.core.model.Product
import com.pricedropai.thas.core.model.ProductVariant
import com.pricedropai.thas.core.model.Store

interface ProductDataSource {
    suspend fun searchProducts(query: String): Result<List<Product>>
    suspend fun getProduct(productId: String): Result<Product>
    suspend fun getPrice(productId: String, store: Store): Result<PriceSnapshot>
    suspend fun getVariants(productId: String): Result<List<ProductVariant>>
}
