package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
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
import com.example.engine.BacktestParams
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SignalsAndLabScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("DIGIT AI", "BACKTEST LAB", "STRATEGIES")

    val digitStats by viewModel.currentDigitStats.collectAsState()
    val signal by viewModel.currentSignal.collectAsState()
    val selectedSymbol by viewModel.selectedSymbol.collectAsState()
    val backtestResult by viewModel.backtestResult.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val strategies by viewModel.allStrategies.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyanPrimary else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) BackgroundDark else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedTab) {
            0 -> DigitAnalysisTab(digitStats = digitStats, symbol = selectedSymbol, viewModel = viewModel)
            1 -> BacktestLabTab(
                viewModel = viewModel,
                isBacktesting = isBacktesting,
                result = backtestResult
            )
            2 -> StrategyLabTab(strategies = strategies)
        }
    }
}

@Composable
private fun DigitAnalysisTab(
    digitStats: com.example.engine.DigitStats?,
    symbol: String,
    viewModel: MainViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIGIT ENGINE TELEMETRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                        letterSpacing = 0.5.sp
                    )
                    StatusPill(
                        text = "MARKET: $symbol",
                        backgroundColor = SurfaceVariantDark,
                        textColor = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (digitStats != null && digitStats.totalCount > 0) {
                    DigitDistributionChart(
                        counts = digitStats.digitCounts,
                        percentages = digitStats.digitPercentages,
                        minDigit = digitStats.minFrequentDigit,
                        maxDigit = digitStats.maxFrequentDigit
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(
                            label = "Shannon Entropy",
                            value = String.format(java.util.Locale.US, "%.3f", digitStats.entropy),
                            subValue = "Uniform: 3.322",
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Coldest Digit",
                            value = "${digitStats.minFrequentDigit} (${digitStats.minFrequencyPct.toInt()}%)",
                            valueColor = BlueSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Hottest Digit",
                            value = "${digitStats.maxFrequentDigit} (${digitStats.maxFrequencyPct.toInt()}%)",
                            valueColor = ProfitGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(
                            label = "Even Parity",
                            value = "${digitStats.evenPct.toInt()}%",
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Odd Parity",
                            value = "${digitStats.oddPct.toInt()}%",
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Active Streak",
                            value = if (digitStats.currentStreakLength > 1) "${digitStats.currentStreakType} (${digitStats.currentStreakLength}x)" else "None",
                            valueColor = if (digitStats.currentStreakLength >= 3) WarningAmber else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Gambler's Fallacy Notice
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "ANTI-GAMBLER'S FALLACY GUARD: Past tick frequencies do not guarantee imminent reversion. Algorithmic trade decisions require entropy breakdown and statistically significant Chi-square confidence (p < 0.05).",
                            fontSize = 10.sp,
                            color = TextMuted,
                            lineHeight = 14.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Awaiting tick data from Deriv...", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BacktestLabTab(
    viewModel: MainViewModel,
    isBacktesting: Boolean,
    result: com.example.engine.BacktestResult?
) {
    var symbol by remember { mutableStateOf("R_100") }
    var contractType by remember { mutableStateOf("DIGITDIFF") }
    var initialBalanceStr by remember { mutableStateOf("1000") }
    var stakeStr by remember { mutableStateOf("5") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORICAL SIMULATION CONFIG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    StatusPill(text = "OUT-OF-SAMPLE", backgroundColor = SurfaceVariantDark, textColor = TextSecondary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it },
                        label = { Text("Symbol", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = contractType,
                        onValueChange = { contractType = it },
                        label = { Text("Contract Type", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = initialBalanceStr,
                        onValueChange = { initialBalanceStr = it },
                        label = { Text("Start Balance ($)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = stakeStr,
                        onValueChange = { stakeStr = it },
                        label = { Text("Stake ($)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val initBal = initialBalanceStr.toDoubleOrNull() ?: 1000.0
                        val st = stakeStr.toDoubleOrNull() ?: 5.0
                        viewModel.runBacktest(
                            BacktestParams(
                                strategyId = "strat_custom",
                                symbol = symbol,
                                contractType = contractType,
                                initialBalance = initBal,
                                fixedStake = st
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("run_backtest_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBacktesting) "SIMULATING..." else "RUN BACKTEST & MONTE CARLO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Backtest Results (PDF Section 17, 18, 19)
        if (result != null) {
            item {
                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SIMULATION RESULTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        StatusPill(
                            text = "NOT GUARANTEED PERFORMANCE",
                            backgroundColor = SurfaceVariantDark,
                            textColor = WarningAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(label = "Total Trades", value = "${result.totalTrades}", modifier = Modifier.weight(1f))
                        MetricBox(
                            label = "Win Rate",
                            value = "${result.winRatePct}%",
                            valueColor = if (result.winRatePct >= 55.0) ProfitGreen else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            label = "Net Return",
                            value = "${if (result.netProfit >= 0) "+$" else "-$"}${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(result.netProfit))}",
                            valueColor = if (result.netProfit >= 0) ProfitGreen else LossRed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(label = "Max Drawdown", value = "${result.maxDrawdownPct}%", valueColor = LossRed, modifier = Modifier.weight(1f))
                        MetricBox(label = "Profit Factor", value = "${result.profitFactor}", modifier = Modifier.weight(1f))
                        MetricBox(label = "Expectancy", value = "$${result.expectancy}", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(label = "Max Win Streak", value = "${result.longestWinStreak}x", modifier = Modifier.weight(1f))
                        MetricBox(label = "Max Loss Streak", value = "${result.longestLossStreak}x", modifier = Modifier.weight(1f))
                        MetricBox(label = "Ending Balance", value = "$${result.endingBalance}", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Monte Carlo Stress Test (PDF Section 19)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "MONTE CARLO ROBUSTNESS ANALYSIS (500 ITERATIONS)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MetricBox(
                                label = "Median Final Balance",
                                value = "$${result.monteCarloMedianBalance}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "95% Worst Drawdown",
                                value = "${result.monteCarloWorstDrawdownPct}%",
                                valueColor = LossRed,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "Prob. of Ruin",
                                value = "${result.monteCarloProbabilityOfRuinPct}%",
                                valueColor = if (result.monteCarloProbabilityOfRuinPct > 5.0) LossRed else ProfitGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StrategyLabTab(
    strategies: List<com.example.data.model.StrategyEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(strategies) { strat ->
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = strat.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "Version: ${strat.version} | ${strat.category}", fontSize = 10.sp, color = TextMuted)
                    }

                    val badgeColor = when (strat.status) {
                        "APPROVED" -> ProfitGreen
                        "PAPER" -> BadgePaperBg
                        "LIVE" -> CyanPrimary
                        else -> TextMuted
                    }
                    StatusPill(text = strat.status, backgroundColor = badgeColor.copy(alpha = 0.2f), textColor = badgeColor)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strat.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(label = "Backtest Win Rate", value = "${strat.winRatePct}%", modifier = Modifier.weight(1f))
                    MetricBox(label = "Trades Sampled", value = "${strat.totalTrades}", modifier = Modifier.weight(1f))
                    MetricBox(label = "Profit Factor", value = "${strat.profitFactor}", modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
