package com.example

import com.example.data.model.RiskSettingsEntity
import com.example.engine.DigitEngine
import com.example.engine.MarketAnalysisEngine
import com.example.engine.RiskEngine
import com.example.engine.SignalCandidate
import com.example.network.TickData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDigitEngineCalculations() {
        val engine = DigitEngine()
        val ticks = listOf(
            TickData("R_100", 100.12, 1, lastDigit = 2),
            TickData("R_100", 100.15, 2, lastDigit = 5),
            TickData("R_100", 100.18, 3, lastDigit = 8),
            TickData("R_100", 100.22, 4, lastDigit = 2),
            TickData("R_100", 100.27, 5, lastDigit = 7)
        )
        val stats = engine.analyzeTicks(ticks)
        assertEquals(5, stats.totalCount)
        assertEquals(2, stats.digitCounts[2])
        assertEquals(1, stats.digitCounts[5])
        assertEquals(7, stats.lastDigit)
    }

    @Test
    fun testRiskEngineEmergencyStopBlocksTrades() {
        val riskEngine = RiskEngine(
            RiskSettingsEntity(
                emergencyStopActive = true
            )
        )

        val signal = SignalCandidate(
            signalId = "TEST_SIG",
            symbol = "R_100",
            symbolName = "Volatility 100",
            contractType = "DIGITDIFF",
            direction = "DIFFERS",
            barrier = "5",
            modelProbabilityPct = 91.0,
            historicalOosHitRatePct = 90.0,
            breakEvenThresholdPct = 85.0,
            expectedValuePct = 6.0,
            signalQualityScore = 85,
            sampleSize = 100,
            regime = "NORMAL",
            recommendedStake = 5.0,
            decision = "TRADE",
            reason = "Edge confirmed",
            reasonsList = emptyList()
        )

        val result = riskEngine.evaluateTradeRisk(signal, currentBalance = 1000.0, activeOrdersCount = 0, latencyMs = 50)
        assertFalse("Emergency stop must block trades", result.allowed)
        assertTrue(result.reason.contains("Emergency Stop"))
    }

    @Test
    fun testRiskEngineRejectsHighLatency() {
        val riskEngine = RiskEngine(
            RiskSettingsEntity(
                maxExecutionLatencyMs = 800
            )
        )

        val signal = SignalCandidate(
            signalId = "TEST_SIG",
            symbol = "R_100",
            symbolName = "Volatility 100",
            contractType = "DIGITDIFF",
            direction = "DIFFERS",
            barrier = "5",
            modelProbabilityPct = 91.0,
            historicalOosHitRatePct = 90.0,
            breakEvenThresholdPct = 85.0,
            expectedValuePct = 6.0,
            signalQualityScore = 85,
            sampleSize = 100,
            regime = "NORMAL",
            recommendedStake = 5.0,
            decision = "TRADE",
            reason = "Edge confirmed",
            reasonsList = emptyList()
        )

        val result = riskEngine.evaluateTradeRisk(signal, currentBalance = 1000.0, activeOrdersCount = 0, latencyMs = 1500)
        assertFalse("High latency must block trades", result.allowed)
        assertTrue(result.reason.contains("latency too high"))
    }

    @Test
    fun testRiskEngineBlocksDuplicateTrades() {
        val riskEngine = RiskEngine()
        val signal = SignalCandidate(
            signalId = "TEST_SIG",
            symbol = "R_100",
            symbolName = "Volatility 100",
            contractType = "DIGITDIFF",
            direction = "DIFFERS",
            barrier = "5",
            modelProbabilityPct = 91.0,
            historicalOosHitRatePct = 90.0,
            breakEvenThresholdPct = 85.0,
            expectedValuePct = 6.0,
            signalQualityScore = 85,
            sampleSize = 100,
            regime = "NORMAL",
            recommendedStake = 5.0,
            decision = "TRADE",
            reason = "Edge confirmed",
            reasonsList = emptyList()
        )

        val first = riskEngine.evaluateTradeRisk(signal, currentBalance = 1000.0, activeOrdersCount = 0, latencyMs = 50)
        assertTrue("First trade must pass", first.allowed)

        val duplicate = riskEngine.evaluateTradeRisk(signal, currentBalance = 1000.0, activeOrdersCount = 0, latencyMs = 50)
        assertFalse("Duplicate in same second must be rejected", duplicate.allowed)
        assertTrue(duplicate.reason.contains("Duplicate trade"))
    }

    @Test
    fun testStaleTicksRejectedByMarketEngine() {
        val marketEngine = MarketAnalysisEngine()
        val oldTimestamp = System.currentTimeMillis() - 20000 // 20s ago
        val staleTicks = (1..30).map { i ->
            TickData("R_100", 100.0 + i, i.toLong(), receivedAtMs = oldTimestamp)
        }

        val result = marketEngine.evaluateSignal("R_100", "Volatility 100", staleTicks)
        assertEquals("NO TRADE", result.decision)
        assertTrue(result.reason.contains("stale"))
    }

    @Test
    fun testRetrofitServiceInitialization() {
        val client = com.example.network.rest.DerivRetrofitClient()
        org.junit.Assert.assertNotNull("Retrofit API Service must be instantiated", client.apiService)
    }

    @Test
    fun testBalanceUpdateAndRetrieval() {
        val wsClient = com.example.network.DerivWebSocketClient(appId = "1089")
        wsClient.updateAccountBalance(12500.50, "USD")
        val account = wsClient.authorizedAccount.value
        org.junit.Assert.assertNotNull("Authorized account must be non-null after balance update", account)
        org.junit.Assert.assertEquals(12500.50, account!!.balance, 0.001)
        org.junit.Assert.assertEquals("USD", account.currency)
    }
}
