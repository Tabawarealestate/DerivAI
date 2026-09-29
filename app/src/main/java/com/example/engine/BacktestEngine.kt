package com.example.engine

import java.util.Locale
import kotlin.random.Random

data class BacktestParams(
    val strategyId: String,
    val symbol: String,
    val contractType: String,
    val initialBalance: Double = 1000.0,
    val fixedStake: Double = 5.0,
    val durationTicks: Int = 5,
    val payoutMultiplier: Double = 1.95 // Deriv standard payout ratio
)

data class BacktestResult(
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRatePct: Double,
    val netProfit: Double,
    val endingBalance: Double,
    val maxDrawdownPct: Double,
    val profitFactor: Double,
    val expectancy: Double,
    val longestWinStreak: Int,
    val longestLossStreak: Int,
    val equityCurve: List<Double>,
    // Monte Carlo outputs
    val monteCarloMedianBalance: Double,
    val monteCarloWorstDrawdownPct: Double,
    val monteCarloProbabilityOfRuinPct: Double
)

class BacktestEngine {

    fun runBacktest(
        prices: List<Double>,
        params: BacktestParams
    ): BacktestResult {
        if (prices.size < 30) {
            return BacktestResult(
                totalTrades = 0,
                wins = 0,
                losses = 0,
                winRatePct = 0.0,
                netProfit = 0.0,
                endingBalance = params.initialBalance,
                maxDrawdownPct = 0.0,
                profitFactor = 0.0,
                expectancy = 0.0,
                longestWinStreak = 0,
                longestLossStreak = 0,
                equityCurve = listOf(params.initialBalance),
                monteCarloMedianBalance = params.initialBalance,
                monteCarloWorstDrawdownPct = 0.0,
                monteCarloProbabilityOfRuinPct = 0.0
            )
        }

        var balance = params.initialBalance
        var peakBalance = balance
        var maxDrawdown = 0.0
        val equityCurve = mutableListOf(balance)

        var wins = 0
        var losses = 0
        var grossProfit = 0.0
        var grossLoss = 0.0

        var curWinStreak = 0
        var maxWinStreak = 0
        var curLossStreak = 0
        var maxLossStreak = 0

        val tradePnlList = mutableListOf<Double>()

        val step = maxOf(1, params.durationTicks)
        for (i in 20 until (prices.size - step) step step) {
            val entry = prices[i]
            val exit = prices[i + step]
            val stake = params.fixedStake.coerceAtMost(balance * 0.10)
            if (balance <= stake) break // Ruin check

            val won: Boolean = when (params.contractType) {
                "DIGITDIFF" -> {
                    val entryDigit = (Math.abs(Math.round(entry * 1000)) % 10).toInt()
                    val exitDigit = (Math.abs(Math.round(exit * 1000)) % 10).toInt()
                    entryDigit != exitDigit
                }
                "DIGITMATCH" -> {
                    val entryDigit = (Math.abs(Math.round(entry * 1000)) % 10).toInt()
                    val exitDigit = (Math.abs(Math.round(exit * 1000)) % 10).toInt()
                    entryDigit == exitDigit
                }
                "DIGITEVEN" -> {
                    val exitDigit = (Math.abs(Math.round(exit * 1000)) % 10).toInt()
                    exitDigit % 2 == 0
                }
                "DIGITODD" -> {
                    val exitDigit = (Math.abs(Math.round(exit * 1000)) % 10).toInt()
                    exitDigit % 2 != 0
                }
                "CALL" -> exit > entry
                "PUT" -> exit < entry
                else -> exit > entry
            }

            val pnl = if (won) {
                val winReturn = if (params.contractType == "DIGITDIFF") stake * 0.09 else stake * (params.payoutMultiplier - 1.0)
                wins++
                grossProfit += winReturn
                curWinStreak++
                curLossStreak = 0
                if (curWinStreak > maxWinStreak) maxWinStreak = curWinStreak
                winReturn
            } else {
                losses++
                grossLoss += stake
                curLossStreak++
                curWinStreak = 0
                if (curLossStreak > maxLossStreak) maxLossStreak = curLossStreak
                -stake
            }

            balance += pnl
            tradePnlList.add(pnl)
            if (balance > peakBalance) peakBalance = balance
            val currentDd = ((peakBalance - balance) / peakBalance) * 100.0
            if (currentDd > maxDrawdown) maxDrawdown = currentDd
            equityCurve.add(balance)
        }

        val totalTrades = wins + losses
        val winRate = if (totalTrades > 0) (wins.toDouble() / totalTrades) * 100.0 else 0.0
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 9.99 else 0.0
        val expectancy = if (totalTrades > 0) (grossProfit - grossLoss) / totalTrades else 0.0

        // Run Monte Carlo Resampling (500 simulations)
        var mcRuinCount = 0
        val mcEndBalances = mutableListOf<Double>()
        var mcWorstDd = maxDrawdown

        if (tradePnlList.size >= 10) {
            val random = Random(42)
            for (sim in 0 until 500) {
                var simBal = params.initialBalance
                var simPeak = simBal
                var simMaxDd = 0.0
                var ruined = false

                for (trade in 0 until tradePnlList.size) {
                    val randomPnl = tradePnlList[random.nextInt(tradePnlList.size)]
                    simBal += randomPnl
                    if (simBal > simPeak) simPeak = simBal
                    val dd = ((simPeak - simBal) / simPeak) * 100.0
                    if (dd > simMaxDd) simMaxDd = dd

                    if (simBal <= (params.initialBalance * 0.20)) {
                        ruined = true
                        break
                    }
                }
                if (ruined) mcRuinCount++
                if (simMaxDd > mcWorstDd) mcWorstDd = simMaxDd
                mcEndBalances.add(simBal)
            }
        }

        mcEndBalances.sort()
        val medianEndBalance = if (mcEndBalances.isNotEmpty()) mcEndBalances[mcEndBalances.size / 2] else balance
        val probOfRuinPct = if (tradePnlList.size >= 10) (mcRuinCount.toDouble() / 500.0) * 100.0 else 0.0

        return BacktestResult(
            totalTrades = totalTrades,
            wins = wins,
            losses = losses,
            winRatePct = Math.round(winRate * 10.0) / 10.0,
            netProfit = Math.round((balance - params.initialBalance) * 100.0) / 100.0,
            endingBalance = Math.round(balance * 100.0) / 100.0,
            maxDrawdownPct = Math.round(maxDrawdown * 10.0) / 10.0,
            profitFactor = Math.round(profitFactor * 100.0) / 100.0,
            expectancy = Math.round(expectancy * 100.0) / 100.0,
            longestWinStreak = maxWinStreak,
            longestLossStreak = maxLossStreak,
            equityCurve = equityCurve,
            monteCarloMedianBalance = Math.round(medianEndBalance * 100.0) / 100.0,
            monteCarloWorstDrawdownPct = Math.round(mcWorstDd * 10.0) / 10.0,
            monteCarloProbabilityOfRuinPct = Math.round(probOfRuinPct * 10.0) / 10.0
        )
    }
}
