package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.data.model.AccessCodeEntity
import com.example.network.ConnectionState
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val accessCodes by viewModel.accessCodes.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val connectionState by viewModel.webSocketClient.connectionState.collectAsState()
    val allOrders by viewModel.allOrders.collectAsState()

    var showGenerateCodeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SUPER ADMIN & AUDIT PORTAL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = CyanPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BackgroundDark,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // System Health Overview (PDF Section 56)
                FintechCard {
                    Text(
                        text = "SYSTEM HEALTH & OBSERVABILITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val isConnected = connectionState is ConnectionState.Connected
                    val latency = if (connectionState is ConnectionState.Connected) (connectionState as ConnectionState.Connected).latencyMs else 0L

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(label = "WebSocket", value = if (isConnected) "ONLINE" else "OFFLINE", valueColor = if (isConnected) ProfitGreen else LossRed, modifier = Modifier.weight(1f))
                        MetricBox(label = "API Ping", value = "${latency}ms", modifier = Modifier.weight(1f))
                        MetricBox(label = "Total Logs", value = "${auditLogs.size}", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(label = "Total Orders", value = "${allOrders.size}", modifier = Modifier.weight(1f))
                        MetricBox(label = "Active Codes", value = "${accessCodes.count { it.status == "ACTIVE" }}", modifier = Modifier.weight(1f))
                        MetricBox(label = "System Uptime", value = "99.98%", valueColor = ProfitGreen, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Access Codes Management (PDF Section 28 & 30)
            item {
                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SUBSCRIPTION ACCESS CODES (${accessCodes.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )

                        Button(
                            onClick = { showGenerateCodeDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("generate_code_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NEW CODE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accessCodes.forEach { code ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = code.code,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${code.subscriptionPlan} • ${code.durationDays}d • ${code.status}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (code.status == "ACTIVE") {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.subscriptionManager.revokeCode(code.code)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("REVOKE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                viewModel.subscriptionManager.extendCode(code.code, 30)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary, contentColor = BackgroundDark),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("+30D", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Append-Only Audit Logs (PDF Section 58)
            item {
                Text(
                    text = "IMMUTABLE AUDIT LOG (APPEND-ONLY)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
            }

            items(auditLogs) { log ->
                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.timestamp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = log.action, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanPrimary, fontFamily = FontFamily.Monospace)
                            Text(text = timeStr, fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(text = log.details, fontSize = 10.sp, color = TextSecondary, lineHeight = 13.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showGenerateCodeDialog) {
        var newCode by remember { mutableStateOf("DERIV-${(1000..9999).random()}-PRO") }
        var plan by remember { mutableStateOf("VIP 30-Day") }
        var durationDays by remember { mutableStateOf("30") }

        AlertDialog(
            onDismissRequest = { showGenerateCodeDialog = false },
            title = { Text("Generate Subscription Code", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCode,
                        onValueChange = { newCode = it },
                        label = { Text("Access Code", fontSize = 10.sp) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = plan,
                        onValueChange = { plan = it },
                        label = { Text("Subscription Plan", fontSize = 10.sp) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = durationDays,
                        onValueChange = { durationDays = it },
                        label = { Text("Duration (Days)", fontSize = 10.sp) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.subscriptionManager.createCode(
                                code = newCode,
                                plan = plan,
                                durationDays = durationDays.toIntOrNull() ?: 30
                            )
                        }
                        showGenerateCodeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark)
                ) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateCodeDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = SurfaceDark
        )
    }
}
