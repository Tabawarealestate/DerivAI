package com.example.engine

import com.example.data.model.RiskSettingsEntity
import com.example.data.model.TradeOrderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class RiskCheckResult(
    val allowed: Boolean,
    val approvedStake: Double,
    val reason: String
)

class RiskEngine(
    private var settings: RiskSettingsEntity = RiskSettingsEntity()
) {
    private var consecutiveLosses = 0
    private var sessionLoss = 0.0
    private var lastTradeTimestamp = 0L
    private var lastLossTimestamp = 0L
    private var hourlyTrades = mutableListOf<Long>()
    private val executedSignalSignatures = ConcurrentHashMap.newKeySet<String>()

    fun updateSettings(newSettings: RiskSettingsEntity) {
        this.settings = newSettings
    }

    fun getSettings(): RiskSettingsEntity = settings

    fun triggerEmergencyStop(): RiskSettingsEntity {
        settings = settings.copy(emergencyStopActive = true)
        return settings
    }

    fun clearEmergencyStop(): RiskSettingsEntity {
        settings = settings.copy(emergencyStopActive = false)
        return settings
    }

    /**
     * Check daily safety reset based on date string (YYYY-MM-DD)
     */
    fun checkDailyReset() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (settings.lastResetDay != today) {
            settings = settings.copy(
                dailyLossCounter = 0.0,
                dailyTradeCount = 0,
                lastResetDay = today
            )
            sessionLoss = 0.0
            consecutiveLosses = 0
            hourlyTrades.clear()
        }
    }

    fun onTradeCompleted(order: TradeOrderEntity) {
        checkDailyReset()
        lastTradeTimestamp = System.currentTimeMillis()
        val pnl = order.profitLoss ?: 0.0

        if (order.result == "LOST" || pnl < 0) {
            val lossAmt = kotlin.math.abs(pnl)
            consecutiveLosses++
            sessionLoss += lossAmt
            lastLossTimestamp = System.currentTimeMillis()
            settings = settings.copy(
                dailyLossCounter = settings.dailyLossCounter + lossAmt,
                dailyTradeCount = settings.dailyTradeCount + 1
            )
        } else if (order.result == "WON" || pnl > 0) {
            consecutiveLosses = 0 // reset loss streak on win
            settings = settings.copy(
                dailyTradeCount = settings.dailyTradeCount + 1
            )
        }
    }

    /**
     * Validates whether a proposed order may execute under all risk rules.
     */
    fun evaluateTradeRisk(
        signal: SignalCandidate,
        currentBalance: Double,
        activeOrdersCount: Int,
        latencyMs: Long
    ): RiskCheckResult {
        checkDailyReset()

        // 1. Emergency Stop Check
        if (settings.emergencyStopActive) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Emergency Stop is currently ACTIVE. All trading halted."
            )
        }

        // 2. Latency Guard
        if (latencyMs > settings.maxExecutionLatencyMs) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Network/Execution latency too high (${latencyMs}ms > ${settings.maxExecutionLatencyMs}ms guard limit)."
            )
        }

        // 3. Balance Insufficient Check
        if (currentBalance <= 1.0) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Account balance is below minimum trading threshold ($1.00)."
            )
        }

        // 4. Max Daily Loss Limit
        if (settings.dailyLossCounter >= settings.maxDailyLoss) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Daily loss limit reached ($${String.format(Locale.US, "%.2f", settings.dailyLossCounter)} / $${String.format(Locale.US, "%.2f", settings.maxDailyLoss)})."
            )
        }

        // 5. Max Session Loss Limit
        if (sessionLoss >= settings.maxSessionLoss) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Session loss limit reached ($${String.format(Locale.US, "%.2f", sessionLoss)} / $${String.format(Locale.US, "%.2f", settings.maxSessionLoss)})."
            )
        }

        // 6. Max Consecutive Losses Guard
        if (consecutiveLosses >= settings.maxConsecutiveLosses) {
            val elapsedSec = (System.currentTimeMillis() - lastLossTimestamp) / 1000
            val waitSec = settings.cooldownConsecutiveLossesSec - elapsedSec
            if (waitSec > 0) {
                return RiskCheckResult(
                    allowed = false,
                    approvedStake = 0.0,
                    reason = "BLOCKED: Cooldown active after $consecutiveLosses consecutive losses (${waitSec}s remaining)."
                )
            }
        }

        // 7. Single Loss Cooldown
        if (consecutiveLosses > 0) {
            val elapsedSec = (System.currentTimeMillis() - lastLossTimestamp) / 1000
            val waitSec = settings.cooldownAfterLossSec - elapsedSec
            if (waitSec > 0) {
                return RiskCheckResult(
                    allowed = false,
                    approvedStake = 0.0,
                    reason = "BLOCKED: Post-loss cooldown active (${waitSec}s remaining)."
                )
            }
        }

        // 8. Max Open Contracts Guard
        if (activeOrdersCount >= settings.maxOpenContracts) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Max open positions limit reached ($activeOrdersCount / ${settings.maxOpenContracts})."
            )
        }

        // 9. Trades per Hour Rate Limit
        val now = System.currentTimeMillis()
        hourlyTrades.removeAll { now - it > 3600000L }
        if (hourlyTrades.size >= settings.maxTradesPerHour) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Hourly trades frequency cap reached (${hourlyTrades.size} / ${settings.maxTradesPerHour} per hour)."
            )
        }

        // 10. Trades per Day Limit
        if (settings.dailyTradeCount >= settings.maxTradesPerDay) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Daily trade volume cap reached (${settings.dailyTradeCount} / ${settings.maxTradesPerDay})."
            )
        }

        // 11. Duplicate Signal / Second Guard (PDF Section 38)
        val secondWindow = now / 1000
        val signature = "${signal.symbol}_${signal.contractType}_${signal.direction}_$secondWindow"
        if (executedSignalSignatures.contains(signature)) {
            return RiskCheckResult(
                allowed = false,
                approvedStake = 0.0,
                reason = "BLOCKED: Duplicate trade prevented for $signature in same window."
            )
        }

        // 12. Smart Stake Sizing with Strict Anti-Martingale (PDF Section 14, 15)
        // If Martingale is disabled, NEVER increase stake after loss!
        val baseRiskAmount = currentBalance * (settings.stakePercentOfBalance / 100.0)
        var calculatedStake = baseRiskAmount.coerceIn(0.50, settings.maxStake)

        // Scale by model quality
        val qualityRatio = (signal.signalQualityScore / 100.0).coerceIn(0.5, 1.0)
        calculatedStake *= qualityRatio

        // Clamp to balance and settings
        val finalStake = Math.round(calculatedStake.coerceAtMost(currentBalance - 0.50).coerceIn(0.50, settings.maxStake) * 100.0) / 100.0

        executedSignalSignatures.add(signature)
        hourlyTrades.add(now)

        return RiskCheckResult(
            allowed = true,
            approvedStake = finalStake,
            reason = "PASSED: All risk parameters verified. Smart stake sized to $${String.format(Locale.US, "%.2f", finalStake)}."
        )
    }
}
