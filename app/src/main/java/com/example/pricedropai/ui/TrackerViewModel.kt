package com.example.pricedropai.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pricedropai.core.model.PriceSnapshot
import com.example.pricedropai.core.model.Product
import com.example.pricedropai.core.model.Store
import com.example.pricedropai.core.model.TrackedProduct
import com.example.pricedropai.data.local.PriceDropDatabase
import com.example.pricedropai.data.repository.AiCopilotRepository
import com.example.pricedropai.data.repository.CopilotChatMessage
import com.example.pricedropai.data.repository.ProductRepository
import com.example.pricedropai.data.repository.WatchlistRepository
import com.example.pricedropai.ui.state.SearchUiState
import com.example.pricedropai.worker.PriceCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PriceDropDatabase.getInstance(application.applicationContext)
    private val productRepository = ProductRepository(database)
    private val watchlistRepository = WatchlistRepository(database)
    private val copilotRepository = AiCopilotRepository()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    val watchlist: StateFlow<List<TrackedProduct>> = watchlistRepository.observeWatchlist()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentSearches: StateFlow<List<String>> = productRepository.observeRecentSearches(10)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentProducts: StateFlow<List<Product>> = productRepository.observeRecentProducts(10)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentSnapshots = MutableStateFlow<List<PriceSnapshot>>(emptyList())
    val currentSnapshots: StateFlow<List<PriceSnapshot>> = _currentSnapshots.asStateFlow()

    var chatMessages = mutableStateListOf<CopilotChatMessage>()
        private set

    var isBotTyping by mutableStateOf(false)
        private set

    init {
        initDefaultChatMessages()
        PriceCheckWorker.schedulePeriodicCheck(application.applicationContext)
    }

    private fun initDefaultChatMessages() {
        chatMessages.add(
            CopilotChatMessage(
                text = "Hello! I am PriceDrop AI Copilot. Ask me to compare store prices, check recorded price history, or evaluate multi-store offers.",
                isUser = false
            )
        )
    }

    fun search(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return

        _searchState.value = SearchUiState.Loading

        viewModelScope.launch {
            productRepository.searchAndSyncProduct(clean).collect { result ->
                result.fold(
                    onSuccess = { product ->
                        _searchState.value = SearchUiState.Success(product)
                        // Load historical price snapshots for this product
                        loadSnapshotsForProduct(product.id)
                    },
                    onFailure = { error ->
                        _searchState.value = SearchUiState.Error(
                            error.localizedMessage ?: "Unable to fetch live price data from stores. Please check connection and try again."
                        )
                    }
                )
            }
        }
    }

    private fun loadSnapshotsForProduct(productId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshots = productRepository.getPriceSnapshots(productId)
            _currentSnapshots.value = snapshots
        }
    }

    fun resetToHome() {
        _searchState.value = SearchUiState.Idle
        _currentSnapshots.value = emptyList()
    }

    fun saveTargetAlert(
        productTitle: String,
        targetPrice: Double,
        currentPrice: Double,
        store: Store? = null,
        imageUrl: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            watchlistRepository.addTrackedProduct(
                productTitle = productTitle,
                targetPrice = targetPrice,
                currentPrice = currentPrice,
                store = store,
                imageUrl = imageUrl
            )
        }
    }

    fun toggleTrackedEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            watchlistRepository.setEnabled(id, enabled)
        }
    }

    fun removeWatchlistItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            watchlistRepository.deleteTracked(id)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            productRepository.clearSearchHistory()
        }
    }

    fun sendChatMessage(userText: String) {
        val prompt = userText.trim()
        if (prompt.isBlank()) return

        chatMessages.add(CopilotChatMessage(text = prompt, isUser = true))
        isBotTyping = true

        val currentProduct = (_searchState.value as? SearchUiState.Success)?.product
        val snapshots = _currentSnapshots.value

        viewModelScope.launch {
            val botReply = copilotRepository.askCopilot(
                prompt = prompt,
                currentProduct = currentProduct,
                priceHistory = snapshots
            )
            isBotTyping = false
            chatMessages.add(botReply)
        }
    }
}
