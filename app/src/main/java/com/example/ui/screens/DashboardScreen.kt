package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AccountMode
import com.example.engine.SignalCandidate
import com.example.engine.TradingMode
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToMarkets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSymbol by viewModel.selectedSymbol.collectAsState()
    val signal by viewModel.currentSignal.collectAsState()
    val proposal by viewModel.currentProposal.collectAsState()
    val openOrders by viewModel.openOrders.collectAsState()
    val tradingMode by viewModel.executionEngine.tradingMode.collectAsState()
    val isEmergencyStopActive by viewModel.isEmergencyStopActive.collectAsState()
    val accountMode by viewModel.executionEngine.accountMode.collectAsState()
    val balance by viewModel.effectiveBalanceFlow.collectAsState()
    val balanceCurrency by viewModel.balanceCurrency.collectAsState()
    val tickHistory = viewModel.getTickHistory(selectedSymbol)
    val prices = tickHistory.map { it.quote }

    val openExposure = openOrders.sumOf { it.stake }
    val todayPnl = openOrders.filter { it.result != "PENDING" }.sumOf { it.profitLoss ?: 0.0 }
    val isAutoTrading = tradingMode == TradingMode.AUTONOMOUS

    var showAutoConfirmDialog by remember { mutableStateOf(false) }
    var autoRiskConfirmed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isEmergencyStopActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LossRedBg),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LossRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emergency_stop_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = LossRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EMERGENCY STOP ACTIVE",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = LossRed,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "All paper trading and autonomous orders are halted.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = { viewModel.clearEmergencyStop() },
                            colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("resume_trading_button")
                        ) {
                            Text(
                                text = "RESUME",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            ResponsibleTradingBanner()
        }

        // Account Overview Card (PDF Section 21)
        item {
            FintechCard(
                borderColor = CardBorderDark
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ACCOUNT PORTFOLIO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        IconButton(
                            onClick = { viewModel.refreshAccountBalance() },
                            modifier = Modifier.size(24.dp).testTag("refresh_balance_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Account Balance",
                                tint = CyanPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    StatusPill(
                        text = if (accountMode == AccountMode.REAL) "REAL MONEY ($balanceCurrency)" else "PAPER SIMULATION ($balanceCurrency)",
                        backgroundColor = if (accountMode == AccountMode.REAL) BadgeRealBg else BadgePaperBg
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(
                        label = "Balance",
                        value = "$${String.format(java.util.Locale.US, "%,.2f", balance)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Open Exposure",
                        value = "$${String.format(java.util.Locale.US, "%.2f", openExposure)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Session P/L",
                        value = "${if (todayPnl >= 0) "+$" else "-$"}${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(todayPnl))}",
                        valueColor = if (todayPnl >= 0) ProfitGreen else LossRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // AI Engine Status Grid (PDF Section 21)
        item {
            FintechCard {
                Text(
                    text = "AI ENGINE TELEMETRY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusPill(text = "AI: ONLINE", backgroundColor = SurfaceVariantDark, textColor = CyanPrimary)
                    StatusPill(text = "DATA: LIVE", backgroundColor = SurfaceVariantDark, textColor = ProfitGreen)
                    StatusPill(text = "RISK: ACTIVE", backgroundColor = SurfaceVariantDark, textColor = BlueSecondary)
                    StatusPill(
                        text = "AUTO: ${if (isAutoTrading) "ON" else "OFF"}",
                        backgroundColor = if (isAutoTrading) CyanGlow else SurfaceVariantDark,
                        textColor = if (isAutoTrading) CyanPrimary else TextMuted
                    )
                }
            }
        }

        // Current Active Market Chart
        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = viewModel.getSymbolDisplayName(selectedSymbol),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Deriv Live Feed: $selectedSymbol",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    TextButton(
                        onClick = onNavigateToMarkets,
                        colors = ButtonDefaults.textButtonColors(contentColor = CyanPrimary)
                    ) {
                        Text("SWITCH MARKET", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                InteractiveTickChart(prices = prices)
            }
        }

        // CURRENT AI SIGNAL Card (PDF Section 9, 10, 12, 21, 24, 67)
        item {
            val sig = signal
            FintechCard(
                borderColor = if (sig?.decision == "TRADE") ProfitGreen.copy(alpha = 0.6f) else CardBorderDark
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AI QUANTITATIVE SIGNAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = sig?.contractType ?: "DIGITDIFF",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanPrimary
                        )
                    }

                    val isTrade = sig?.decision == "TRADE"
                    StatusPill(
                        text = if (isTrade) "DECISION: TRADE" else "DECISION: NO TRADE",
                        backgroundColor = if (isTrade) ProfitGreenBg else LossRedBg,
                        textColor = if (isTrade) ProfitGreen else LossRed
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (sig != null) {
                    // Metrics Row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(
                            label = "Model Estimate",
                            value = "${String.format(java.util.Locale.US, "%.1f", sig.modelProbabilityPct)}%",
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Expected Value",
                            value = "${if (sig.expectedValuePct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", sig.expectedValuePct)}%",
                            valueColor = if (sig.expectedValuePct >= 0) ProfitGreen else LossRed,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Quality Score",
                            value = "${sig.signalQualityScore}/100",
                            valueColor = if (sig.signalQualityScore >= 70) CyanPrimary else WarningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(
                            label = "Historical OOS",
                            value = "${String.format(java.util.Locale.US, "%.1f", sig.historicalOosHitRatePct)}%",
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Smart Stake",
                            value = "$${String.format(java.util.Locale.US, "%.2f", sig.recommendedStake)}",
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Market Regime",
                            value = sig.regime,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // "WHY THIS SIGNAL?" Panel (PDF Section 24)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "WHY THIS SIGNAL?",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        sig.reasonsList.forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons (PDF Section 21)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ANALYZE
                        OutlinedButton(
                            onClick = { viewModel.requestFreshProposal(selectedSymbol) },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                            modifier = Modifier.weight(1f).testTag("analyze_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ANALYZE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // PAPER TRADE
                        Button(
                            onClick = { viewModel.executeManualOrder(sig, isPaper = true) },
                            enabled = !isEmergencyStopActive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEmergencyStopActive) SurfaceVariantDark else BadgePaperBg,
                                disabledContainerColor = SurfaceVariantDark,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("paper_trade_button")
                        ) {
                            Text(if (isEmergencyStopActive) "HALTED" else "PAPER TRADE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // APPROVE REAL TRADE
                        Button(
                            onClick = { viewModel.executeManualOrder(sig, isPaper = false) },
                            enabled = !isEmergencyStopActive && sig.decision == "TRADE",
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f).testTag("approve_trade_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEmergencyStopActive) "HALTED" else "APPROVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto Trading Toggle Button (Section 15, 86)
                    Button(
                        onClick = {
                            if (isEmergencyStopActive) {
                                viewModel.clearEmergencyStop()
                            } else if (isAutoTrading) {
                                viewModel.setAutonomousTrading(false)
                            } else {
                                showAutoConfirmDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEmergencyStopActive || isAutoTrading) LossRed else SurfaceVariantDark,
                            contentColor = if (isEmergencyStopActive || isAutoTrading) Color.White else CyanPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isEmergencyStopActive || isAutoTrading) LossRed else CyanPrimary, RoundedCornerShape(8.dp))
                            .testTag("auto_trading_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isEmergencyStopActive) Icons.Default.Warning else if (isAutoTrading) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEmergencyStopActive) "EMERGENCY STOP ACTIVE - TAP TO RESUME" else if (isAutoTrading) "STOP AUTONOMOUS TRADING" else "ENABLE AUTONOMOUS TRADING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Two-step Confirmation Dialog for Autonomous Mode (PDF Section 86)
    if (showAutoConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showAutoConfirmDialog = false
                autoRiskConfirmed = false
            },
            title = {
                Text("Enable Autonomous Trading", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Autonomous trading allows the AI to automatically analyze Deriv ticks, score expected value, and dispatch approved contract proposals according to your configured Global Risk limits.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { autoRiskConfirmed = !autoRiskConfirmed }
                    ) {
                        Checkbox(
                            checked = autoRiskConfirmed,
                            onCheckedChange = { autoRiskConfirmed = it },
                            colors = CheckboxDefaults.colors(checkedColor = CyanPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I understand that this will place automated algorithmic trades subject to risk limits.",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setAutonomousTrading(true)
                        showAutoConfirmDialog = false
                        autoRiskConfirmed = false
                    },
                    enabled = autoRiskConfirmed,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark)
                ) {
                    Text("ENABLE")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAutoConfirmDialog = false
                    autoRiskConfirmed = false
                }) {
                    Text("CANCEL")
                }
            },
            containerColor = SurfaceDark
        )
    }
}
