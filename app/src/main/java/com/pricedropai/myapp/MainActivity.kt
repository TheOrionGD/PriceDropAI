package com.pricedropai.myapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.pricedropai.myapp.core.parser.UrlProductParser
import com.pricedropai.myapp.notification.NotificationHelper
import com.pricedropai.myapp.ui.TrackerViewModel
import com.pricedropai.myapp.ui.screens.CopilotScreen
import com.pricedropai.myapp.ui.screens.DiscoverScreen
import com.pricedropai.myapp.ui.screens.GetStartedScreen
import com.pricedropai.myapp.ui.screens.LandingScreen
import com.pricedropai.myapp.ui.screens.OnboardingScreen
import com.pricedropai.myapp.ui.screens.SettingsScreen
import com.pricedropai.myapp.ui.screens.SplashScreen
import com.pricedropai.myapp.ui.screens.WatchlistScreen
import com.pricedropai.myapp.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: TrackerViewModel by viewModels()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        NotificationHelper.ensureChannelCreated(this)

        handleIncomingIntent(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var currentThemeMode by remember { mutableStateOf(sessionManager.getThemeMode()) }
            var currentAccentHex by remember { mutableStateOf(sessionManager.getAccentColorHex()) }

            // Polling preference updates when returning to screens
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(400)
                    val newMode = sessionManager.getThemeMode()
                    val newHex = sessionManager.getAccentColorHex()
                    if (newMode != currentThemeMode) currentThemeMode = newMode
                    if (newHex != currentAccentHex) currentAccentHex = newHex
                }
            }

            val isDark = when (currentThemeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }

            val accentColor = remember(currentAccentHex) {
                try {
                    Color(android.graphics.Color.parseColor(currentAccentHex))
                } catch (e: Exception) {
                    if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)
                }
            }

            // Status bar configuration for Android screens
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        window.statusBarColor = if (isDark) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#F8FAFC")
                        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                    }
                }
            }

            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme(
                    primary = accentColor,
                    secondary = Color(0xFF10B981),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    surfaceVariant = Color(0xFF334155),
                    primaryContainer = accentColor.copy(alpha = 0.25f),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                ) else lightColorScheme(
                    primary = accentColor,
                    secondary = Color(0xFF059669),
                    background = Color(0xFFF8FAFC),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF1F5F9),
                    primaryContainer = accentColor.copy(alpha = 0.15f),
                    onBackground = Color(0xFF0F172A),
                    onSurface = Color(0xFF0F172A)
                )
            ) {
                // Request Notification Permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

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
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Deep link query from Notification
        val notificationQuery = intent.getStringExtra("OPEN_PRODUCT_QUERY")
        if (!notificationQuery.isNullOrBlank()) {
            viewModel.search(notificationQuery)
            return
        }

        // 2. Share Intent from browser or store apps
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val parsedQuery = UrlProductParser.extractSearchQuery(sharedText)
                if (parsedQuery.isNotBlank()) {
                    viewModel.search(parsedQuery)
                }
            }
        } else if (intent.action == Intent.ACTION_VIEW) {
            val dataUri = intent.dataString
            if (!dataUri.isNullOrBlank()) {
                val parsedQuery = UrlProductParser.extractSearchQuery(dataUri)
                if (parsedQuery.isNotBlank()) {
                    viewModel.search(parsedQuery)
                }
            }
        }
    }
}

enum class AppScreenState {
    SPLASH,
    ONBOARDING,
    LANDING,
    GET_STARTED,
    AUTH,
    MAIN_HUB
}

@Composable
fun MainAppContainer(
    viewModel: TrackerViewModel,
    sessionManager: SessionManager
) {
    var screenState by remember {
        mutableStateOf(
            if (sessionManager.isLoggedIn()) AppScreenState.MAIN_HUB else AppScreenState.SPLASH
        )
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = screenState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                (slideInHorizontally(animationSpec = tween(400)) { width -> width } + fadeIn(animationSpec = tween(400)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(400)) { width -> -width } + fadeOut(animationSpec = tween(400)))
            } else {
                (slideInHorizontally(animationSpec = tween(400)) { width -> -width } + fadeIn(animationSpec = tween(400)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(400)) { width -> width } + fadeOut(animationSpec = tween(400)))
            }
        },
        label = "screen_navigation_anim"
    ) { currentScreen ->
        when (currentScreen) {
            AppScreenState.SPLASH -> {
                SplashScreen(
                    onSplashFinished = {
                        if (sessionManager.isLoggedIn()) {
                            screenState = AppScreenState.MAIN_HUB
                        } else {
                            screenState = AppScreenState.ONBOARDING
                        }
                    }
                )
            }
            AppScreenState.ONBOARDING -> {
                OnboardingScreen(
                    onFinishOnboarding = {
                        screenState = AppScreenState.LANDING
                    }
                )
            }
            AppScreenState.LANDING -> {
                LandingScreen(
                    onNavigateToAuth = {
                        screenState = AppScreenState.GET_STARTED
                    }
                )
            }
            AppScreenState.GET_STARTED -> {
                GetStartedScreen(
                    onNavigateToLogin = {
                        screenState = AppScreenState.AUTH
                    }
                )
            }
            AppScreenState.AUTH -> {
                LoginScreen(
                    sessionManager = sessionManager,
                    onLoginSuccess = {
                        screenState = AppScreenState.MAIN_HUB
                    }
                )
            }
            AppScreenState.MAIN_HUB -> {
                AnimatedContent(
                    targetState = showSettingsScreen,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically(animationSpec = tween(400)) { height -> height } + fadeIn(animationSpec = tween(400)))
                                .togetherWith(slideOutVertically(animationSpec = tween(400)) { height -> -height / 3 } + fadeOut(animationSpec = tween(400)))
                        } else {
                            (slideInVertically(animationSpec = tween(400)) { height -> -height / 3 } + fadeIn(animationSpec = tween(400)))
                                .togetherWith(slideOutVertically(animationSpec = tween(400)) { height -> height } + fadeOut(animationSpec = tween(400)))
                        }
                    },
                    label = "settings_modal_anim"
                ) { isSettingsOpen ->
                    if (isSettingsOpen) {
                        SettingsScreen(
                            sessionManager = sessionManager,
                            onBack = { showSettingsScreen = false },
                            onLogout = {
                                showSettingsScreen = false
                                screenState = AppScreenState.AUTH
                            }
                        )
                    } else {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 8.dp
                                ) {
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = {
                                            selectedTab = 0
                                            viewModel.resetToHome()
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Default.Explore,
                                                contentDescription = "Discover",
                                                modifier = if (selectedTab == 0) Modifier.glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.7f) else Modifier
                                            )
                                        },
                                        label = { Text("Discover", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        icon = {
                                            Icon(
                                                Icons.Default.SmartToy,
                                                contentDescription = "AI Copilot",
                                                modifier = if (selectedTab == 1) Modifier.glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.7f) else Modifier
                                            )
                                        },
                                        label = { Text("AI Copilot", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        icon = {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = "Watchlist",
                                                modifier = if (selectedTab == 2) Modifier.glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.7f) else Modifier
                                            )
                                        },
                                        label = { Text("Watchlist", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        ) { padding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(padding)
                            ) {
                                AnimatedContent(
                                    targetState = selectedTab,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInHorizontally(animationSpec = tween(350)) { width -> width } + fadeIn(animationSpec = tween(350)))
                                                .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { width -> -width } + fadeOut(animationSpec = tween(350)))
                                        } else {
                                            (slideInHorizontally(animationSpec = tween(350)) { width -> -width } + fadeIn(animationSpec = tween(350)))
                                                .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { width -> width } + fadeOut(animationSpec = tween(350)))
                                        }
                                    },
                                    label = "tab_switch_anim"
                                ) { tab ->
                                    when (tab) {
                                        0 -> DiscoverScreen(
                                            viewModel = viewModel,
                                            onOpenSettings = { showSettingsScreen = true }
                                        )
                                        1 -> CopilotScreen(
                                            viewModel = viewModel,
                                            onProductSelect = { query ->
                                                selectedTab = 0
                                                viewModel.search(query)
                                            }
                                        )
                                        2 -> WatchlistScreen(
                                            viewModel = viewModel,
                                            onProductSelect = { query ->
                                                selectedTab = 0
                                                viewModel.search(query)
                                            },
                                            onOpenSettings = {
                                                showSettingsScreen = true
                                            }
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
}

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginSuccess: () -> Unit
) {
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
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .glowBackdrop(glowColor = Color(0xFF6366F1), minAlpha = 0.3f, maxAlpha = 0.8f)
                    .clip(CircleShape)
                    .neonGlow(color = Color(0xFF6366F1), secondaryColor = Color(0xFF38BDF8), shape = CircleShape, borderWidth = 1.5.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                        )
                    )
                    .shimmerSweep(shimmerColor = Color.White.copy(alpha = 0.15f), durationMillis = 2000),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "PriceDrop AI Logo",
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text("PriceDrop AI", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Data-driven multi-store price tracker, price-drop alerts & AI shopping assistant",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMsg = null
                },
                label = { Text("Your Name / Username") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMsg = null
                },
                label = { Text("Password (4+ chars)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            errorMsg?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.length < 4) {
                        errorMsg = "Please enter a valid username and a 4+ character password."
                    } else {
                        val success = sessionManager.loginOrRegister(username, password)
                        if (success) {
                            onLoginSuccess()
                        } else {
                            errorMsg = "Incorrect password for this user profile."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .bounceClick(targetScale = 0.96f) {
                        if (username.isBlank() || password.length < 4) {
                            errorMsg = "Please enter a valid username and a 4+ character password."
                        } else {
                            val success = sessionManager.loginOrRegister(username, password)
                            if (success) {
                                onLoginSuccess()
                            } else {
                                errorMsg = "Incorrect password for this user profile."
                            }
                        }
                    }
                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.25f, maxAlpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Get Started", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}