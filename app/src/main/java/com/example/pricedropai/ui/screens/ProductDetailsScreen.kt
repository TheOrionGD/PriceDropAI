package com.example.pricedropai.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.pricedropai.core.model.PriceSnapshot
import com.example.pricedropai.core.model.Product
import com.example.pricedropai.core.model.Store
import com.example.pricedropai.ui.components.InteractivePriceChart
import com.example.pricedropai.ui.components.PaymentOfferCalculator
import com.example.pricedropai.ui.components.ReviewSummarySection
import com.example.pricedropai.ui.components.StoreOfferCard

@Composable
fun ProductDetailsScreen(
    product: Product,
    priceSnapshots: List<PriceSnapshot>,
    onSaveAlert: (targetPrice: Double, store: Store?, imageUrl: String?) -> Unit
) {
    val context = LocalContext.current
    var showAlertDialog by remember { mutableStateOf(false) }
    var inputPrice by remember {
        mutableStateOf(
            product.lowestPrice?.let { (it * 0.95).toInt().toString() } ?: ""
        )
    }
    var selectedVariant by remember(product.variants) { mutableStateOf(product.variants.firstOrNull()) }
    var storeFilter by remember { mutableIntStateOf(0) } // 0: All, 1: Lowest first, 2: In stock

    val lowestPrice = product.lowestPrice
    val lowestOffer = product.lowestOffer

    val sortedOffers = remember(product.stores, storeFilter) {
        when (storeFilter) {
            1 -> product.stores.sortedBy { it.price ?: Double.MAX_VALUE }
            2 -> product.stores.filter { it.price != null && it.price > 0 }
            else -> product.stores
        }
    }

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
                            Toast.makeText(context, "Price alert saved for ₹${"%,.0f".format(p)}", Toast.LENGTH_SHORT).show()
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

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Product Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
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
                                    .crossfade(true)
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
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            IconButton(
                                onClick = { showAlertDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
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
                    offers = product.paymentOffers
                )
            }
        }

        // AI Review Summary
        item {
            ReviewSummarySection(summary = product.reviewSummary)
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
