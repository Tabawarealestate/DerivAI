package com.example.engine

import com.example.data.db.AuditDao
import com.example.data.db.TradeDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.TradeOrderEntity
import com.example.network.DerivWebSocketClient
import com.example.network.OpenContractInfo
import com.example.network.TickData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class TradingMode {
    PAPER,
    ASSISTED,
    AUTONOMOUS
}

enum class AccountMode {
    DEMO,
    REAL
}

class ExecutionEngine(
    private val webSocketClient: DerivWebSocketClient,
    private val tradeDao: TradeDao,
    private val auditDao: AuditDao,
    private val riskEngine: RiskEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _tradingMode = MutableStateFlow(TradingMode.PAPER)
    val tradingMode: StateFlow<TradingMode> = _tradingMode.asStateFlow()

    private val _accountMode = MutableStateFlow(AccountMode.DEMO)
    val accountMode: StateFlow<AccountMode> = _accountMode.asStateFlow()

    private val _paperBalance = MutableStateFlow(10000.0)
    val paperBalance: StateFlow<Double> = _paperBalance.asStateFlow()

    private val _activeSimulatedContracts = MutableStateFlow<List<TradeOrderEntity>>(emptyList())
    val activeSimulatedContracts: StateFlow<List<TradeOrderEntity>> = _activeSimulatedContracts.asStateFlow()

    private val _lastExecutionLog = MutableStateFlow<String?>(null)
    val lastExecutionLog: StateFlow<String?> = _lastExecutionLog.asStateFlow()

    init {
        // Listen to live ticks to evaluate paper trades
        scope.launch {
            webSocketClient.liveTicks.collect { tick ->
                evaluateOpenPaperTrades(tick)
            }
        }

        // Listen to real Deriv open contract updates
        scope.launch {
            webSocketClient.openContractUpdates.collect { openInfo ->
                handleRealContractUpdate(openInfo)
            }
        }
    }

    fun setTradingMode(mode: TradingMode) {
        _tradingMode.value = mode
        scope.launch {
            auditDao.insertLog(
                AuditLogEntity(
                    action = "TRADING_MODE_CHANGED",
                    accountId = _accountMode.value.name,
                    details = "Trading mode switched to ${mode.name}",
                    level = "WARN"
                )
            )
        }
    }

    fun setAccountMode(mode: AccountMode) {
        _accountMode.value = mode
        scope.launch {
            auditDao.insertLog(
                AuditLogEntity(
                    action = "ACCOUNT_MODE_CHANGED",
                    accountId = mode.name,
                    details = "Account environment set to ${mode.name}",
                    level = if (mode == AccountMode.REAL) "SECURITY" else "INFO"
                )
            )
        }
    }

    fun executeOrder(
        signal: SignalCandidate,
        proposalId: String?,
        userConfirmed: Boolean = false,
        onComplete: (Result<TradeOrderEntity>) -> Unit
    ) {
        val currentAccount = _accountMode.value
        val currentMode = _tradingMode.value

        // Check user authorization for Assisted mode
        if (currentMode == TradingMode.ASSISTED && !userConfirmed) {
            onComplete(Result.failure(IllegalStateException("Assisted mode requires explicit user confirmation 'APPROVE TRADE'")))
            return
        }

        val balance = if (currentAccount == AccountMode.REAL) {
            webSocketClient.authorizedAccount.value?.balance ?: 0.0
        } else {
            _paperBalance.value
        }

        val latencyMs = when (val state = webSocketClient.connectionState.value) {
            is com.example.network.ConnectionState.Connected -> state.latencyMs
            else -> 9999L
        }

        // Run through Global Risk Engine
        val riskCheck = riskEngine.evaluateTradeRisk(
            signal = signal,
            currentBalance = balance,
            activeOrdersCount = _activeSimulatedContracts.value.size,
            latencyMs = latencyMs
        )

        if (!riskCheck.allowed) {
            _lastExecutionLog.value = "Risk Engine Rejected: ${riskCheck.reason}"
            scope.launch {
                auditDao.insertLog(
                    AuditLogEntity(
                        action = "TRADE_REJECTED_BY_RISK_ENGINE",
                        accountId = currentAccount.name,
                        details = "Symbol: ${signal.symbol}, Reason: ${riskCheck.reason}",
                        level = "WARN"
                    )
                )
            }
            onComplete(Result.failure(IllegalStateException(riskCheck.reason)))
            return
        }

        val approvedStake = riskCheck.approvedStake
        val uniqueOrderId = "ORD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6).uppercase()}"

        // Deduct stake for paper mode or dispatch real order
        if (currentAccount == AccountMode.DEMO || currentMode == TradingMode.PAPER) {
            executePaperTrade(signal, uniqueOrderId, approvedStake, latencyMs, onComplete)
        } else {
            executeRealTrade(signal, proposalId, uniqueOrderId, approvedStake, latencyMs, onComplete)
        }
    }

    private fun executePaperTrade(
        signal: SignalCandidate,
        orderId: String,
        stake: Double,
        latencyMs: Long,
        onComplete: (Result<TradeOrderEntity>) -> Unit
    ) {
        val currentSpot = webSocketClient.getLatestTick(signal.symbol)?.quote ?: 100.0
        val payoutMultiplier = if (signal.contractType == "DIGITDIFF") 1.09 else 1.95
        val expectedPayout = stake * payoutMultiplier

        _paperBalance.value = (_paperBalance.value - stake).coerceAtLeast(0.0)

        val order = TradeOrderEntity(
            orderId = orderId,
            accountId = "DEMO_ACCOUNT",
            symbol = signal.symbol,
            symbolName = signal.symbolName,
            contractType = signal.contractType,
            signalId = signal.signalId,
            proposalId = "PROP_SIM_${System.currentTimeMillis()}",
            buyTransactionId = "TX_SIM_${System.currentTimeMillis()}",
            entryTime = System.currentTimeMillis(),
            entryPrice = currentSpot,
            stake = stake,
            expectedPayout = expectedPayout,
            executionLatencyMs = latencyMs,
            result = "PENDING",
            mode = "PAPER",
            riskState = "NORMAL"
        )

        scope.launch {
            tradeDao.insertOrder(order)
            _activeSimulatedContracts.value = _activeSimulatedContracts.value + order
            auditDao.insertLog(
                AuditLogEntity(
                    action = "PAPER_TRADE_EXECUTED",
                    accountId = "DEMO_PAPER",
                    details = "Symbol: ${signal.symbol}, Type: ${signal.contractType}, Stake: $stake",
                    level = "INFO"
                )
            )
        }

        _lastExecutionLog.value = "Paper order placed: ${signal.symbol} ${signal.contractType} ($$stake)"
        onComplete(Result.success(order))
    }

    private fun executeRealTrade(
        signal: SignalCandidate,
        proposalId: String?,
        orderId: String,
        stake: Double,
        latencyMs: Long,
        onComplete: (Result<TradeOrderEntity>) -> Unit
    ) {
        if (proposalId.isNullOrBlank()) {
            onComplete(Result.failure(IllegalStateException("No valid Deriv proposal ID available for execution")))
            return
        }

        val sendTime = System.currentTimeMillis()
        webSocketClient.executeBuy(
            proposalId = proposalId,
            price = stake,
            uniqueExecutionId = orderId
        ) { result ->
            val executionDuration = System.currentTimeMillis() - sendTime
            result.fold(
                onSuccess = { buyRes ->
                    val order = TradeOrderEntity(
                        orderId = orderId,
                        accountId = webSocketClient.authorizedAccount.value?.loginId ?: "CR_REAL",
                        symbol = signal.symbol,
                        symbolName = signal.symbolName,
                        contractType = signal.contractType,
                        signalId = signal.signalId,
                        proposalId = proposalId,
                        buyTransactionId = buyRes.transactionId.toString(),
                        entryTime = System.currentTimeMillis(),
                        entryPrice = signal.recommendedStake,
                        stake = buyRes.buyPrice,
                        expectedPayout = buyRes.buyPrice * 1.95,
                        executionLatencyMs = executionDuration,
                        result = "PENDING",
                        mode = "REAL",
                        riskState = "NORMAL"
                    )

                    scope.launch {
                        tradeDao.insertOrder(order)
                        auditDao.insertLog(
                            AuditLogEntity(
                                action = "REAL_TRADE_EXECUTED",
                                accountId = order.accountId,
                                details = "Contract ID: ${buyRes.contractId}, Stake: ${buyRes.buyPrice}",
                                level = "SECURITY"
                            )
                        )
                    }

                    _lastExecutionLog.value = "Real order executed on Deriv: Contract #${buyRes.contractId}"
                    onComplete(Result.success(order))
                },
                onFailure = { err ->
                    _lastExecutionLog.value = "Execution failed on Deriv: ${err.message}"
                    scope.launch {
                        auditDao.insertLog(
                            AuditLogEntity(
                                action = "REAL_TRADE_FAILED",
                                accountId = "REAL",
                                details = "Error: ${err.message}",
                                level = "WARN"
                            )
                        )
                    }
                    onComplete(Result.failure(err))
                }
            )
        }
    }

    private fun evaluateOpenPaperTrades(tick: TickData) {
        val currentOpen = _activeSimulatedContracts.value
        if (currentOpen.isEmpty()) return

        val now = System.currentTimeMillis()
        val remaining = mutableListOf<TradeOrderEntity>()

        for (order in currentOpen) {
            if (order.symbol == tick.symbol) {
                // Settle simulated order after 5 seconds or 5 ticks
                val elapsedSec = (now - order.entryTime) / 1000
                if (elapsedSec >= 5) {
                    settlePaperOrder(order, tick)
                } else {
                    remaining.add(order)
                }
            } else {
                remaining.add(order)
            }
        }
        _activeSimulatedContracts.value = remaining
    }

    private fun settlePaperOrder(order: TradeOrderEntity, tick: TickData) {
        val won = when (order.contractType) {
            "DIGITDIFF" -> {
                val entryDigit = (Math.abs(Math.round(order.entryPrice * 1000)) % 10).toInt()
                tick.lastDigit != entryDigit
            }
            "DIGITMATCH" -> {
                val entryDigit = (Math.abs(Math.round(order.entryPrice * 1000)) % 10).toInt()
                tick.lastDigit == entryDigit
            }
            "DIGITEVEN" -> tick.lastDigit % 2 == 0
            "DIGITODD" -> tick.lastDigit % 2 != 0
            "CALL" -> tick.quote > order.entryPrice
            "PUT" -> tick.quote < order.entryPrice
            else -> tick.quote > order.entryPrice
        }

        val resultStr = if (won) "WON" else "LOST"
        val profitLoss = if (won) (order.expectedPayout - order.stake) else -order.stake
        val actualPayout = if (won) order.expectedPayout else 0.0

        if (won) {
            _paperBalance.value += order.expectedPayout
        }

        val closedOrder = order.copy(
            exitTime = System.currentTimeMillis(),
            exitPrice = tick.quote,
            actualPayout = actualPayout,
            profitLoss = profitLoss,
            result = resultStr,
            exitReason = "Contract expired at spot ${tick.quote} (Digit ${tick.lastDigit})"
        )

        scope.launch {
            tradeDao.updateOrder(closedOrder)
            riskEngine.onTradeCompleted(closedOrder)
            auditDao.insertLog(
                AuditLogEntity(
                    action = "PAPER_TRADE_SETTLED",
                    accountId = "DEMO_PAPER",
                    details = "Order ${order.orderId}: $resultStr, P/L: $${String.format(java.util.Locale.US, "%.2f", profitLoss)}",
                    level = "INFO"
                )
            )
        }
    }

    private fun handleRealContractUpdate(info: OpenContractInfo) {
        if (info.isSold) {
            scope.launch {
                val existing = tradeDao.getOrderByOrderId(info.contractId.toString())
                if (existing != null && existing.result == "PENDING") {
                    val updated = existing.copy(
                        exitTime = System.currentTimeMillis(),
                        exitPrice = info.currentSpot,
                        actualPayout = if (info.status == "won") info.payout else 0.0,
                        profitLoss = info.profit,
                        result = if (info.status == "won") "WON" else "LOST",
                        exitReason = "Settled by Deriv: ${info.status}"
                    )
                    tradeDao.updateOrder(updated)
                    riskEngine.onTradeCompleted(updated)
                }
            }
        }
    }
}
