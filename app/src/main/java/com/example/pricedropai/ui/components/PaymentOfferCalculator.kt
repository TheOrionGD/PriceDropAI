package com.example.pricedropai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pricedropai.core.model.PaymentOffer
import kotlin.math.max

@Composable
fun PaymentOfferCalculator(
    basePrice: Double,
    offers: List<PaymentOffer>,
    modifier: Modifier = Modifier
) {
    var selectedOffer by remember { mutableStateOf<PaymentOffer?>(null) }

    val discountAmount = remember(selectedOffer, basePrice) {
        val offer = selectedOffer ?: return@remember 0.0
        when {
            offer.amount != null -> offer.amount
            offer.percentage != null -> {
                val calculated = basePrice * (offer.percentage / 100.0)
                if (offer.maximumDiscount != null) minOf(calculated, offer.maximumDiscount) else calculated
            }
            else -> 0.0
        }
    }

    val netPrice = max(0.0, basePrice - discountAmount)

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Payment Discounts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                if (selectedOffer != null) {
                    TextButton(onClick = { selectedOffer = null }) {
                        Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (offers.isEmpty()) {
                Text(
                    text = "No verified bank/payment offers currently applicable.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    offers.forEach { offer ->
                        val isSelected = selectedOffer?.id == offer.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedOffer = if (isSelected) null else offer },
                            label = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(offer.provider, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(offer.description, fontSize = 10.sp, color = Color(0xFF10B981))
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Net Effective Price:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selectedOffer != null && discountAmount > 0) {
                            Text(
                                "Includes ${selectedOffer?.provider} (-₹${"%,.0f".format(discountAmount)})",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = "₹${"%,.0f".format(netPrice)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}
