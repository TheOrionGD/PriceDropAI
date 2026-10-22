package com.pricedropai.myapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pricedropai.myapp.core.model.Product
import com.pricedropai.myapp.ui.TrackerViewModel
import com.pricedropai.myapp.ui.state.SearchUiState

import com.pricedropai.myapp.ui.theme.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: TrackerViewModel,
    onOpenSettings: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val searchState by viewModel.searchState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val recentProducts by viewModel.recentProducts.collectAsState()
    val currentSnapshots by viewModel.currentSnapshots.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(searchQuery) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

        // Top Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searchState is SearchUiState.Success) {
                IconButton(
                    onClick = { viewModel.resetToHome() },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Discover"
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search product name or paste store link...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.7f)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    focusManager.clearFocus()
                                    viewModel.search(searchQuery)
                                }
                            },
                            modifier = Modifier.bounceClick(targetScale = 0.92f) {
                                if (searchQuery.isNotBlank()) {
                                    focusManager.clearFocus()
                                    viewModel.search(searchQuery)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.8f)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            focusManager.clearFocus()
                            viewModel.search(searchQuery)
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.04f, maxAlpha = 0.16f)
            )

            if (onOpenSettings != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .bounceClick(targetScale = 0.92f) { onOpenSettings() }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.05f, maxAlpha = 0.25f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (val state = searchState) {
            is SearchUiState.Idle -> {
                HomeDiscoveryContent(
                    recentSearches = recentSearches,
                    recentProducts = recentProducts,
                    onSearchSelect = { query ->
                        searchQuery = query
                        focusManager.clearFocus()
                        viewModel.search(query)
                    },
                    onClearHistory = { viewModel.clearSearchHistory() }
                )
            }
            is SearchUiState.Loading -> {
                // Multi-stage animated progression during live multi-store comparison
                val steps = remember {
                    listOf(
                        "Connecting to 12 retail platforms & initializing anti-bot gateways..." to 0.12f,
                        "Querying Amazon India & Flipkart live inventory..." to 0.25f,
                        "Scanning Meesho & Myntra catalog and checking keyword relevance..." to 0.38f,
                        "Fetching electronics deals from Croma & Reliance Digital..." to 0.50f,
                        "Scanning Tata CLiQ, Ajio, Nykaa & JioMart store listings..." to 0.62f,
                        "Querying Blinkit quick-commerce & eBay international inventory..." to 0.75f,
                        "Engaging Gemini & Groq dual-AI market validation & price normalization..." to 0.88f,
                        "Clustering live offers, picking verified HD image & calculating lowest deals..." to 0.98f
                    )
                }
                var currentStepIdx by remember { mutableIntStateOf(0) }
                var displayedChars by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    while (currentStepIdx < steps.size - 1) {
                        kotlinx.coroutines.delay(4200)
                        currentStepIdx++
                        displayedChars = 0
                    }
                }

                val currentStep = steps[currentStepIdx]
                val fullText = currentStep.first

                // Typewriter effect ticker with smooth, comfortably readable typing speed
                LaunchedEffect(currentStepIdx) {
                    displayedChars = 0
                    for (i in 1..fullText.length) {
                        displayedChars = i
                        kotlinx.coroutines.delay(75)
                    }
                }

                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = currentStep.second,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 600,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    label = "loaderProgress"
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.25f, maxAlpha = 0.65f)
                            )
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(72.dp),
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(24.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.15f, maxAlpha = 0.45f)
                        ) {
                            Text(
                                text = "LIVE MULTI-STORE ENGINE ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.3.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        // Typewriter text box with cursor
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fullText.take(displayedChars) + if (displayedChars < fullText.length) "▋" else "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        // Linear progress visualizer
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "Phase ${currentStepIdx + 1} of ${steps.size} • ${(animatedProgress * 100).toInt()}% Complete",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is SearchUiState.Success -> {
                ProductDetailsScreen(
                    product = state.product,
                    priceSnapshots = currentSnapshots,
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh(state.product.title) },
                    onSaveAlert = { target, store, img ->
                        viewModel.saveTargetAlert(
                            productTitle = state.product.title,
                            targetPrice = target,
                            currentPrice = state.product.lowestPrice ?: target,
                            store = store,
                            imageUrl = img
                        )
                    }
                )
            }
            is SearchUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ShoppingBag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No matching products found for '${state.query}'",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Try refining your search keyword or pasting a direct product URL.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            is SearchUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.resetToHome() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back to Discover")
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun HomeDiscoveryContent(
    recentSearches: List<String>,
    recentProducts: List<Product>,
    onSearchSelect: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val recentHeadingGlow = rememberBreathingColor(
        baseColor = MaterialTheme.colorScheme.onSurface,
        pulseColor = MaterialTheme.colorScheme.primary,
        durationMillis = 2400
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Quick Recent Searches Row
        if (recentSearches.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.7f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Recent Searches", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.bounceClick(targetScale = 0.94f) { onClearHistory() }
                        ) {
                            Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearches.forEach { query ->
                            SuggestionChip(
                                onClick = { onSearchSelect(query) },
                                label = { Text(query, fontSize = 12.sp) },
                                modifier = Modifier
                                    .bounceClick(targetScale = 0.95f) { onSearchSelect(query) }
                                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.05f, maxAlpha = 0.22f)
                            )
                        }
                    }
                }
            }
        }

        // Recently Checked Products (Home Page Glowing Cards for New Products)
        if (recentProducts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently Checked Products",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = recentHeadingGlow
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        modifier = Modifier.glowBackdrop(glowColor = Color(0xFF10B981), minAlpha = 0.2f, maxAlpha = 0.6f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "LIVE TRACKING",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            items(recentProducts) { product ->
                // The newest added product gets an intense glowing border and shimmer sweep
                val isLatestProduct = product == recentProducts.firstOrNull()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(targetScale = 0.98f) { onSearchSelect(product.title) }
                        .then(
                            if (isLatestProduct) {
                                Modifier
                                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.15f, maxAlpha = 0.45f)
                                    .neonGlow(
                                        color = MaterialTheme.colorScheme.primary,
                                        secondaryColor = Color(0xFF10B981),
                                        shape = RoundedCornerShape(16.dp),
                                        borderWidth = 1.4.dp
                                    )
                                    .shimmerSweep(shimmerColor = Color.White.copy(alpha = 0.12f), durationMillis = 2200)
                            } else {
                                Modifier
                                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.05f, maxAlpha = 0.2f)
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isLatestProduct) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!product.imageUrl.isNullOrBlank()) {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(product.imageUrl)
                                        .allowHardware(false)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = product.title,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (isLatestProduct) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(3.dp)
                                            .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.3f, maxAlpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "NEW",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            val lowest = product.lowestPrice
                            if (lowest != null) {
                                val lowestColor = if (isLatestProduct) {
                                    rememberBreathingColor(
                                        baseColor = Color(0xFF10B981),
                                        pulseColor = Color(0xFF34D399),
                                        durationMillis = 1600
                                    )
                                } else Color(0xFF10B981)

                                Text(
                                    text = "Lowest: ₹${"%,.0f".format(lowest)}",
                                    color = lowestColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "${product.stores.size} store offers checked",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else if (recentSearches.isEmpty()) {
            // Honest initial discovery prompt
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Search Live Multi-Store Prices",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Search across 12+ stores including Amazon, Flipkart, Croma, Reliance Digital, Tata CLiQ, Ajio, Nykaa, Meesho, Myntra, and eBay with instant price comparison.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
