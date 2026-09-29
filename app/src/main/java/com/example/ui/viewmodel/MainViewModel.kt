package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.AccessCodeEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.RiskSettingsEntity
import com.example.data.model.StrategyEntity
import com.example.data.model.TradeOrderEntity
import com.example.engine.AccountMode
import com.example.engine.BacktestEngine
import com.example.engine.BacktestParams
import com.example.engine.BacktestResult
import com.example.engine.DigitEngine
import com.example.engine.DigitStats
import com.example.engine.ExecutionEngine
import com.example.engine.MarketAnalysisEngine
import com.example.engine.RiskEngine
import com.example.engine.SignalCandidate
import com.example.engine.TradingMode
import com.example.network.ActiveSymbol
import com.example.network.ConnectionState
import com.example.network.ContractProposal
import com.example.network.DerivAccount
import com.example.network.DerivWebSocketClient
import com.example.network.TickData
import com.example.subscription.SubscriptionManager
import com.example.network.rest.DerivRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val database = AppDatabase.getInstance(application)
    val webSocketClient = DerivWebSocketClient(appId = "1089")
    val retrofitClient = DerivRetrofitClient(appId = "1089")
    val digitEngine = DigitEngine()
    val marketAnalysisEngine = MarketAnalysisEngine(digitEngine)
    val riskEngine = RiskEngine()
    val subscriptionManager = SubscriptionManager(database.accessCodeDao())
    val backtestEngine = BacktestEngine()

    val executionEngine = ExecutionEngine(
        webSocketClient = webSocketClient,
        tradeDao = database.tradeDao(),
        auditDao = database.auditDao(),
        riskEngine = riskEngine
    )

    // Data buffers
    private val tickHistoryMap = ConcurrentHashMap<String, MutableList<TickData>>()
    private val _selectedSymbol = MutableStateFlow("R_100")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    private val _currentSignal = MutableStateFlow<SignalCandidate?>(null)
    val currentSignal: StateFlow<SignalCandidate?> = _currentSignal.asStateFlow()

    private val _currentDigitStats = MutableStateFlow<DigitStats?>(null)
    val currentDigitStats: StateFlow<DigitStats?> = _currentDigitStats.asStateFlow()

    private val _currentProposal = MutableStateFlow<ContractProposal?>(null)
    val currentProposal: StateFlow<ContractProposal?> = _currentProposal.asStateFlow()

    private val _scannerSignals = MutableStateFlow<Map<String, SignalCandidate>>(emptyMap())
    val scannerSignals: StateFlow<Map<String, SignalCandidate>> = _scannerSignals.asStateFlow()

    private val _backtestResult = MutableStateFlow<BacktestResult?>(null)
    val backtestResult: StateFlow<BacktestResult?> = _backtestResult.asStateFlow()

    private val _isBacktesting = MutableStateFlow(false)
    val isBacktesting: StateFlow<Boolean> = _isBacktesting.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Autonomous Trader Loop Job
    private var autoTraderJob: Job? = null

    // Room Database Flows
    val allOrders: StateFlow<List<TradeOrderEntity>> = database.tradeDao().getAllOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openOrders: StateFlow<List<TradeOrderEntity>> = database.tradeDao().getOpenOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStrategies: StateFlow<List<StrategyEntity>> = database.strategyDao().getAllStrategies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = database.auditDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accessCodes: StateFlow<List<AccessCodeEntity>> = database.accessCodeDao().getAllCodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSubscription: StateFlow<AccessCodeEntity?> = subscriptionManager.activeSubscription
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isSubscribed: StateFlow<Boolean> = subscriptionManager.isSubscribed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val riskSettingsFlow: StateFlow<RiskSettingsEntity?> = database.riskSettingsDao().getRiskSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isEmergencyStopActive: StateFlow<Boolean> = riskSettingsFlow.map {
        it?.emergencyStopActive ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val effectiveBalanceFlow: StateFlow<Double> = combine(
        executionEngine.accountMode,
        webSocketClient.authorizedAccount,
        executionEngine.paperBalance
    ) { mode, auth, paper ->
        if (mode == AccountMode.REAL) {
            auth?.balance ?: 0.0
        } else {
            paper
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10000.0)

    val balanceCurrency: StateFlow<String> = webSocketClient.authorizedAccount.map {
        it?.currency ?: "USD"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    private var lastUsedToken: String? = null

    init {
        // Load risk settings into engine
        viewModelScope.launch(Dispatchers.IO) {
            val settings = database.riskSettingsDao().getRiskSettingsSync()
            if (settings != null) {
                riskEngine.updateSettings(settings)
            }
        }

        // Subscribe to initial popular symbols
        listOf("R_10", "R_25", "R_50", "R_75", "R_100", "1HZ10V", "1HZ100V", "frxEURUSD", "cryBTCUSD").forEach {
            webSocketClient.subscribeTick(it)
        }

        // Process incoming ticks
        viewModelScope.launch(Dispatchers.Default) {
            webSocketClient.liveTicks.collect { tick ->
                handleIncomingTick(tick)
            }
        }

        // Listen to proposals
        viewModelScope.launch {
            webSocketClient.proposals.collect { proposal ->
                if (proposal.symbol == _selectedSymbol.value) {
                    _currentProposal.value = proposal
                }
            }
        }

        // Initial market data fetch via Retrofit
        fetchRealtimeMarketDataWithRetrofit()

        // Periodic heartbeat & automated signal scanning loop
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                scanSelectedMarkets()
            }
        }
    }

    private fun handleIncomingTick(tick: TickData) {
        val list = tickHistoryMap.getOrPut(tick.symbol) { mutableListOf() }
        synchronized(list) {
            list.add(tick)
            if (list.size > 200) {
                list.removeAt(0)
            }
        }

        if (tick.symbol == _selectedSymbol.value) {
            val snapshot = synchronized(list) { list.toList() }
            val stats = digitEngine.analyzeTicks(snapshot)
            _currentDigitStats.value = stats

            val sig = marketAnalysisEngine.evaluateSignal(
                symbol = tick.symbol,
                symbolName = getSymbolDisplayName(tick.symbol),
                ticks = snapshot,
                balance = getEffectiveBalance(),
                configuredRiskPercent = riskEngine.getSettings().stakePercentOfBalance,
                minEvThreshold = riskEngine.getSettings().minExpectedValuePct,
                minScoreThreshold = riskEngine.getSettings().minModelQualityScore
            )
            _currentSignal.value = sig
        }
    }

    private fun scanSelectedMarkets() {
        val symbolsToScan = listOf("R_10", "R_25", "R_50", "R_75", "R_100", "1HZ10V", "1HZ100V", "frxEURUSD", "cryBTCUSD")
        val updatedMap = mutableMapOf<String, SignalCandidate>()

        for (sym in symbolsToScan) {
            val list = tickHistoryMap[sym]
            if (list != null) {
                val snapshot = synchronized(list) { list.toList() }
                if (snapshot.size >= 10) {
                    val sig = marketAnalysisEngine.evaluateSignal(
                        symbol = sym,
                        symbolName = getSymbolDisplayName(sym),
                        ticks = snapshot,
                        balance = getEffectiveBalance(),
                        configuredRiskPercent = riskEngine.getSettings().stakePercentOfBalance,
                        minEvThreshold = riskEngine.getSettings().minExpectedValuePct,
                        minScoreThreshold = riskEngine.getSettings().minModelQualityScore
                    )
                    updatedMap[sym] = sig

                    // If Autonomous Mode is ON, evaluate execution
                    if (executionEngine.tradingMode.value == TradingMode.AUTONOMOUS && sig.decision == "TRADE") {
                        triggerAutonomousExecution(sig)
                    }
                }
            }
        }
        _scannerSignals.value = updatedMap
    }

    private fun triggerAutonomousExecution(sig: SignalCandidate) {
        val currentProposal = _currentProposal.value
        val proposalId = if (currentProposal?.symbol == sig.symbol) currentProposal.id else "PROP_AUTO_${System.currentTimeMillis()}"

        executionEngine.executeOrder(
            signal = sig,
            proposalId = proposalId,
            userConfirmed = true
        ) { result ->
            result.fold(
                onSuccess = { order ->
                    _userMessage.value = "Auto-trade placed: ${order.symbol} (${order.contractType})"
                },
                onFailure = { err ->
                    _userMessage.value = "Auto-trade rejected: ${err.message}"
                }
            )
        }
    }

    fun selectSymbol(symbol: String) {
        _selectedSymbol.value = symbol
        webSocketClient.subscribeTick(symbol)
        requestFreshProposal(symbol)
        fetchRealtimeMarketDataWithRetrofit()
    }

    fun fetchRealtimeMarketDataWithRetrofit() {
        viewModelScope.launch(Dispatchers.IO) {
            // Fetch live tick for current market via Retrofit REST service
            val tickRes = retrofitClient.fetchLiveTick(_selectedSymbol.value)
            tickRes.onSuccess { tick ->
                handleIncomingTick(tick)
            }

            // Fetch active symbols via Retrofit REST service
            val symbolsRes = retrofitClient.fetchActiveSymbols()
            symbolsRes.onSuccess { symbols ->
                if (symbols.isNotEmpty()) {
                    symbols.take(5).forEach { sym ->
                        webSocketClient.subscribeTick(sym.symbol)
                    }
                }
            }
        }
    }

    fun requestFreshProposal(symbol: String) {
        val sig = _currentSignal.value
        val contractType = sig?.contractType ?: "DIGITDIFF"
        val barrier = sig?.barrier ?: "5"
        val stake = sig?.recommendedStake ?: 2.0

        webSocketClient.requestProposal(
            symbol = symbol,
            contractType = contractType,
            amount = stake,
            barrier = barrier
        )

        // Concurrently query price proposal via Retrofit REST service
        viewModelScope.launch(Dispatchers.IO) {
            val res = retrofitClient.fetchPriceProposal(
                symbol = symbol,
                contractType = contractType,
                amount = stake,
                barrier = barrier
            )
            res.onSuccess { prop ->
                if (_currentProposal.value == null || _currentProposal.value?.symbol == prop.symbol) {
                    _currentProposal.value = prop
                }
            }
        }
    }

    fun executeManualOrder(signal: SignalCandidate, isPaper: Boolean) {
        if (riskEngine.getSettings().emergencyStopActive) {
            _userMessage.value = "Order blocked: Emergency Stop is currently ACTIVE. Reset to resume trading."
            return
        }
        val proposalId = _currentProposal.value?.id ?: "PROP_MANUAL_${System.currentTimeMillis()}"
        if (isPaper) {
            executionEngine.setTradingMode(TradingMode.PAPER)
        } else {
            executionEngine.setTradingMode(TradingMode.ASSISTED)
        }

        executionEngine.executeOrder(
            signal = signal,
            proposalId = proposalId,
            userConfirmed = true
        ) { res ->
            res.fold(
                onSuccess = { order ->
                    _userMessage.value = "Order successfully executed (${order.mode}): ${order.orderId}"
                },
                onFailure = { err ->
                    _userMessage.value = "Order blocked: ${err.message}"
                }
            )
        }
    }

    fun setAutonomousTrading(enabled: Boolean) {
        if (enabled) {
            if (riskEngine.getSettings().emergencyStopActive) {
                _userMessage.value = "Cannot start autonomous trading: Emergency Stop is ACTIVE. Resume trading first."
                return
            }
            executionEngine.setTradingMode(TradingMode.AUTONOMOUS)
            _userMessage.value = "Autonomous Trading Activated within Risk Engine limits."
        } else {
            executionEngine.setTradingMode(TradingMode.PAPER)
            _userMessage.value = "Autonomous Trading Stopped. Reverted to Paper Mode."
        }
    }

    fun triggerEmergencyStop() {
        val updated = riskEngine.triggerEmergencyStop()
        executionEngine.emergencyHaltAll()
        viewModelScope.launch(Dispatchers.IO) {
            database.riskSettingsDao().setRiskSettings(updated)
            database.auditDao().insertLog(
                AuditLogEntity(
                    action = "EMERGENCY_STOP_TRIGGERED",
                    accountId = executionEngine.accountMode.value.name,
                    details = "CRITICAL: User triggered emergency stop. All trading halted immediately.",
                    level = "SECURITY"
                )
            )
        }
        _userMessage.value = "EMERGENCY STOP ACTIVATED! All paper and live trades halted."
    }

    fun clearEmergencyStop() {
        val updated = riskEngine.clearEmergencyStop()
        viewModelScope.launch(Dispatchers.IO) {
            database.riskSettingsDao().setRiskSettings(updated)
            database.auditDao().insertLog(
                AuditLogEntity(
                    action = "EMERGENCY_STOP_CLEARED",
                    accountId = executionEngine.accountMode.value.name,
                    details = "Emergency stop cleared. System back in normal monitoring mode.",
                    level = "INFO"
                )
            )
        }
        _userMessage.value = "Emergency stop cleared. System back in normal monitoring mode."
    }

    fun toggleEmergencyStop() {
        if (isEmergencyStopActive.value) {
            clearEmergencyStop()
        } else {
            triggerEmergencyStop()
        }
    }

    fun updateRiskSettings(settings: RiskSettingsEntity) {
        riskEngine.updateSettings(settings)
        viewModelScope.launch(Dispatchers.IO) {
            database.riskSettingsDao().setRiskSettings(settings)
            database.auditDao().insertLog(
                AuditLogEntity(
                    action = "RISK_SETTINGS_UPDATED",
                    accountId = "SETTINGS",
                    details = "Max stake: $${settings.maxStake}, Max daily loss: $${settings.maxDailyLoss}, Latency limit: ${settings.maxExecutionLatencyMs}ms",
                    level = "WARN"
                )
            )
        }
        _userMessage.value = "Risk parameters updated."
    }

    fun runBacktest(params: BacktestParams) {
        _isBacktesting.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val ticks = tickHistoryMap[params.symbol]?.map { it.quote } ?: emptyList()
            val result = backtestEngine.runBacktest(ticks, params)
            _backtestResult.value = result
            _isBacktesting.value = false
        }
    }

    fun redeemAccessCode(code: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = subscriptionManager.redeemCode(code)
            res.fold(
                onSuccess = { entity ->
                    database.auditDao().insertLog(
                        AuditLogEntity(
                            action = "ACCESS_CODE_REDEEMED",
                            accountId = "LICENSE",
                            details = "Code: ${entity.code}, Plan: ${entity.subscriptionPlan}",
                            level = "INFO"
                        )
                    )
                    onResult(true, "Subscription activated! Plan: ${entity.subscriptionPlan}")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Failed to validate access code.")
                }
            )
        }
    }

    fun connectDerivAccount(token: String) {
        if (token.isBlank()) {
            _userMessage.value = "Token cannot be empty."
            return
        }
        val cleanToken = token.trim()
        lastUsedToken = cleanToken
        webSocketClient.authorize(cleanToken)

        viewModelScope.launch(Dispatchers.IO) {
            database.auditDao().insertLog(
                AuditLogEntity(
                    action = "DERIV_OAUTH_TOKEN_SUBMITTED",
                    accountId = "DERIV",
                    details = "Authorization request dispatched to Deriv API.",
                    level = "SECURITY"
                )
            )

            // Concurrently query REST API for accounts and real-time balances
            val accountsRes = retrofitClient.fetchAccounts(cleanToken)
            accountsRes.onSuccess { accounts ->
                val first = accounts.firstOrNull()?.accountId
                if (first != null) {
                    val balRes = retrofitClient.fetchAccountBalance(first, cleanToken)
                    balRes.onSuccess { amt ->
                        webSocketClient.updateAccountBalance(amt)
                        _userMessage.value = "Connected to Deriv! Balance: $${String.format(java.util.Locale.US, "%,.2f", amt)}"
                    }
                }
            }
        }
    }

    fun refreshAccountBalance() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Request via WebSocket
            webSocketClient.requestBalance()

            // 2. Request via Retrofit REST service if token is known
            val token = lastUsedToken ?: webSocketClient.authorizedAccount.value?.token
            if (!token.isNullOrBlank()) {
                val accountsRes = retrofitClient.fetchAccounts(token)
                accountsRes.onSuccess { accounts ->
                    val accId = accounts.firstOrNull()?.accountId
                    if (accId != null) {
                        val balRes = retrofitClient.fetchAccountBalance(accId, token)
                        balRes.onSuccess { amt ->
                            webSocketClient.updateAccountBalance(amt)
                            _userMessage.value = "Live funds updated from Deriv API: $${String.format(java.util.Locale.US, "%,.2f", amt)}"
                        }
                    }
                }
            } else {
                _userMessage.value = "Balance update requested from Deriv gateway."
            }

            database.auditDao().insertLog(
                AuditLogEntity(
                    action = "BALANCE_RETRIEVED",
                    accountId = webSocketClient.authorizedAccount.value?.loginId ?: "DERIV",
                    details = "Account balance retrieved via Deriv API.",
                    level = "INFO"
                )
            )
        }
    }

    fun getTickHistory(symbol: String): List<TickData> {
        val list = tickHistoryMap[symbol] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    fun getSymbolDisplayName(symbol: String): String {
        return when (symbol) {
            "R_10" -> "Volatility 10 Index"
            "R_25" -> "Volatility 25 Index"
            "R_50" -> "Volatility 50 Index"
            "R_75" -> "Volatility 75 Index"
            "R_100" -> "Volatility 100 Index"
            "1HZ10V" -> "Volatility 10 (1s) Index"
            "1HZ25V" -> "Volatility 25 (1s) Index"
            "1HZ50V" -> "Volatility 50 (1s) Index"
            "1HZ75V" -> "Volatility 75 (1s) Index"
            "1HZ100V" -> "Volatility 100 (1s) Index"
            "CRASH_500" -> "Crash 500 Index"
            "BOOM_500" -> "Boom 500 Index"
            "frxEURUSD" -> "EUR/USD"
            "frxGBPUSD" -> "GBP/USD"
            "cryBTCUSD" -> "BTC/USD"
            "cryETHUSD" -> "ETH/USD"
            else -> symbol
        }
    }

    fun getEffectiveBalance(): Double {
        return if (executionEngine.accountMode.value == AccountMode.REAL) {
            webSocketClient.authorizedAccount.value?.balance ?: 0.0
        } else {
            executionEngine.paperBalance.value
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
