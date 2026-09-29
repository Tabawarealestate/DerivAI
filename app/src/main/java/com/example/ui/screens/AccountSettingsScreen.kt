package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RiskSettingsEntity
import com.example.engine.AccountMode
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AccountSettingsScreen(
    viewModel: MainViewModel,
    onOpenAdminPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authorizedAccount by viewModel.webSocketClient.authorizedAccount.collectAsState()
    val activeSub by viewModel.activeSubscription.collectAsState()
    val riskSettings by viewModel.riskSettingsFlow.collectAsState()
    val accountMode by viewModel.executionEngine.accountMode.collectAsState()

    var tokenInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var redemptionStatus by remember { mutableStateOf<String?>(null) }
    var showRiskConfigDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            ResponsibleTradingBanner()
        }

        // Section 4: Referral / Account Creation
        item {
            FintechCard(
                borderColor = CyanPrimary.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "DON'T HAVE A DERIV ACCOUNT?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create an official account with Deriv to unlock live trading access. Referral Code: MXNC5FXXFLGJ",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.deriv.link?t=DFWK7P758YVU"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("create_deriv_account_button")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CREATE DERIV ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Deriv Connection (Section 44 & 45)
        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DERIV API CONNECTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    StatusPill(
                        text = if (authorizedAccount != null) "AUTHORIZED" else "UNAUTHORIZED",
                        backgroundColor = if (authorizedAccount != null) ProfitGreenBg else SurfaceVariantDark,
                        textColor = if (authorizedAccount != null) ProfitGreen else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (authorizedAccount != null) {
                    val acc = authorizedAccount!!
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(label = "Login ID", value = acc.loginId, modifier = Modifier.weight(1f))
                        MetricBox(label = "Currency", value = acc.currency, modifier = Modifier.weight(1f))
                        MetricBox(
                            label = "Live Balance",
                            value = "$${String.format(java.util.Locale.US, "%,.2f", acc.balance)}",
                            valueColor = ProfitGreen,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scopes: ${acc.scopes.joinToString(", ")}",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            text = if (acc.isVirtual) "DEMO / VIRTUAL" else "REAL FUNDS",
                            backgroundColor = if (acc.isVirtual) BadgePaperBg else BadgeRealBg,
                            textColor = if (acc.isVirtual) TextSecondary else ProfitGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.refreshAccountBalance() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("refresh_account_funds_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("REFRESH FUNDS (DERIV API)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Connect your Deriv API token to retrieve real-time account funds, balance updates, and trade execution permissions.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Deriv API Token", fontSize = 10.sp) },
                        placeholder = { Text("e.g. 1a2b3c4d5e6f7g8", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("deriv_token_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.connectDerivAccount(tokenInput)
                                tokenInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("connect_deriv_button")
                        ) {
                            Text("AUTHORIZE & RETRIEVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                tokenInput = "demo_virtual_trader_token"
                                viewModel.connectDerivAccount("demo_virtual_trader_token")
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                            modifier = Modifier.weight(1f).testTag("load_demo_funds_button")
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DEMO FUNDS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Subscription & Access Code (Section 28 & 29)
        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SUBSCRIPTION & ACCESS CODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    StatusPill(
                        text = if (activeSub != null) activeSub!!.subscriptionPlan else "FREE TRIAL",
                        backgroundColor = CyanGlow,
                        textColor = CyanPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeSub != null) {
                    val expDateStr = if (activeSub!!.expirationDate != null) {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(activeSub!!.expirationDate!!))
                    } else "Never"
                    Text(
                        text = "Active License: ${activeSub!!.code} (Expires: $expDateStr)",
                        fontSize = 11.sp,
                        color = ProfitGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("Redeem New Access Code", fontSize = 10.sp) },
                    placeholder = { Text("e.g. DERIVAI-VIP-2026", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("access_code_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = CardBorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (redemptionStatus != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = redemptionStatus!!,
                        fontSize = 11.sp,
                        color = if (redemptionStatus!!.contains("activated")) ProfitGreen else LossRed
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.redeemAccessCode(codeInput) { success, msg ->
                            redemptionStatus = msg
                            if (success) codeInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary, contentColor = BackgroundDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("redeem_code_button")
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ACTIVATE ACCESS CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // WhatsApp Payment & Support Button (Section 29)
                OutlinedButton(
                    onClick = {
                        val whatsappUrl = "https://wa.me/?text=Hello%20Admin,%20I%20would%20like%20to%20subscribe%20to%20Deriv%20AI%20Access%20Code"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                        context.startActivity(intent)
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("whatsapp_subscribe_button")
                ) {
                    Text("CONTACT ADMIN ON WHATSAPP TO SUBSCRIBE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ProfitGreen)
                }
            }
        }

        // Global Risk Engine Settings (Section 13, 14, 15)
        item {
            val r = riskSettings ?: RiskSettingsEntity()
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GLOBAL RISK ENGINE RULES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    StatusPill(
                        text = if (r.emergencyStopActive) "HALTED" else "ENFORCED",
                        backgroundColor = if (r.emergencyStopActive) LossRedBg else ProfitGreenBg,
                        textColor = if (r.emergencyStopActive) LossRed else ProfitGreen
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(label = "Max Stake", value = "$${r.maxStake}", modifier = Modifier.weight(1f))
                    MetricBox(label = "Daily Loss Limit", value = "$${r.maxDailyLoss}", modifier = Modifier.weight(1f))
                    MetricBox(label = "Max Cons. Losses", value = "${r.maxConsecutiveLosses}", modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(label = "Trades / Hr", value = "${r.maxTradesPerHour}", modifier = Modifier.weight(1f))
                    MetricBox(label = "Latency Cap", value = "${r.maxExecutionLatencyMs}ms", modifier = Modifier.weight(1f))
                    MetricBox(label = "Martingale", value = if (r.martingaleEnabled) "ON" else "DISABLED", valueColor = if (r.martingaleEnabled) WarningAmber else ProfitGreen, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { showRiskConfigDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CONFIGURE RISK PARAMETERS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Admin Portal Link (Section 27 & 30)
        item {
            Button(
                onClick = onOpenAdminPanel,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("open_admin_button")
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SUPER ADMIN & AUDIT PORTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRiskConfigDialog) {
        val current = riskSettings ?: RiskSettingsEntity()
        var maxStakeInput by remember { mutableStateOf(current.maxStake.toString()) }
        var dailyLossInput by remember { mutableStateOf(current.maxDailyLoss.toString()) }
        var consLossInput by remember { mutableStateOf(current.maxConsecutiveLosses.toString()) }

        AlertDialog(
            onDismissRequest = { showRiskConfigDialog = false },
            title = { Text("Configure Risk Limits", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxStakeInput,
                        onValueChange = { maxStakeInput = it },
                        label = { Text("Max Stake ($)", fontSize = 10.sp) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dailyLossInput,
                        onValueChange = { dailyLossInput = it },
                        label = { Text("Max Daily Loss ($)", fontSize = 10.sp) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = consLossInput,
                        onValueChange = { consLossInput = it },
                        label = { Text("Max Consecutive Losses", fontSize = 10.sp) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = current.copy(
                            maxStake = maxStakeInput.toDoubleOrNull() ?: current.maxStake,
                            maxDailyLoss = dailyLossInput.toDoubleOrNull() ?: current.maxDailyLoss,
                            maxConsecutiveLosses = consLossInput.toIntOrNull() ?: current.maxConsecutiveLosses
                        )
                        viewModel.updateRiskSettings(updated)
                        showRiskConfigDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark)
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRiskConfigDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = SurfaceDark
        )
    }
}
