package com.pricedropai.myapp.data.repository

import com.pricedropai.myapp.core.model.Product
import com.pricedropai.myapp.core.model.PriceSnapshot
import com.pricedropai.myapp.data.remote.datasource.AiCopilotDataSource
import com.pricedropai.myapp.data.remote.datasource.AiCopilotResponse

data class CopilotChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val suggestedSearchQuery: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class AiCopilotRepository(
    private val dataSource: AiCopilotDataSource = AiCopilotDataSource()
) {
    suspend fun askCopilot(
        prompt: String,
        currentProduct: Product? = null,
        priceHistory: List<PriceSnapshot> = emptyList()
    ): CopilotChatMessage {
        val response = dataSource.generateResponse(prompt, currentProduct, priceHistory)
        return CopilotChatMessage(
            text = response.replyText,
            isUser = false,
            suggestedSearchQuery = response.suggestedSearchQuery
        )
    }
}
