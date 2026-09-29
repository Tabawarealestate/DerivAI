package com.example.network

data class ActiveSymbol(
    val symbol: String,
    val displayName: String,
    val market: String,
    val submarket: String,
    val isSuspended: Boolean,
    val spot: Double?,
    val pip: Double
)

data class TickData(
    val symbol: String,
    val quote: Double,
    val epoch: Long,
    val ask: Double = quote,
    val bid: Double = quote,
    val lastDigit: Int = (Math.round(quote * 100) % 10).toInt(),
    val receivedAtMs: Long = System.currentTimeMillis()
)

data class ContractProposal(
    val id: String,
    val symbol: String,
    val contractType: String,
    val stake: Double,
    val payout: Double,
    val spot: Double,
    val longcode: String,
    val barrier: String? = null,
    val duration: Int = 1,
    val durationUnit: String = "t",
    val requestedAtMs: Long = System.currentTimeMillis()
)

data class BuyResult(
    val contractId: Long,
    val transactionId: Long,
    val buyPrice: Double,
    val balanceAfter: Double?,
    val longcode: String? = null
)

data class OpenContractInfo(
    val contractId: Long,
    val symbol: String,
    val contractType: String,
    val buyPrice: Double,
    val payout: Double,
    val entrySpot: Double,
    val currentSpot: Double,
    val profit: Double,
    val isSold: Boolean,
    val status: String, // open, won, lost
    val dateExpiry: Long = 0,
    val tickCount: Int = 0
)

data class DerivAccount(
    val loginId: String,
    val currency: String,
    val balance: Double,
    val email: String,
    val isVirtual: Boolean,
    val scopes: List<String>,
    val token: String = ""
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val latencyMs: Long) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
