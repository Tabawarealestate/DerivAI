package com.example.engine

import com.example.network.TickData
import kotlin.math.ln
import kotlin.math.sqrt

data class DigitStats(
    val lastDigit: Int,
    val digitCounts: IntArray, // 0..9 counts
    val digitPercentages: DoubleArray, // 0..9 percentages
    val totalCount: Int,
    val entropy: Double, // Shannon entropy (uniform ~ 3.32)
    val maxFrequentDigit: Int,
    val maxFrequencyPct: Double,
    val minFrequentDigit: Int,
    val minFrequencyPct: Double,
    val evenCount: Int,
    val oddCount: Int,
    val evenPct: Double,
    val oddPct: Double,
    val overFiveCount: Int,
    val underFiveCount: Int,
    val currentStreakLength: Int,
    val currentStreakType: String, // "REPEATING", "EVEN", "ODD", "OVER", "UNDER"
    val sampleConfidence: Double // 0.0 to 1.0 based on N
)

class DigitEngine {

    fun analyzeTicks(ticks: List<TickData>): DigitStats {
        if (ticks.isEmpty()) {
            return DigitStats(
                lastDigit = 0,
                digitCounts = IntArray(10),
                digitPercentages = DoubleArray(10),
                totalCount = 0,
                entropy = 0.0,
                maxFrequentDigit = 0,
                maxFrequencyPct = 0.0,
                minFrequentDigit = 0,
                minFrequencyPct = 0.0,
                evenCount = 0,
                oddCount = 0,
                evenPct = 50.0,
                oddPct = 50.0,
                overFiveCount = 0,
                underFiveCount = 0,
                currentStreakLength = 0,
                currentStreakType = "NONE",
                sampleConfidence = 0.0
            )
        }

        val lastDigit = ticks.last().lastDigit
        val counts = IntArray(10)
        var evenCount = 0
        var oddCount = 0
        var overFiveCount = 0
        var underFiveCount = 0

        for (t in ticks) {
            val d = t.lastDigit.coerceIn(0, 9)
            counts[d]++
            if (d % 2 == 0) evenCount++ else oddCount++
            if (d > 4) overFiveCount++ else underFiveCount++
        }

        val total = ticks.size
        val percentages = DoubleArray(10)
        var entropy = 0.0
        var maxD = 0
        var minD = 0
        var maxCnt = -1
        var minCnt = Int.MAX_VALUE

        val log2 = ln(2.0)
        for (i in 0..9) {
            val p = counts[i].toDouble() / total
            percentages[i] = p * 100.0
            if (p > 0.0) {
                entropy -= p * (ln(p) / log2)
            }
            if (counts[i] > maxCnt) {
                maxCnt = counts[i]
                maxD = i
            }
            if (counts[i] < minCnt) {
                minCnt = counts[i]
                minD = i
            }
        }

        // Streak detection on tail
        var streakLen = 1
        var streakType = "NONE"
        val reversed = ticks.map { it.lastDigit }.reversed()
        if (reversed.size > 1) {
            val first = reversed[0]
            val isEven = first % 2 == 0
            val isOver = first > 4

            var sameDigitStreak = 1
            var parityStreak = 1
            var overStreak = 1

            for (i in 1 until reversed.size) {
                val d = reversed[i]
                if (d == first) sameDigitStreak++ else break
            }
            for (i in 1 until reversed.size) {
                val d = reversed[i]
                if ((d % 2 == 0) == isEven) parityStreak++ else break
            }
            for (i in 1 until reversed.size) {
                val d = reversed[i]
                if ((d > 4) == isOver) overStreak++ else break
            }

            if (sameDigitStreak >= 2) {
                streakLen = sameDigitStreak
                streakType = "REPEATING ($first)"
            } else if (parityStreak >= 3) {
                streakLen = parityStreak
                streakType = if (isEven) "EVEN RUN" else "ODD RUN"
            } else if (overStreak >= 3) {
                streakLen = overStreak
                streakType = if (isOver) "OVER 4 RUN" else "UNDER 5 RUN"
            }
        }

        // Sample size confidence (asymptotic sigmoid scaling up to N=100)
        val sampleConfidence = (1.0 - (1.0 / (1.0 + total / 35.0))).coerceIn(0.0, 1.0)

        return DigitStats(
            lastDigit = lastDigit,
            digitCounts = counts,
            digitPercentages = percentages,
            totalCount = total,
            entropy = entropy,
            maxFrequentDigit = maxD,
            maxFrequencyPct = if (total > 0) (maxCnt.toDouble() / total * 100.0) else 0.0,
            minFrequentDigit = minD,
            minFrequencyPct = if (total > 0) (minCnt.toDouble() / total * 100.0) else 0.0,
            evenCount = evenCount,
            oddCount = oddCount,
            evenPct = if (total > 0) (evenCount.toDouble() / total * 100.0) else 50.0,
            oddPct = if (total > 0) (oddCount.toDouble() / total * 100.0) else 50.0,
            overFiveCount = overFiveCount,
            underFiveCount = underFiveCount,
            currentStreakLength = streakLen,
            currentStreakType = streakType,
            sampleConfidence = sampleConfidence
        )
    }

    /**
     * Compute Chi-Square statistic to determine if observed digit distribution
     * differs significantly from uniform (10% per digit) with df=9.
     * Critical value for p=0.05 is 16.92.
     */
    fun computeChiSquare(counts: IntArray, total: Int): Double {
        if (total < 10) return 0.0
        val expected = total.toDouble() / 10.0
        var chiSq = 0.0
        for (c in counts) {
            val diff = c - expected
            chiSq += (diff * diff) / expected
        }
        return chiSq
    }
}
