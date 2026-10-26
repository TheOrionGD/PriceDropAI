package com.pricedropai.myapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.pricedropai.myapp.core.model.Availability
import com.pricedropai.myapp.core.model.PriceSnapshot
import com.pricedropai.myapp.core.model.Product
import com.pricedropai.myapp.core.model.Store
import com.pricedropai.myapp.ui.components.InteractivePriceChart
import com.pricedropai.myapp.ui.components.PaymentOfferCalculator
import com.pricedropai.myapp.ui.components.ReviewSummarySection
import com.pricedropai.myapp.ui.components.StoreOfferCard
import com.pricedropai.myapp.ui.theme.*

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    product: Product,
    priceSnapshots: List<PriceSnapshot>,
    onSaveAlert: (targetPrice: Double, store: Store?, imageUrl: String?) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAlertDialog by remember { mutableStateOf(false) }
    var inputPrice by remember {
        mutableStateOf(
            product.lowestPrice?.let { (it * 0.95).toInt().toString() } ?: ""
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
    var selectedVariant by remember(product.variants) { mutableStateOf(product.variants.firstOrNull()) }
    var storeFilter by remember { mutableIntStateOf(0) } // 0: All, 1: Lowest first, 2: In stock

    val lowestPrice = product.lowestPrice
    val lowestOffer = product.lowestOffer

    val sortedOffers = remember(product.stores, storeFilter) {
        when (storeFilter) {
            1 -> {
                val available = product.stores.filter { it.price != null && it.price > 0 && it.availability != Availability.OUT_OF_STOCK }
                    .sortedBy { it.price }
                val unavailable = product.stores.filter { it.price == null || it.price <= 0 || it.availability == Availability.OUT_OF_STOCK }
                available + unavailable
            }
            2 -> product.stores.filter { it.price != null && it.price > 0 && it.availability != Availability.OUT_OF_STOCK }
            else -> product.stores
        }
    }

    var showRewardAnimation by remember { mutableStateOf(false) }

    if (showAlertDialog) {
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            title = { Text("Set Price Drop Alert", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = product.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(6.dp))
                    if (lowestPrice != null) {
                        Text(
                            text = "Current Lowest: ₹${"%,.0f".format(lowestPrice)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { inputPrice = it },
                        label = { Text("Notify when price drops below") },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        val p = inputPrice.toDoubleOrNull()
                        if (p != null && p > 0) {
                            onSaveAlert(p, lowestOffer?.store, product.imageUrl)
                            showAlertDialog = false
                            showRewardAnimation = true
                        }
                    }
                ) {
                    Text("Save Alert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlertDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRewardAnimation) {
        RewardSuccessModal(
            title = "Price Drop Tracker Activated! 🎉",
            subtitle = "You earned +50 PriceDrop Points! We're monitoring 12+ stores around the clock for your discount.",
            onDismiss = { showRewardAnimation = false }
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Product Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.08f, maxAlpha = 0.28f)
                    .neonGlow(
                        color = MaterialTheme.colorScheme.primary,
                        secondaryColor = Color(0xFF10B981),
                        shape = RoundedCornerShape(20.dp),
                        borderWidth = 1.2.dp
                    )
                    .shimmerSweep(shimmerColor = Color.White.copy(alpha = 0.07f), durationMillis = 2800),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!product.imageUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(product.imageUrl)
                                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                    .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                                    .crossfade(true)
                                    .allowHardware(false)
                                    .listener(
                                        onError = { _, result ->
                                            android.util.Log.e("CoilImage", "Failed to load product image: ${product.imageUrl}", result.throwable)
                                        },
                                        onSuccess = { _, _ ->
                                            android.util.Log.d("CoilImage", "Successfully rendered image: ${product.imageUrl}")
                                        }
                                    )
                                    .build(),
                                contentDescription = product.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                error = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ShoppingBag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(54.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = product.category ?: "Product Image",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ShoppingBag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = product.category ?: "Product Image",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!product.category.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = product.category,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (!product.brand.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = product.brand,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showAlertDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .bounceClick(targetScale = 0.90f) { showAlertDialog = true }
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.65f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Set Alert",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = product.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        if (product.rating != null) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF10B981),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("${product.rating}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                }
                                if (product.reviewCount != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "(${product.reviewCount} reviews)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Variants selector if available
                        if (product.variants.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Select Variant:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                product.variants.forEach { variant ->
                                    val isSelected = selectedVariant?.id == variant.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedVariant = variant },
                                        label = { Text(variant.name, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Interactive Price History Chart
        item {
            InteractivePriceChart(snapshots = priceSnapshots)
        }

        // Bank / Payment Discounts Calculator
        item {
            if (lowestPrice != null) {
                PaymentOfferCalculator(
                    basePrice = lowestPrice,
                    offers = product.paymentOffers,
                    emiPlans = product.emiPlans
                )
            }
        }

        // AI Review Summary
        item {
            ReviewSummarySection(
                summary = product.reviewSummary,
                avgRating = product.rating,
                totalReviews = product.reviewCount
            )
        }

        // Live Multi-Store Deals Section Header
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Live Store Offers (${sortedOffers.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = storeFilter == 0,
                        onClick = { storeFilter = 0 },
                        label = { Text("All Stores", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = storeFilter == 1,
                        onClick = { storeFilter = 1 },
                        label = { Text("Lowest Price First", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = storeFilter == 2,
                        onClick = { storeFilter = 2 },
                        label = { Text("In Stock Only", fontSize = 12.sp) }
                    )
                }
            }
        }

        // Dynamic Store Cards
        items(sortedOffers) { offer ->
            StoreOfferCard(
                offer = offer,
                isLowest = lowestOffer?.id == offer.id
            )
        }

        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}
}

@Composable
fun RewardSuccessModal(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "rewardScale"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFD97706))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 38.sp,
                        modifier = Modifier.scale(scale)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(text = "⭐ +50 POINTS EARNED", fontWeight = FontWeight.Black, color = Color(0xFFB45309), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Claim Reward & Continue", fontWeight = FontWeight.Bold)
            }
        }
    )
}
