package com.pricedropai.myapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pricedropai.myapp.core.model.PriceSnapshot
import com.pricedropai.myapp.core.model.Product
import com.pricedropai.myapp.core.model.Store
import com.pricedropai.myapp.core.model.TrackedProduct
import com.pricedropai.myapp.data.local.PriceDropDatabase
import com.pricedropai.myapp.data.repository.AiCopilotRepository
import com.pricedropai.myapp.data.repository.CopilotChatMessage
import com.pricedropai.myapp.data.repository.ProductRepository
import com.pricedropai.myapp.data.repository.WatchlistRepository
import com.pricedropai.myapp.ui.state.SearchUiState
import com.pricedropai.myapp.worker.PriceCheckWorker
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
                        loadSnapshotsForProduct(product.id)

                        // Real-time In-App Notification based on search completion
                        val lowestPrice = product.lowestPrice
                        val lowestPriceText = if (lowestPrice != null) "Lowest price: ₹${"%,.0f".format(lowestPrice)}" else "Prices compared"
                        com.pricedropai.myapp.data.local.InAppNotificationManager.postNotification(
                            title = "Search Complete: ${product.title.take(35)}...",
                            description = "Found ${product.stores.size} verified store offers. $lowestPriceText.",
                            category = com.pricedropai.myapp.data.local.InAppNotificationCategory.SEARCHES,
                            iconType = com.pricedropai.myapp.data.local.NotificationIconType.SEARCH,
                            accentColor = androidx.compose.ui.graphics.Color(0xFF38BDF8),
                            severity = com.pricedropai.myapp.data.local.NotificationSeverity.INFO
                        )
                    },
                    onFailure = { error ->
                        val errorMsg = error.localizedMessage ?: "Unable to fetch live price data from stores. Please check connection and try again."
                        _searchState.value = SearchUiState.Error(errorMsg)

                        com.pricedropai.myapp.data.local.InAppNotificationManager.postNotification(
                            title = "Search Failed for '$clean'",
                            description = errorMsg,
                            category = com.pricedropai.myapp.data.local.InAppNotificationCategory.SYSTEM,
                            iconType = com.pricedropai.myapp.data.local.NotificationIconType.SYSTEM,
                            accentColor = androidx.compose.ui.graphics.Color(0xFFEF4444),
                            severity = com.pricedropai.myapp.data.local.NotificationSeverity.ALERT
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

            // Real-time In-App Notification on user setting price alert
            com.pricedropai.myapp.data.local.InAppNotificationManager.postNotification(
                title = "Price Alert Armed",
                description = "Tracking '${productTitle.take(32)}...'. We will alert you the moment price drops to or below ₹${"%,.0f".format(targetPrice)}.",
                category = com.pricedropai.myapp.data.local.InAppNotificationCategory.ALERTS,
                iconType = com.pricedropai.myapp.data.local.NotificationIconType.ALERT,
                accentColor = androidx.compose.ui.graphics.Color(0xFF10B981),
                severity = com.pricedropai.myapp.data.local.NotificationSeverity.INFO
            )
        }
    }

    fun toggleTrackedEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            watchlistRepository.setEnabled(id, enabled)
            val status = if (enabled) "Resumed" else "Paused"
            com.pricedropai.myapp.data.local.InAppNotificationManager.postNotification(
                title = "Alert Monitoring $status",
                description = "Live tracking for item #$id is now $status.",
                category = com.pricedropai.myapp.data.local.InAppNotificationCategory.ALERTS,
                iconType = com.pricedropai.myapp.data.local.NotificationIconType.TUNING,
                accentColor = androidx.compose.ui.graphics.Color(0xFFF59E0B),
                severity = com.pricedropai.myapp.data.local.NotificationSeverity.INFO
            )
        }
    }

    fun removeWatchlistItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            watchlistRepository.deleteTracked(id)
            com.pricedropai.myapp.data.local.InAppNotificationManager.postNotification(
                title = "Watchlist Item Removed",
                description = "Price drop tracker for item #$id has been removed from your watchlist.",
                category = com.pricedropai.myapp.data.local.InAppNotificationCategory.ALERTS,
                iconType = com.pricedropai.myapp.data.local.NotificationIconType.TRASH,
                accentColor = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                severity = com.pricedropai.myapp.data.local.NotificationSeverity.INFO
            )
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            productRepository.clearSearchHistory()
            com.pricedropai.myapp.data.local.InAppNotificationManager.postNotification(
                title = "Search History Cleared",
                description = "All on-device recent search queries have been safely cleared.",
                category = com.pricedropai.myapp.data.local.InAppNotificationCategory.SEARCHES,
                iconType = com.pricedropai.myapp.data.local.NotificationIconType.HISTORY,
                accentColor = androidx.compose.ui.graphics.Color(0xFF6366F1),
                severity = com.pricedropai.myapp.data.local.NotificationSeverity.INFO
            )
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

            // Real-time In-App Notification on AI Copilot consultation
            com.pricedropai.myapp.data.local.InAppNotificationManager.postNotification(
                title = "AI Copilot Response Ready",
                description = "Evaluated '${prompt.take(30)}...': ${botReply.text.take(65)}...",
                category = com.pricedropai.myapp.data.local.InAppNotificationCategory.COPILOT,
                iconType = com.pricedropai.myapp.data.local.NotificationIconType.COPILOT,
                accentColor = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
                severity = com.pricedropai.myapp.data.local.NotificationSeverity.INFO
            )
        }
    }

    fun refresh(query: String? = null) {
        val targetQuery = query?.trim()?.takeIf { it.isNotBlank() }
            ?: (_searchState.value as? SearchUiState.Success)?.product?.title
            ?: recentSearches.value.firstOrNull()

        if (targetQuery.isNullOrBlank()) {
            _isRefreshing.value = false
            return
        }

        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                productRepository.searchAndSyncProduct(targetQuery).collect { result ->
                    result.getOrNull()?.let { product ->
                        _searchState.value = SearchUiState.Success(product)
                        loadSnapshotsForProduct(product.id)
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshWatchlist() {
        val tracked = watchlist.value
        if (tracked.isEmpty()) {
            _isRefreshing.value = false
            return
        }

        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                for (item in tracked) {
                    productRepository.searchAndSyncProduct(item.productTitle).collect {}
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
