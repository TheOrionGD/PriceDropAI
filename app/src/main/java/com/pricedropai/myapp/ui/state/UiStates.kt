package com.pricedropai.myapp.ui.state

import com.pricedropai.myapp.core.model.Product

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val product: Product) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
