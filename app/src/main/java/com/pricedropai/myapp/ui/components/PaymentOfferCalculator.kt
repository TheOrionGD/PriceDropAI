package com.pricedropai.myapp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricedropai.myapp.core.model.EmiPlan
import com.pricedropai.myapp.core.model.PaymentOffer
import com.pricedropai.myapp.core.model.PaymentOfferType
import kotlin.math.max

@Composable
fun PaymentOfferCalculator(
    basePrice: Double,
    offers: List<PaymentOffer>,
    emiPlans: List<EmiPlan> = emptyList(),
    isLoading: Boolean = false,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Instant Discounts, 1: No-Cost EMI
    var selectedOffer by remember { mutableStateOf<PaymentOffer?>(null) }
    var selectedEmiPlan by remember { mutableStateOf<EmiPlan?>(null) }
    var selectedMethodFilter by remember { mutableStateOf("ALL") } // ALL, CREDIT_CARD, DEBIT_CARD, UPI, EMI

    // Filter offers according to selected payment method
    val filteredOffers = remember(offers, selectedMethodFilter) {
        if (selectedMethodFilter == "ALL") {
            offers
        } else {
            offers.filter { offer ->
                val method = offer.paymentMethod?.uppercase() ?: ""
                when (selectedMethodFilter) {
                    "CREDIT_CARD" -> method.contains("CREDIT") || offer.cardType?.contains("CREDIT") == true
                    "DEBIT_CARD" -> method.contains("DEBIT") || offer.cardType?.contains("DEBIT") == true || offer.cardType == "ANY"
                    "UPI" -> method.contains("UPI") || offer.provider.contains("UPI", ignoreCase = true)
                    "EMI" -> offer.emiEligible || method.contains("EMI")
                    else -> true
                }
            }
        }
    }

    // Automatically calculate net discount based on backend offer or formula
    val discountAmount = remember(selectedOffer, basePrice) {
        val offer = selectedOffer ?: return@remember 0.0
        if (!offer.isEligible) return@remember 0.0

        if (offer.calculatedDiscount != null && offer.calculatedDiscount > 0) {
            offer.calculatedDiscount
        } else {
            when {
                offer.amount != null -> offer.amount
                offer.percentage != null -> {
                    val calculated = basePrice * (offer.percentage / 100.0)
                    if (offer.maximumDiscount != null) minOf(calculated, offer.maximumDiscount) else calculated
                }
                else -> 0.0
            }
        }
    }

    val isSelectedCashback = selectedOffer?.isCashback == true || selectedOffer?.type == PaymentOfferType.CASHBACK
    val netPayablePrice = if (isSelectedCashback) basePrice else max(0.0, basePrice - discountAmount)

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with primary tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Bank Offers & EMI Engine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (selectedOffer != null || selectedEmiPlan != null) {
                    TextButton(
                        onClick = {
                            selectedOffer = null
                            selectedEmiPlan = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Reset", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tab bar: Instant Discounts vs No-Cost EMI
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Instant Discounts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("No-Cost EMI", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // TAB 0: INSTANT DISCOUNTS & CASHBACK
            if (selectedTabIndex == 0) {
                // Payment Method Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "All Methods",
                        "CREDIT_CARD" to "Credit Card",
                        "DEBIT_CARD" to "Debit Card",
                        "UPI" to "UPI",
                        "EMI" to "EMI Eligible"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMethodFilter == key,
                            onClick = { selectedMethodFilter = key },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    isError -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Unable to connect to live banking offer service. Showing offline rules.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    filteredOffers.isEmpty() -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "No verified bank/payment promotions matching '$selectedMethodFilter' for this price point.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredOffers.forEach { offer ->
                                val isSelected = selectedOffer?.id == offer.id
                                val isEligible = offer.isEligible

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedOffer = if (isSelected) null else offer
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.CreditCard,
                                                    contentDescription = null,
                                                    tint = if (isEligible) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = offer.provider,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            // Eligibility badge
                                            Surface(
                                                color = if (isEligible) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (isEligible) "ELIGIBLE" else "NOT ELIGIBLE",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isEligible) Color(0xFF10B981) else Color(0xFFEF4444),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = offer.description,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (!isEligible && !offer.ineligibilityReason.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Reason: ${offer.ineligibilityReason}",
                                                fontSize = 11.sp,
                                                color = Color(0xFFEF4444),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (offer.minimumPurchase != null && offer.minimumPurchase > 0) {
                                                Text(
                                                    text = "Min Purchase: ₹${"%,.0f".format(offer.minimumPurchase)}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (offer.maximumDiscount != null) {
                                                Text(
                                                    text = "Max Savings: ₹${"%,.0f".format(offer.maximumDiscount)}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (!offer.terms.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = offer.terms,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Summary Calculation Card
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Net Effective Price:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (selectedOffer != null && discountAmount > 0) {
                                    val benefitType = if (isSelectedCashback) "Cashback Benefit" else "Instant Discount"
                                    Text(
                                        text = "${selectedOffer?.provider} ($benefitType: -₹${"%,.0f".format(discountAmount)})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                text = "₹${"%,.0f".format(netPayablePrice)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = Color(0xFF10B981)
                            )
                        }

                        if (isSelectedCashback && discountAmount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ℹ️ Cashback of ₹${"%,.0f".format(discountAmount)} is credited post-delivery to statement / wallet (Upfront payable amount: ₹${"%,.0f".format(basePrice)}).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            } else {
                // TAB 1: NO-COST EMI
                if (basePrice < 3000) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "No-Cost EMI is available on purchases of ₹3,000 or higher.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Current product price: ₹${"%,.0f".format(basePrice)}. Add eligible accessories or select higher storage models to unlock EMI tenures.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (emiPlans.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No verified bank No-Cost EMI plans currently available for this merchant.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        emiPlans.forEach { plan ->
                            val isSelected = selectedEmiPlan?.id == plan.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEmiPlan = if (isSelected) null else plan }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = plan.displayName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${plan.tenureMonths} Monthly Installments",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "₹${"%,.0f".format(plan.monthlyInstallment)} / mo",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp,
                                                color = Color(0xFF10B981)
                                            )
                                            if (plan.isNoCostEmi) {
                                                Surface(
                                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "NO-COST EMI",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total Installment: ₹${"%,.0f".format(plan.totalInstallmentAmount)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Bank Fee + GST: ₹${"%,.0f".format(plan.processingFee + plan.taxes)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (plan.subventionDiscount > 0) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Upfront Merchant Interest Discount: -₹${"%,.0f".format(plan.subventionDiscount)}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    if (!plan.terms.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = plan.terms,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedEmiPlan != null) {
                            val plan = selectedEmiPlan!!
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Selected Financing Breakdown (${plan.displayName}):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "• Installment: ${plan.tenureMonths} × ₹${"%,.0f".format(plan.monthlyInstallment)} = ₹${"%,.0f".format(plan.totalInstallmentAmount)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "• Processing Fee: ₹${"%,.0f".format(plan.processingFee)} + 18% GST (₹${"%,.0f".format(plan.taxes)})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "• Effective Total Outflow: ₹${"%,.0f".format(plan.effectiveTotalCost)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
