package com.example.network.rest

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ActiveSymbolsRestResponse(
    @Json(name = "active_symbols") val activeSymbols: List<RestSymbolItem>? = null,
    @Json(name = "msg_type") val msgType: String? = null,
    @Json(name = "error") val error: RestErrorItem? = null
)

@JsonClass(generateAdapter = true)
data class RestSymbolItem(
    @Json(name = "symbol") val symbol: String? = null,
    @Json(name = "underlying_symbol") val underlyingSymbol: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "market") val market: String? = null,
    @Json(name = "market_display_name") val marketDisplayName: String? = null,
    @Json(name = "submarket") val submarket: String? = null,
    @Json(name = "submarket_display_name") val submarketDisplayName: String? = null,
    @Json(name = "spot") val spot: Double? = null,
    @Json(name = "pip") val pip: Double? = null,
    @Json(name = "is_trading_suspended") val isTradingSuspended: Int? = 0
)

@JsonClass(generateAdapter = true)
data class RestTickResponse(
    @Json(name = "tick") val tick: RestTickItem? = null,
    @Json(name = "error") val error: RestErrorItem? = null
)

@JsonClass(generateAdapter = true)
data class RestTickItem(
    @Json(name = "symbol") val symbol: String? = null,
    @Json(name = "quote") val quote: Double? = null,
    @Json(name = "epoch") val epoch: Long? = null,
    @Json(name = "ask") val ask: Double? = null,
    @Json(name = "bid") val bid: Double? = null
)

@JsonClass(generateAdapter = true)
data class RestProposalRequest(
    @Json(name = "proposal") val proposal: Int = 1,
    @Json(name = "amount") val amount: Double,
    @Json(name = "basis") val basis: String = "stake",
    @Json(name = "contract_type") val contractType: String,
    @Json(name = "currency") val currency: String = "USD",
    @Json(name = "duration") val duration: Int = 5,
    @Json(name = "duration_unit") val durationUnit: String = "t",
    @Json(name = "symbol") val symbol: String,
    @Json(name = "barrier") val barrier: String? = null
)

@JsonClass(generateAdapter = true)
data class RestProposalResponse(
    @Json(name = "proposal") val proposal: RestProposalItem? = null,
    @Json(name = "error") val error: RestErrorItem? = null
)

@JsonClass(generateAdapter = true)
data class RestProposalItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "ask_price") val askPrice: Double? = null,
    @Json(name = "payout") val payout: Double? = null,
    @Json(name = "spot") val spot: Double? = null,
    @Json(name = "longcode") val longcode: String? = null
)

@JsonClass(generateAdapter = true)
data class RestAccountsResponse(
    @Json(name = "accounts") val accounts: List<RestAccountItem>? = null,
    @Json(name = "error") val error: RestErrorItem? = null
)

@JsonClass(generateAdapter = true)
data class RestAccountItem(
    @Json(name = "account_id") val accountId: String? = null,
    @Json(name = "account_type") val accountType: String? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "is_virtual") val isVirtual: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class RestBalanceResponse(
    @Json(name = "balance") val balance: RestBalanceItem? = null,
    @Json(name = "error") val error: RestErrorItem? = null
)

@JsonClass(generateAdapter = true)
data class RestBalanceItem(
    @Json(name = "amount") val amount: Double? = null,
    @Json(name = "currency") val currency: String? = null
)

@JsonClass(generateAdapter = true)
data class RestPortfolioResponse(
    @Json(name = "portfolio") val portfolio: RestPortfolioItem? = null,
    @Json(name = "error") val error: RestErrorItem? = null
)

@JsonClass(generateAdapter = true)
data class RestPortfolioItem(
    @Json(name = "contracts") val contracts: List<RestPortfolioContract>? = null
)

@JsonClass(generateAdapter = true)
data class RestPortfolioContract(
    @Json(name = "contract_id") val contractId: Long? = null,
    @Json(name = "symbol") val symbol: String? = null,
    @Json(name = "buy_price") val buyPrice: Double? = null,
    @Json(name = "payout") val payout: Double? = null
)

@JsonClass(generateAdapter = true)
data class RestOtpResponse(
    @Json(name = "otp_url") val otpUrl: String? = null,
    @Json(name = "error") val error: RestErrorItem? = null
)

@JsonClass(generateAdapter = true)
data class RestErrorItem(
    @Json(name = "code") val code: String? = null,
    @Json(name = "message") val message: String? = null
)
