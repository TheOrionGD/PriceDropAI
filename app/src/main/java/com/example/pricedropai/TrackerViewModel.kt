package com.example.pricedropai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val data: ProductReport) : UiState()
    data class Error(val message: String) : UiState()
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val suggestedProduct: SuggestedProduct? = null
)

class TrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = LocalDbHelper(application.applicationContext)

    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set

    var watchlist = mutableStateListOf<WatchlistItem>()
        private set

    var chatMessages = mutableStateListOf<ChatMessage>()
        private set

    var isBotTyping by mutableStateOf(false)
        private set

    init {
        loadWatchlist()
        initDefaultChatMessages()
    }

    private fun initDefaultChatMessages() {
        chatMessages.add(
            ChatMessage(
                text = "Hello! I am your PriceDrop AI Copilot. Ask me about product price trends, deal recommendations, or upcoming festive sales!",
                isUser = false
            )
        )
    }

    fun search(query: String) {
        if (query.isBlank()) return
        uiState = UiState.Loading
        viewModelScope.launch {
            try {
                val report = LocalPriceEngine.fetchProductData(getApplication(), query)
                uiState = UiState.Success(report)
            } catch (e: Exception) {
                uiState = UiState.Error(e.localizedMessage ?: "Failed to fetch price comparison data.")
            }
        }
    }

    fun resetToHome() {
        uiState = UiState.Idle
    }

    fun loadWatchlist() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = dbHelper.getAllWatchlist()
            viewModelScope.launch(Dispatchers.Main) {
                watchlist.clear()
                watchlist.addAll(list)
            }
        }
    }

    fun saveTargetAlert(productName: String, targetPrice: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.insertWatchlist(productName, targetPrice)
            loadWatchlist()
        }
    }

    fun removeWatchlistItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteWatchlist(id)
            loadWatchlist()
        }
    }

    fun sendChatMessage(userText: String) {
        chatMessages.add(ChatMessage(text = userText, isUser = true))
        isBotTyping = true

        viewModelScope.launch {
            delay(900) // Realistic conversational latency
            val prompt = userText.lowercase()

            val (botReply, suggestedProduct) = when {
                prompt.contains("iphone") || prompt.contains("drop time") -> {
                    Pair(
                        "iPhone 15 is hovering around ₹56,499 - ₹57,999 across stores. Based on historical trends, it usually drops closer to ₹49,999 during mega festive events. My advice: WAIT if you can!",
                        SuggestedProduct(
                            title = "Apple iPhone 15 (128GB)",
                            price = 56499.0,
                            rating = 4.6,
                            imageUrl = "https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=600&q=80",
                            query = "iPhone 15 128GB"
                        )
                    )
                }
                prompt.contains("headphone") || prompt.contains("20k") || prompt.contains("audio") || prompt.contains("sony") -> {
                    Pair(
                        "The Sony WH-1000XM4 is currently at an all-time low of ₹19,499 on Meesho and ₹19,990 on Amazon India. Exceptional ANC and 30-hour battery life. Recommendation: BUY NOW!",
                        SuggestedProduct(
                            title = "Sony WH-1000XM4 Headset",
                            price = 19499.0,
                            rating = 4.5,
                            imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&q=80",
                            query = "Sony WH-1000XM4"
                        )
                    )
                }
                prompt.contains("bank") || prompt.contains("offer") || prompt.contains("card") -> {
                    Pair(
                        "Top bank offers today:\n• HDFC Bank Credit Cards: Flat ₹4,000 Instant Discount\n• SBI Credit Cards: 10% instant off up to ₹2,500\n• ICICI Amazon Pay: 5% Unlimited Cashback.\nSelect your card on any product screen to see net checkout prices!",
                        null
                    )
                }
                prompt.contains("sale") || prompt.contains("festival") || prompt.contains("billion") -> {
                    Pair(
                        "Next major promotional cycle: Festival Mega Drops in roughly 12-14 days. Expect 10% to 20% drops on smartphones and premium laptops. Set a target alert on your watchlist so you don't miss it!",
                        null
                    )
                }
                else -> {
                    Pair(
                        "I can analyze prices across Flipkart, Amazon, Meesho, Croma, and Myntra. Try asking: 'Best headphones under 20k', 'iPhone 15 drop time?', or tap any quick suggestion chip above!",
                        null
                    )
                }
            }

            isBotTyping = false
            chatMessages.add(ChatMessage(text = botReply, isUser = false, suggestedProduct = suggestedProduct))
        }
    }
}