package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AccessCodeEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.RiskSettingsEntity
import com.example.data.model.StrategyEntity
import com.example.data.model.TradeOrderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TradeOrderEntity::class,
        AccessCodeEntity::class,
        StrategyEntity::class,
        AuditLogEntity::class,
        RiskSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun accessCodeDao(): AccessCodeDao
    abstract fun strategyDao(): StrategyDao
    abstract fun auditDao(): AuditDao
    abstract fun riskSettingsDao(): RiskSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deriv_ai_database"
                )
                .fallbackToDestructiveMigration(true)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial defaults
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            seedDefaults(database)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaults(db: AppDatabase) {
            val now = System.currentTimeMillis()
            val thirtyDays = 30L * 24 * 3600 * 1000
            val ninetyDays = 90L * 24 * 3600 * 1000
            val oneYear = 365L * 24 * 3600 * 1000

            db.accessCodeDao().insertDefaultCodes(
                listOf(
                    AccessCodeEntity(
                        code = "DERIVAI-VIP-2026",
                        subscriptionPlan = "VIP Pro Access",
                        durationDays = 365,
                        activationDate = now,
                        expirationDate = now + oneYear,
                        status = "ACTIVE",
                        maxDevices = 2,
                        assignedUser = "Authorized Trader",
                        notes = "Master VIP license granted for autonomous algorithmic engine."
                    ),
                    AccessCodeEntity(
                        code = "ALPHA-QUANT-PRO",
                        subscriptionPlan = "Institutional 90-Day",
                        durationDays = 90,
                        activationDate = now,
                        expirationDate = now + ninetyDays,
                        status = "ACTIVE",
                        maxDevices = 1,
                        assignedUser = "Institutional Client",
                        notes = "Full access code with live multi-market execution."
                    ),
                    AccessCodeEntity(
                        code = "TRIAL-ACCESS-7D",
                        subscriptionPlan = "Trial 7-Day",
                        durationDays = 7,
                        activationDate = now,
                        expirationDate = now + (7L * 24 * 3600 * 1000),
                        status = "ACTIVE",
                        maxDevices = 1,
                        assignedUser = "Trial Trader",
                        notes = "New user testing license."
                    )
                )
            )

            db.strategyDao().insertDefaultStrategies(
                listOf(
                    StrategyEntity(
                        id = "strat_digit_differs",
                        name = "Digit Entropy Differential",
                        version = "v2.1",
                        description = "Exploits statistically anomalous digit clustering using Shannon entropy & Markov transition matrices for Digits Differs contracts.",
                        category = "DIGIT",
                        markets = "R_10,R_25,R_50,R_75,R_100,1HZ10V,1HZ100V",
                        contractTypes = "DIGITDIFF",
                        status = "APPROVED",
                        winRatePct = 90.8,
                        totalTrades = 1840,
                        profitFactor = 1.34
                    ),
                    StrategyEntity(
                        id = "strat_digit_matches",
                        name = "Digit Momentum Matches",
                        version = "v1.8",
                        description = "Identifies high-frequency repeat streaks with mean-reversion filters and strict Kelly risk caps.",
                        category = "DIGIT",
                        markets = "R_100,1HZ100V,R_50",
                        contractTypes = "DIGITMATCH",
                        status = "PAPER",
                        winRatePct = 12.4,
                        totalTrades = 850,
                        profitFactor = 1.18
                    ),
                    StrategyEntity(
                        id = "strat_digit_even_odd",
                        name = "Parity Regime Oscillation",
                        version = "v3.0",
                        description = "Analyzes runs of Even/Odd parity with autocorrelation diagnostics and Wald-Wolfowitz run tests.",
                        category = "DIGIT",
                        markets = "R_25,R_75,1HZ50V",
                        contractTypes = "DIGITEVEN,DIGITODD",
                        status = "APPROVED",
                        winRatePct = 54.2,
                        totalTrades = 2410,
                        profitFactor = 1.12
                    ),
                    StrategyEntity(
                        id = "strat_synthetic_trend",
                        name = "Multi-Timeframe Synthetic Pulse",
                        version = "v2.4",
                        description = "EMA ribbon (9/21/50) breakout engine with ATR volatility band expansion for Rise/Fall contracts.",
                        category = "SYNTHETIC",
                        markets = "R_100,1HZ100V,CRASH_500,BOOM_500",
                        contractTypes = "CALL,PUT",
                        status = "APPROVED",
                        winRatePct = 58.7,
                        totalTrades = 3120,
                        profitFactor = 1.41
                    ),
                    StrategyEntity(
                        id = "strat_mean_reversion",
                        name = "Bollinger Extreme Reversion",
                        version = "v1.5",
                        description = "Mean reversion on 2.5 sigma Bollinger excursions coupled with RSI 14 oversold/overbought confirmation.",
                        category = "FOREX",
                        markets = "frxEURUSD,frxGBPUSD,frxUSDJPY",
                        contractTypes = "CALL,PUT",
                        status = "PAPER",
                        winRatePct = 56.1,
                        totalTrades = 940,
                        profitFactor = 1.22
                    )
                )
            )

            if (db.riskSettingsDao().getRiskSettingsSync() == null) {
                db.riskSettingsDao().setRiskSettings(
                    RiskSettingsEntity(
                        id = 1,
                        maxStake = 25.0,
                        stakePercentOfBalance = 1.0,
                        maxDailyLoss = 50.0,
                        maxSessionLoss = 25.0,
                        maxConsecutiveLosses = 3,
                        maxTradesPerHour = 12,
                        maxTradesPerDay = 50,
                        maxOpenContracts = 2,
                        cooldownAfterLossSec = 45,
                        cooldownConsecutiveLossesSec = 300,
                        minExpectedValuePct = 1.5,
                        minModelQualityScore = 70,
                        maxExecutionLatencyMs = 1200,
                        martingaleEnabled = false,
                        emergencyStopActive = false
                    )
                )
            }

            db.auditDao().insertLog(
                AuditLogEntity(
                    action = "SYSTEM_INITIALIZED",
                    accountId = "SYSTEM",
                    details = "Deriv AI Core Database and Risk Engine initialized successfully.",
                    level = "INFO"
                )
            )
        }
    }
}
