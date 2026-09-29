package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trade_orders")
data class TradeOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val accountId: String,
    val symbol: String,
    val symbolName: String,
    val contractType: String,
    val signalId: String?,
    val proposalId: String?,
    val buyTransactionId: String?,
    val entryTime: Long,
    val exitTime: Long? = null,
    val entryPrice: Double = 0.0,
    val exitPrice: Double? = null,
    val stake: Double,
    val expectedPayout: Double,
    val actualPayout: Double? = null,
    val profitLoss: Double? = null,
    val executionLatencyMs: Long = 0,
    val result: String = "PENDING", // PENDING, WON, LOST, CANCELLED
    val mode: String = "PAPER",     // REAL, PAPER, BACKTEST
    val exitReason: String? = null,
    val riskState: String? = null
)

@Entity(tableName = "access_codes")
data class AccessCodeEntity(
    @PrimaryKey val code: String,
    val subscriptionPlan: String, // VIP Pro, Institutional, Lifetime, Trial
    val durationDays: Int,
    val activationDate: Long? = null,
    val expirationDate: Long? = null,
    val status: String = "UNUSED", // ACTIVE, EXPIRED, REVOKED, SUSPENDED, UNUSED
    val maxDevices: Int = 1,
    val assignedUser: String = "Current User",
    val notes: String = ""
)

@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String,
    val category: String, // DIGIT, SYNTHETIC, FOREX, AI_ENSEMBLE
    val markets: String,  // comma-separated
    val contractTypes: String,
    val parametersJson: String = "{}",
    val status: String = "APPROVED", // DRAFT, BACKTESTING, VALIDATION, PAPER, APPROVED, LIVE, PAUSED, RETIRED
    val winRatePct: Double = 0.0,
    val totalTrades: Int = 0,
    val profitFactor: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val accountId: String,
    val details: String,
    val level: String = "INFO" // INFO, WARN, SECURITY
)

@Entity(tableName = "risk_settings")
data class RiskSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val maxStake: Double = 25.0,
    val stakePercentOfBalance: Double = 1.0,
    val maxDailyLoss: Double = 50.0,
    val maxSessionLoss: Double = 25.0,
    val maxConsecutiveLosses: Int = 3,
    val maxTradesPerHour: Int = 12,
    val maxTradesPerDay: Int = 50,
    val maxOpenContracts: Int = 2,
    val cooldownAfterLossSec: Int = 45,
    val cooldownConsecutiveLossesSec: Int = 300,
    val minExpectedValuePct: Double = 1.5,
    val minModelQualityScore: Int = 70,
    val maxExecutionLatencyMs: Long = 1200,
    val martingaleEnabled: Boolean = false,
    val emergencyStopActive: Boolean = false,
    val dailyLossCounter: Double = 0.0,
    val dailyTradeCount: Int = 0,
    val lastResetDay: String = ""
)
