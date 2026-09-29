package com.example.network.rest

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DerivApiService {

    /**
     * Active Markets (https://developers.deriv.com/llms/active-symbols.md)
     * Retrieves all currently active underlying symbols and spot values.
     */
    @GET("trading/v1/options/active-symbols")
    suspend fun getActiveSymbols(
        @Query("brief") brief: Int = 1,
        @Query("product_type") productType: String = "basic"
    ): Response<ActiveSymbolsRestResponse>

    /**
     * Live Spot Price (https://developers.deriv.com/llms/ticks.md)
     */
    @GET("trading/v1/options/ticks/{symbol}")
    suspend fun getLiveTick(
        @Path("symbol") symbol: String
    ): Response<RestTickResponse>

    /**
     * Price Proposals (https://developers.deriv.com/llms/proposal.md)
     * Request a contract price and payout proposal before buying.
     */
    @POST("trading/v1/options/proposal")
    suspend fun getProposal(
        @Body request: RestProposalRequest
    ): Response<RestProposalResponse>

    /**
     * Account list and access (https://developers.deriv.com/llms/get-accounts.md)
     */
    @GET("trading/v1/options/accounts")
    suspend fun getAccounts(
        @Header("Authorization") bearerToken: String,
        @Header("Deriv-App-ID") appId: String? = null
    ): Response<RestAccountsResponse>

    /**
     * Account Balance (https://developers.deriv.com/llms/balance.md)
     */
    @GET("trading/v1/options/accounts/{accountId}/balance")
    suspend fun getAccountBalance(
        @Path("accountId") accountId: String,
        @Header("Authorization") bearerToken: String,
        @Header("Deriv-App-ID") appId: String? = null
    ): Response<RestBalanceResponse>

    /**
     * Portfolio / Open Contracts (https://developers.deriv.com/llms/portfolio.md)
     */
    @GET("trading/v1/options/accounts/{accountId}/portfolio")
    suspend fun getPortfolio(
        @Path("accountId") accountId: String,
        @Header("Authorization") bearerToken: String,
        @Header("Deriv-App-ID") appId: String? = null
    ): Response<RestPortfolioResponse>

    /**
     * Request OTP WebSocket URL for authenticated trading (https://developers.deriv.com/llms/api-overview.md)
     */
    @POST("trading/v1/options/accounts/{accountId}/otp")
    suspend fun requestOtp(
        @Path("accountId") accountId: String,
        @Header("Authorization") bearerToken: String,
        @Header("Deriv-App-ID") appId: String? = null
    ): Response<RestOtpResponse>
}
