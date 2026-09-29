package com.example

import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AccountMode
import com.example.engine.TradingMode
import com.example.ui.components.DerivAiHeaderBar
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

enum class NavigationScreen(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Dashboard),
    MARKETS("Markets", Icons.Default.ShowChart),
    AI_SIGNALS("AI Lab", Icons.Default.Psychology),
    ORDERS("Orders", Icons.Default.ReceiptLong),
    ACCOUNT("Account", Icons.Default.AccountCircle)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                DerivAiApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DerivAiApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(NavigationScreen.DASHBOARD) }
    var inAdminScreen by remember { mutableStateOf(false) }
    var detailSheetSymbol by remember { mutableStateOf<String?>(null) }
    var showRealConfirmDialog by remember { mutableStateOf(false) }
    var realConfirmChecked by remember { mutableStateOf(false) }

    val connectionState by viewModel.webSocketClient.connectionState.collectAsState()
    val accountMode by viewModel.executionEngine.accountMode.collectAsState()
    val tradingMode by viewModel.executionEngine.tradingMode.collectAsState()
    val isEmergencyStopActive by viewModel.isEmergencyStopActive.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val balance = viewModel.getEffectiveBalance()

    fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (_: Exception) {}
    }

    // Handle back button when inside modal or admin screen
    if (inAdminScreen) {
        BackHandler { inAdminScreen = false }
    } else if (detailSheetSymbol != null) {
        BackHandler { detailSheetSymbol = null }
    } else if (currentScreen != NavigationScreen.DASHBOARD) {
        BackHandler { currentScreen = NavigationScreen.DASHBOARD }
    }

    if (inAdminScreen) {
        AdminScreen(viewModel = viewModel, onBack = { inAdminScreen = false })
        return
    }

    Scaffold(
        topBar = {
            DerivAiHeaderBar(
                connectionState = connectionState,
                accountMode = accountMode,
                balance = balance,
                isAutonomousActive = tradingMode == TradingMode.AUTONOMOUS,
                isEmergencyStopActive = isEmergencyStopActive,
                onEmergencyStop = {
                    triggerVibration()
                    viewModel.toggleEmergencyStop()
                },
                onAccountModeClick = {
                    if (accountMode == AccountMode.DEMO) {
                        showRealConfirmDialog = true
                    } else {
                        viewModel.executionEngine.setAccountMode(AccountMode.DEMO)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextPrimary,
                tonalElevation = 6.dp
            ) {
                NavigationScreen.values().forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BackgroundDark,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = {
            if (userMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearUserMessage() }) {
                            Text("OK", color = CyanPrimary)
                        }
                    },
                    containerColor = SurfaceVariantDark,
                    contentColor = TextPrimary
                ) {
                    Text(userMessage!!, fontSize = 12.sp)
                }
            }
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                NavigationScreen.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToMarkets = { currentScreen = NavigationScreen.MARKETS }
                )
                NavigationScreen.MARKETS -> MarketsScreen(
                    viewModel = viewModel,
                    onMarketSelected = { sym -> detailSheetSymbol = sym }
                )
                NavigationScreen.AI_SIGNALS -> SignalsAndLabScreen(
                    viewModel = viewModel
                )
                NavigationScreen.ORDERS -> OrdersScreen(
                    viewModel = viewModel
                )
                NavigationScreen.ACCOUNT -> AccountSettingsScreen(
                    viewModel = viewModel,
                    onOpenAdminPanel = { inAdminScreen = true }
                )
            }

            // Market Detail Sheet Overlay
            if (detailSheetSymbol != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { detailSheetSymbol = null },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(modifier = Modifier.clickable(enabled = false) {}) {
                        MarketDetailSheet(
                            symbol = detailSheetSymbol!!,
                            viewModel = viewModel,
                            onDismiss = { detailSheetSymbol = null }
                        )
                    }
                }
            }
        }
    }

    // Two-step Real-Money Trading Confirmation (PDF Section 46 & 86)
    if (showRealConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showRealConfirmDialog = false
                realConfirmChecked = false
            },
            title = {
                Text(
                    text = "REAL-MONEY TRADING CONFIRMATION",
                    color = LossRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "You are switching to REAL trading mode. Any subsequent trades approved or executed will place REAL-MONEY orders against your authorized Deriv account balance.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { realConfirmChecked = !realConfirmChecked }
                    ) {
                        Checkbox(
                            checked = realConfirmChecked,
                            onCheckedChange = { realConfirmChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = LossRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I understand that this will place real-money trades on my connected Deriv account.",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.executionEngine.setAccountMode(AccountMode.REAL)
                        showRealConfirmDialog = false
                        realConfirmChecked = false
                    },
                    enabled = realConfirmChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White)
                ) {
                    Text("ENABLE REAL TRADING")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRealConfirmDialog = false
                    realConfirmChecked = false
                }) {
                    Text("CANCEL")
                }
            },
            containerColor = SurfaceDark
        )
    }
}
