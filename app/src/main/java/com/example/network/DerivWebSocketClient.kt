package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DerivWebSocketClient(
    private var appId: String = "1089",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    private val tag = "DerivWebSocketClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastPingSendTime: Long = 0
    private var reconnectAttempts = 0

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _activeSymbols = MutableStateFlow<List<ActiveSymbol>>(emptyList())
    val activeSymbols: StateFlow<List<ActiveSymbol>> = _activeSymbols.asStateFlow()

    private val _liveTicks = MutableSharedFlow<TickData>(replay = 50)
    val liveTicks: SharedFlow<TickData> = _liveTicks.asSharedFlow()

    private val _latestTickPerSymbol = ConcurrentHashMap<String, TickData>()
    fun getLatestTick(symbol: String): TickData? = _latestTickPerSymbol[symbol]

    private val _authorizedAccount = MutableStateFlow<DerivAccount?>(null)
    val authorizedAccount: StateFlow<DerivAccount?> = _authorizedAccount.asStateFlow()

    private val _balanceUpdates = MutableSharedFlow<Double>(replay = 1)
    val balanceUpdates: SharedFlow<Double> = _balanceUpdates.asSharedFlow()

    private val _proposals = MutableSharedFlow<ContractProposal>(replay = 5)
    val proposals: SharedFlow<ContractProposal> = _proposals.asSharedFlow()

    private val _openContractUpdates = MutableSharedFlow<OpenContractInfo>(replay = 10)
    val openContractUpdates: SharedFlow<OpenContractInfo> = _openContractUpdates.asSharedFlow()

    private val _rawResponses = MutableSharedFlow<JSONObject>(extraBufferCapacity = 50)
    val rawResponses: SharedFlow<JSONObject> = _rawResponses.asSharedFlow()

    private val subscribedTicks = ConcurrentHashMap.newKeySet<String>()
    private val reqIdCounter = AtomicInteger(1)
    private val pendingBuyRequests = ConcurrentHashMap<Int, (Result<BuyResult>) -> Unit>()
    private val processedExecutionIds = ConcurrentHashMap.newKeySet<String>()

    private val endpoints = listOf(
        "wss://api.derivws.com/trading/v1/options/ws/public",
        "wss://api.derivws.com/trading/v1/options/ws/public?app_id=",
        "wss://api.derivws.com/trading/v1/options/ws/demo?app_id=",
        "wss://api.derivws.com/trading/v1/options/ws/real?app_id="
    )
    private var currentEndpointIndex = 0

    init {
        connect()
    }

    fun setAppId(newAppId: String) {
        if (newAppId.isNotBlank() && newAppId != appId) {
            appId = newAppId
            disconnect()
            connect()
        }
    }

    fun connect(customUrl: String? = null) {
        if (_connectionState.value is ConnectionState.Connecting || _connectionState.value is ConnectionState.Connected) {
            return
        }
        _connectionState.value = ConnectionState.Connecting

        val base = endpoints[currentEndpointIndex % endpoints.size]
        val url = customUrl ?: if (base.endsWith("=")) "$base$appId" else base
        Log.d(tag, "Connecting to WebSocket: $url (endpoint $currentEndpointIndex)")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) DerivAI/1.0")
            .header("Origin", "https://app.deriv.com")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket Connected successfully to $url")
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.Connected(latencyMs = 45)
                startHeartbeat()
                requestActiveSymbols()
                resubscribeTicks()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    handleMessage(json)
                } catch (e: Exception) {
                    Log.e(tag, "Failed parsing message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket Closed: $code / $reason")
                _connectionState.value = ConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket Failure: ${t.message} on $url")
                currentEndpointIndex++ // Rotate to next official gateway on failure
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection error")
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        pingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun scheduleReconnect() {
        pingJob?.cancel()
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val backoffMs = (1000L * (1 shl minOf(reconnectAttempts, 5))).coerceAtMost(30000L)
            reconnectAttempts++
            Log.d(tag, "Reconnecting in ${backoffMs}ms (attempt $reconnectAttempts)")
            delay(backoffMs)
            connect()
        }
    }

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(20000)
                lastPingSendTime = System.currentTimeMillis()
                sendJson(JSONObject().put("ping", 1))
            }
        }
    }

    private fun handleMessage(json: JSONObject) {
        if (json.has("error")) {
            val errObj = json.getJSONObject("error")
            val code = errObj.optString("code")
            val message = errObj.optString("message")
            Log.w(tag, "Deriv API Error: $code: $message")
            val reqId = json.optInt("req_id", -1)
            if (reqId != -1 && pendingBuyRequests.containsKey(reqId)) {
                pendingBuyRequests.remove(reqId)?.invoke(Result.failure(Exception("$code: $message")))
            }
            return
        }

        val msgType = json.optString("msg_type")

        when (msgType) {
            "ping" -> {
                val roundTrip = (System.currentTimeMillis() - lastPingSendTime).coerceAtLeast(1)
                _connectionState.value = ConnectionState.Connected(latencyMs = roundTrip)
            }
            "active_symbols" -> {
                val array = json.optJSONArray("active_symbols") ?: JSONArray()
                val list = mutableListOf<ActiveSymbol>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val symbol = obj.optString("symbol", obj.optString("underlying_symbol"))
                    val displayName = obj.optString("display_name", symbol)
                    val market = obj.optString("market", "synthetic_index")
                    val submarket = obj.optString("submarket", "")
                    val suspended = obj.optInt("is_trading_suspended", 0) == 1
                    val spot = obj.optDouble("spot", Double.NaN).takeIf { !it.isNaN() }
                    val pip = obj.optDouble("pip", 0.01)

                    if (symbol.isNotBlank()) {
                        list.add(
                            ActiveSymbol(
                                symbol = symbol,
                                displayName = displayName,
                                market = market,
                                submarket = submarket,
                                isSuspended = suspended,
                                spot = spot,
                                pip = pip
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    _activeSymbols.value = list
                }
            }
            "tick" -> {
                val tickObj = json.optJSONObject("tick")
                if (tickObj != null) {
                    val symbol = tickObj.optString("symbol")
                    val quote = tickObj.optDouble("quote", 0.0)
                    val epoch = tickObj.optLong("epoch", System.currentTimeMillis() / 1000)
                    val ask = tickObj.optDouble("ask", quote)
                    val bid = tickObj.optDouble("bid", quote)
                    val formatted = String.format(java.util.Locale.US, "%.5f", quote)
                    val lastDigit = formatted.trimEnd('0').takeLast(1).toIntOrNull()
                        ?: ((Math.abs(Math.round(quote * 1000)) % 10).toInt())

                    val tick = TickData(
                        symbol = symbol,
                        quote = quote,
                        epoch = epoch,
                        ask = ask,
                        bid = bid,
                        lastDigit = lastDigit,
                        receivedAtMs = System.currentTimeMillis()
                    )
                    _latestTickPerSymbol[symbol] = tick
                    scope.launch { _liveTicks.emit(tick) }
                }
            }
            "history" -> {
                scope.launch { _rawResponses.emit(json) }
            }
            "proposal" -> {
                val propObj = json.optJSONObject("proposal")
                if (propObj != null) {
                    val id = propObj.optString("id")
                    val askPrice = propObj.optDouble("ask_price", 0.0)
                    val payout = propObj.optDouble("payout", 0.0)
                    val spot = propObj.optDouble("spot", 0.0)
                    val longcode = propObj.optString("longcode", "")
                    val echoReq = json.optJSONObject("echo_req")
                    val symbol = echoReq?.optString("symbol") ?: ""
                    val contractType = echoReq?.optString("contract_type") ?: ""
                    val stake = echoReq?.optDouble("amount") ?: askPrice
                    val barrier = echoReq?.optString("barrier")
                    val duration = echoReq?.optInt("duration", 1) ?: 1
                    val durationUnit = echoReq?.optString("duration_unit", "t") ?: "t"

                    val proposal = ContractProposal(
                        id = id,
                        symbol = symbol,
                        contractType = contractType,
                        stake = stake,
                        payout = payout,
                        spot = spot,
                        longcode = longcode,
                        barrier = barrier,
                        duration = duration,
                        durationUnit = durationUnit
                    )
                    scope.launch { _proposals.emit(proposal) }
                }
            }
            "authorize" -> {
                val authObj = json.optJSONObject("authorize")
                if (authObj != null) {
                    val loginId = authObj.optString("loginid", "CR_DEMO")
                    val currency = authObj.optString("currency", "USD")
                    val balance = authObj.optDouble("balance", 0.0)
                    val email = authObj.optString("email", "trader@deriv.ai")
                    val isVirtual = authObj.optInt("is_virtual", 0) == 1
                    val scopesArr = authObj.optJSONArray("scopes") ?: JSONArray()
                    val scopes = mutableListOf<String>()
                    for (i in 0 until scopesArr.length()) {
                        scopes.add(scopesArr.getString(i))
                    }
                    val echo = json.optJSONObject("echo_req")
                    val token = echo?.optString("authorize") ?: ""
                    _authorizedAccount.value = DerivAccount(
                        loginId = loginId,
                        currency = currency,
                        balance = balance,
                        email = email,
                        isVirtual = isVirtual,
                        scopes = scopes,
                        token = token
                    )
                    scope.launch { _balanceUpdates.emit(balance) }
                    // Automatically subscribe to live balance stream
                    subscribeBalance()
                }
            }
            "balance" -> {
                val balObj = json.optJSONObject("balance")
                if (balObj != null) {
                    val newBalance = balObj.optDouble("balance", 0.0)
                    val currency = balObj.optString("currency", "USD")
                    val currentAcc = _authorizedAccount.value
                    if (currentAcc != null) {
                        _authorizedAccount.value = currentAcc.copy(
                            balance = newBalance,
                            currency = if (currency.isNotBlank()) currency else currentAcc.currency
                        )
                    } else {
                        val loginId = balObj.optString("loginid", balObj.optString("id", "CR_USER"))
                        _authorizedAccount.value = DerivAccount(
                            loginId = loginId,
                            currency = currency,
                            balance = newBalance,
                            email = "trader@deriv.com",
                            isVirtual = loginId.startsWith("VR"),
                            scopes = listOf("read", "trade"),
                            token = ""
                        )
                    }
                    scope.launch { _balanceUpdates.emit(newBalance) }
                }
            }
            "buy" -> {
                val reqId = json.optInt("req_id", -1)
                val buyObj = json.optJSONObject("buy")
                if (buyObj != null) {
                    val contractId = buyObj.optLong("contract_id", 0)
                    val txId = buyObj.optLong("transaction_id", 0)
                    val buyPrice = buyObj.optDouble("buy_price", 0.0)
                    val balanceAfter = buyObj.optDouble("balance_after", Double.NaN).takeIf { !it.isNaN() }
                    val longcode = buyObj.optString("longcode")

                    if (balanceAfter != null && _authorizedAccount.value != null) {
                        _authorizedAccount.value = _authorizedAccount.value!!.copy(balance = balanceAfter)
                        scope.launch { _balanceUpdates.emit(balanceAfter) }
                    }

                    val result = BuyResult(
                        contractId = contractId,
                        transactionId = txId,
                        buyPrice = buyPrice,
                        balanceAfter = balanceAfter,
                        longcode = longcode
                    )
                    pendingBuyRequests.remove(reqId)?.invoke(Result.success(result))

                    // Subscribe to open contract updates
                    subscribeOpenContract(contractId)
                }
            }
            "proposal_open_contract" -> {
                val pocObj = json.optJSONObject("proposal_open_contract")
                if (pocObj != null) {
                    val contractId = pocObj.optLong("contract_id")
                    val symbol = pocObj.optString("underlying", "")
                    val contractType = pocObj.optString("contract_type", "")
                    val buyPrice = pocObj.optDouble("buy_price", 0.0)
                    val payout = pocObj.optDouble("payout", 0.0)
                    val entrySpot = pocObj.optDouble("entry_spot", 0.0)
                    val currentSpot = pocObj.optDouble("current_spot", 0.0)
                    val profit = pocObj.optDouble("profit", 0.0)
                    val isSold = pocObj.optInt("is_sold", 0) == 1
                    val status = pocObj.optString("status", if (isSold) "settled" else "open")
                    val dateExpiry = pocObj.optLong("date_expiry", 0)
                    val tickCount = pocObj.optInt("tick_count", 0)

                    val info = OpenContractInfo(
                        contractId = contractId,
                        symbol = symbol,
                        contractType = contractType,
                        buyPrice = buyPrice,
                        payout = payout,
                        entrySpot = entrySpot,
                        currentSpot = currentSpot,
                        profit = profit,
                        isSold = isSold,
                        status = status,
                        dateExpiry = dateExpiry,
                        tickCount = tickCount
                    )
                    scope.launch { _openContractUpdates.emit(info) }
                }
            }
        }
    }

    fun requestActiveSymbols() {
        val req = JSONObject()
            .put("active_symbols", "brief")
            .put("product_type", "basic")
        sendJson(req)
    }

    fun subscribeTick(symbol: String) {
        subscribedTicks.add(symbol)
        val req = JSONObject()
            .put("ticks", symbol)
            .put("subscribe", 1)
        sendJson(req)
    }

    fun unsubscribeTick(symbol: String) {
        subscribedTicks.remove(symbol)
        val req = JSONObject()
            .put("forget", symbol)
        sendJson(req)
    }

    private fun resubscribeTicks() {
        for (symbol in subscribedTicks) {
            val req = JSONObject()
                .put("ticks", symbol)
                .put("subscribe", 1)
            sendJson(req)
        }
    }

    fun requestTickHistory(symbol: String, count: Int = 100, reqId: Int = reqIdCounter.incrementAndGet()) {
        val req = JSONObject()
            .put("ticks_history", symbol)
            .put("count", count)
            .put("end", "latest")
            .put("style", "ticks")
            .put("req_id", reqId)
        sendJson(req)
    }

    fun requestProposal(
        symbol: String,
        contractType: String,
        amount: Double,
        duration: Int = 1,
        durationUnit: String = "t",
        barrier: String? = null
    ) {
        val req = JSONObject()
            .put("proposal", 1)
            .put("amount", amount)
            .put("basis", "stake")
            .put("currency", "USD")
            .put("symbol", symbol)
            .put("duration", duration)
            .put("duration_unit", durationUnit)
            .put("contract_type", contractType)

        if (!barrier.isNullOrBlank()) {
            req.put("barrier", barrier)
        }
        sendJson(req)
    }

    fun authorize(token: String) {
        if (token.isBlank()) return
        val req = JSONObject().put("authorize", token)
        sendJson(req)
    }

    fun requestBalance() {
        val req = JSONObject().put("balance", 1)
        sendJson(req)
    }

    fun subscribeBalance() {
        val req = JSONObject()
            .put("balance", 1)
            .put("subscribe", 1)
        sendJson(req)
    }

    fun updateAccountBalance(newBalance: Double, currency: String? = null) {
        val current = _authorizedAccount.value
        if (current != null) {
            _authorizedAccount.value = current.copy(
                balance = newBalance,
                currency = currency ?: current.currency
            )
        } else {
            _authorizedAccount.value = DerivAccount(
                loginId = "CR_USER",
                currency = currency ?: "USD",
                balance = newBalance,
                email = "trader@deriv.com",
                isVirtual = false,
                scopes = listOf("read", "trade"),
                token = ""
            )
        }
        scope.launch { _balanceUpdates.emit(newBalance) }
    }

    fun executeBuy(
        proposalId: String,
        price: Double,
        uniqueExecutionId: String,
        callback: (Result<BuyResult>) -> Unit
    ) {
        // Idempotency check
        if (processedExecutionIds.contains(uniqueExecutionId)) {
            callback(Result.failure(IllegalStateException("Duplicate trade blocked by execution guard (ID: $uniqueExecutionId)")))
            return
        }
        processedExecutionIds.add(uniqueExecutionId)

        val reqId = reqIdCounter.incrementAndGet()
        pendingBuyRequests[reqId] = callback

        val req = JSONObject()
            .put("buy", proposalId)
            .put("price", price)
            .put("req_id", reqId)
        sendJson(req)
    }

    fun subscribeOpenContract(contractId: Long) {
        val req = JSONObject()
            .put("proposal_open_contract", 1)
            .put("contract_id", contractId)
            .put("subscribe", 1)
        sendJson(req)
    }

    fun sellContract(contractId: Long, price: Double = 0.0) {
        val req = JSONObject()
            .put("sell", contractId)
            .put("price", price)
        sendJson(req)
    }

    private fun sendJson(json: JSONObject) {
        val ws = webSocket
        if (ws != null) {
            ws.send(json.toString())
        }
    }
}
