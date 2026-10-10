package com.pricedropai.myapp.data.remote.datasource

import com.pricedropai.myapp.core.model.PriceSnapshot
import com.pricedropai.myapp.core.model.Product
import com.pricedropai.myapp.core.model.ProductVariant
import com.pricedropai.myapp.core.model.Store

interface ProductDataSource {
    suspend fun searchProducts(query: String): Result<List<Product>>
    suspend fun getProduct(productId: String): Result<Product>
    suspend fun getPrice(productId: String, store: Store): Result<PriceSnapshot>
    suspend fun getVariants(productId: String): Result<List<ProductVariant>>
}
