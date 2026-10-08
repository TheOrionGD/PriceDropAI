package com.example.pricedropai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: TrackerViewModel by viewModels()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        handleIncomingShareIntent(intent)

        setContent {
            MaterialTheme {
                MainAppContainer(
                    viewModel = viewModel,
                    sessionManager = sessionManager
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val parsedQuery = UrlProductParser.extractSearchQuery(sharedText)
                viewModel.search(parsedQuery)
            }
        }
    }
}

@Composable
fun MainAppContainer(
    viewModel: TrackerViewModel,
    sessionManager: SessionManager
) {
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    if (!isLoggedIn) {
        LoginScreen(
            onLoginSuccess = { name ->
                sessionManager.saveLogin(name)
                isLoggedIn = true
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(tonalElevation = 6.dp) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            viewModel.resetToHome()
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Discover") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.SmartToy, contentDescription = "AI Copilot") },
                        label = { Text("AI Copilot") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Watchlist") },
                        label = { Text("Watchlist") }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(viewModel = viewModel)
                    1 -> CopilotScreen(
                        viewModel = viewModel,
                        onProductSelect = { query ->
                            selectedTab = 0
                            viewModel.search(query)
                        }
                    )
                    2 -> WatchlistScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("PriceDrop AI", fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                "Real-time multi-store deals & smart price prediction",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Your Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            errorMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.length < 4) {
                        errorMsg = "Enter a valid name and 4+ character password."
                    } else {
                        onLoginSuccess(username.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Get Started", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: TrackerViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search iPhone, Nike, MacBook, Sony...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                focusManager.clearFocus()
                                viewModel.search(searchQuery)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        when (val state = viewModel.uiState) {
            is UiState.Idle -> {
                HomeDiscoveryView(
                    onProductClick = { query ->
                        searchQuery = query
                        viewModel.search(query)
                    }
                )
            }
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Comparing Flipkart, Amazon, Meesho, Myntra...", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
            is UiState.Success -> {
                ProductResultsView(
                    product = state.data,
                    onSaveAlert = { target ->
                        viewModel.saveTargetAlert(state.data.productName, target)
                    }
                )
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun HomeDiscoveryView(onProductClick: (String) -> Unit) {
    val categories = remember { LocalPriceEngine.getCategories() }
    val banners = remember { LocalPriceEngine.getPromoBanners() }
    val suggested = remember { LocalPriceEngine.getSuggestedProducts() }
    val flashDeals = remember { LocalPriceEngine.getFlashDeals() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(categories) { cat ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onProductClick(cat.searchQuery) }
                            .width(68.dp)
                    ) {
                        AsyncImage(
                            model = cat.iconUrl,
                            contentDescription = cat.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(cat.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(banners) { banner ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .width(290.dp)
                            .height(140.dp)
                            .clickable { onProductClick(banner.query) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = banner.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.42f))
                                    .padding(14.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Column {
                                    Text(banner.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Text(banner.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Suggested For You", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                suggested.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProductClick(item.query) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(2.dp))
                                Text("Starts at ₹${"%,.0f".format(item.price)}", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                            Surface(color = Color(0xFF388E3C), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "★ ${item.rating}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Trending Flash Deals", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                flashDeals.forEach { deal ->
                    val context = LocalContext.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deal.url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = deal.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(deal.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("₹${"%,.0f".format(deal.dealPrice)}", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${deal.discountPercent}% OFF", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Text("on ${deal.storeName}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductResultsView(
    product: ProductReport,
    onSaveAlert: (Double) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var inputPrice by remember { mutableStateOf((product.lowestPrice * 0.95).toInt().toString()) }
    var selectedFilter by remember { mutableIntStateOf(0) }
    var selectedBankOffer by remember { mutableStateOf<BankOffer?>(null) }

    val effectiveLowestPrice = remember(product.lowestPrice, selectedBankOffer) {
        val discount = selectedBankOffer?.discountAmount ?: 0.0
        maxOf(0.0, product.lowestPrice - discount)
    }

    val displayedStores = remember(product.stores, selectedFilter) {
        when (selectedFilter) {
            0 -> product.stores.sortedBy { it.price }
            1 -> product.stores.sortedBy {
                when {
                    it.deliveryDays.contains("Tomorrow") -> 1
                    it.deliveryDays.contains("Same Day") -> 2
                    it.deliveryDays.contains("1-2 Days") -> 3
                    it.deliveryDays.contains("2-3 Days") -> 4
                    else -> 5
                }
            }
            2 -> product.stores.filter { it.inStock }
            else -> product.stores
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Set Target Price Alert", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(product.productName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Current Lowest: ₹${"%,.0f".format(product.lowestPrice)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { inputPrice = it },
                        label = { Text("Alert me when price drops below") },
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
                            onSaveAlert(p)
                            showDialog = false
                            Toast.makeText(context, "Tracker active for ₹${"%,.0f".format(p)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Alert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            .height(210.dp)
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.productName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            IconButton(
                                onClick = { showDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = "Set Alert",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = product.productName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF388E3C),
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
                            Spacer(Modifier.width(8.dp))
                            Text(product.ratingCount, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // AI Review Summarizer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI Review Summary", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }

                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${product.reviewSummary.sentimentScore}% Positive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(product.reviewSummary.totalAnalyzed, fontSize = 10.sp, color = Color.Gray)

                    Spacer(Modifier.height(12.dp))

                    Text("What Buyers Love:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Spacer(Modifier.height(4.dp))
                    product.reviewSummary.pros.forEach { pro ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Text("✓ ", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Text(pro, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text("Watch Out For:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    Spacer(Modifier.height(4.dp))
                    product.reviewSummary.cons.forEach { con ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Text("✕ ", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Text(con, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }

        // Festive Sale Banner
        product.festiveSale?.let { sale ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6366F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Celebration, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Mega Sale Alert (${sale.saleName})",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFF4F46E5)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = sale.bannerNote,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Bank Offer Calculator
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Bank Offer Calculator", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (selectedBankOffer != null) {
                            TextButton(onClick = { selectedBankOffer = null }) {
                                Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        product.bankOffers.forEach { offer ->
                            val isSelected = selectedBankOffer?.id == offer.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBankOffer = if (isSelected) null else offer
                                },
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(offer.bankName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("-₹${"%,.0f".format(offer.discountAmount)}", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Black)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Net Effective Price:", fontSize = 12.sp, color = Color.Gray)
                                if (selectedBankOffer != null) {
                                    Text("Includes ${selectedBankOffer?.bankName} (-₹${"%,.0f".format(selectedBankOffer?.discountAmount ?: 0.0)})", fontSize = 10.sp, color = Color(0xFF10B981))
                                }
                            }
                            Text(
                                text = "₹${"%,.0f".format(effectiveLowestPrice)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
        }

        // Buy/Wait Recommendation
        item {
            val isBuyNow = product.recommendation.contains("BUY")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBuyNow) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (isBuyNow) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFF59E0B).copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isBuyNow) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = product.recommendation,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = if (isBuyNow) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                        Text(
                            text = "Standard Lowest: ₹${"%,.0f".format(product.lowestPrice)}  •  AI Target: ${product.predictedPrice}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 30-Day Trend Chart
        item {
            Column {
                Text("30-Day Market Trend", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    ) {
                        val maxPrice = product.history.maxOrNull() ?: 1.0
                        val minPrice = product.history.minOrNull() ?: 0.0
                        val range = if (maxPrice == minPrice) 1.0 else maxPrice - minPrice
                        val spacing = size.width / (product.history.size - 1)
                        val path = Path()

                        product.history.forEachIndexed { i, p ->
                            val x = i * spacing
                            val norm = ((p - minPrice) / range).toFloat()
                            val y = size.height - (norm * size.height)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(Color(0xFF0284C7), 4.dp.toPx(), Offset(x, y))
                        }
                        drawPath(path, Color(0xFF0284C7), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }

        // 4 Store Deals Section
        item {
            Column {
                Text(
                    text = "Live Store Deals (${displayedStores.size} Online)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == 0,
                        onClick = { selectedFilter = 0 },
                        label = { Text("Lowest Price", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilter == 1,
                        onClick = { selectedFilter = 1 },
                        label = { Text("Fast Delivery", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilter == 2,
                        onClick = { selectedFilter = 2 },
                        label = { Text("In Stock Only", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

        items(displayedStores) { store ->
            val isLowest = store.price == product.lowestPrice
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(store.url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLowest) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isLowest) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(store.storeName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (isLowest) {
                                Spacer(Modifier.width(8.dp))
                                Surface(color = Color(0xFF10B981), shape = RoundedCornerShape(4.dp)) {
                                    Text("LOWEST", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Estimated: ${store.deliveryDays}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${"%,.0f".format(store.price)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = if (isLowest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (store.inStock) "In Stock" else "Sold Out",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (store.inStock) Color(0xFF10B981) else Color.Red
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CopilotScreen(
    viewModel: TrackerViewModel,
    onProductSelect: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val suggestionChips = listOf(
        "iPhone 15 drop time?",
        "Best headphones under 20k",
        "Top bank offers today",
        "When is the next mega sale?"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("PriceDrop AI Copilot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Ask about discounts, drops & store choices", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestionChips.forEach { chipText ->
                SuggestionChip(
                    onClick = {
                        viewModel.sendChatMessage(chipText)
                        scope.launch { listState.animateScrollToItem(viewModel.chatMessages.size) }
                    },
                    label = { Text(chipText, fontSize = 12.sp) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(viewModel.chatMessages) { msg ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (msg.isUser) 16.dp else 2.dp,
                            bottomEnd = if (msg.isUser) 2.dp else 16.dp
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.text,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }

                    msg.suggestedProduct?.let { p ->
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .width(280.dp)
                                .clickable { onProductSelect(p.query) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = p.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(p.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                    Text("Lowest: ₹${"%,.0f".format(p.price)}", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    Text("Tap to compare stores", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.isBotTyping) {
                item {
                    Text("AI Copilot is typing...", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask Copilot...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        viewModel.sendChatMessage(text)
                        scope.launch { listState.animateScrollToItem(viewModel.chatMessages.size) }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun WatchlistScreen(viewModel: TrackerViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Price Drop Watchlist", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Background alerts via WorkManager", fontSize = 11.sp, color = Color.Gray)
            }
            Badge {
                Text("${viewModel.watchlist.size}")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (viewModel.watchlist.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = Color.LightGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No products in watchlist", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("Tap the bell icon on any product to track it", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(viewModel.watchlist, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Target: ₹${"%,.0f".format(item.targetPrice)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }

                            IconButton(onClick = { viewModel.removeWatchlistItem(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}