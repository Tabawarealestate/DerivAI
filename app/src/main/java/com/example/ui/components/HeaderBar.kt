package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.network.ConnectionState
import com.example.ui.theme.*

@Composable
fun DerivAiHeaderBar(
    connectionState: ConnectionState,
    accountMode: AccountMode,
    balance: Double,
    isAutonomousActive: Boolean,
    isEmergencyStopActive: Boolean = false,
    onEmergencyStop: () -> Unit,
    onAccountModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark),
        color = BackgroundDark,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Title & Status
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DERIV",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Account Environment Badge (DEMO / REAL)
                        val isReal = accountMode == AccountMode.REAL
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isReal) BadgeRealBg else BadgeDemoBg)
                                .clickable { onAccountModeClick() }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("account_mode_badge")
                        ) {
                            Text(
                                text = if (isReal) "REAL" else "DEMO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "AI-Powered Market Intelligence & Automated Trading",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                // Emergency STOP Button
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEmergencyStopActive) LossRed else LossRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isEmergencyStopActive) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("emergency_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Stop All Trading",
                        modifier = Modifier.size(16.dp),
                        tint = if (isEmergencyStopActive) Color.Yellow else Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEmergencyStopActive) "HALTED (RESET)" else "STOP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-bar with Live Connection, Latency, and Balance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection State
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isConnected = connectionState is ConnectionState.Connected
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) ProfitGreen else LossRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "CONNECTED" else "DATA UNAVAILABLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) ProfitGreen else LossRed,
                        fontFamily = FontFamily.Monospace
                    )

                    if (connectionState is ConnectionState.Connected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${connectionState.latencyMs}ms)",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Auto status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AI AUTO: ",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                    Text(
                        text = if (isAutonomousActive) "ON" else "OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAutonomousActive) CyanPrimary else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Balance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BAL: ",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "$${String.format(java.util.Locale.US, "%,.2f", balance)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
