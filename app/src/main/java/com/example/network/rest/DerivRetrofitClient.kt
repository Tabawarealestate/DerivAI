package com.example.network.rest

import android.util.Log
import com.example.network.ActiveSymbol
import com.example.network.ContractProposal
import com.example.network.TickData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class DerivRetrofitClient(
    private var baseUrl: String = "https://api.derivws.com/",
    private var appId: String = "1089"
) {
    private val tag = "DerivRetrofitClient"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) DerivAI/1.0")
                .header("Accept", "application/json")
                .header("Deriv-App-ID", appId)
            chain.proceed(requestBuilder.build())
        }
        .build()

    val apiService: DerivApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DerivApiService::class.java)
    }

    suspend fun fetchActiveSymbols(): Result<List<ActiveSymbol>> {
        return try {
            val response = apiService.getActiveSymbols()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.activeSymbols?.mapNotNull { item ->
                    val sym = item.underlyingSymbol ?: item.symbol ?: return@mapNotNull null
                    ActiveSymbol(
                        symbol = sym,
                        displayName = item.displayName ?: sym,
                        market = item.market ?: "synthetic_index",
                        submarket = item.submarket ?: "",
                        isSuspended = item.isTradingSuspended == 1,
                        spot = item.spot,
                        pip = item.pip ?: 0.01
                    )
                } ?: emptyList()
                Result.success(list)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.w(tag, "REST fetchActiveSymbols fallback: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchLiveTick(symbol: String): Result<TickData> {
        return try {
            val response = apiService.getLiveTick(symbol)
            if (response.isSuccessful && response.body()?.tick != null) {
                val item = response.body()!!.tick!!
                val quote = item.quote ?: 0.0
                val formatted = String.format(java.util.Locale.US, "%.5f", quote)
                val lastDigit = formatted.trimEnd('0').takeLast(1).toIntOrNull() ?: ((Math.abs(Math.round(quote * 1000)) % 10).toInt())
                val tick = TickData(
                    symbol = item.symbol ?: symbol,
                    quote = quote,
                    epoch = item.epoch ?: (System.currentTimeMillis() / 1000),
                    ask = item.ask ?: quote,
                    bid = item.bid ?: quote,
                    lastDigit = lastDigit,
                    receivedAtMs = System.currentTimeMillis()
                )
                Result.success(tick)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.w(tag, "REST fetchLiveTick fallback: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchPriceProposal(
        symbol: String,
        contractType: String,
        amount: Double,
        duration: Int = 5,
        durationUnit: String = "t",
        barrier: String? = null
    ): Result<ContractProposal> {
        return try {
            val request = RestProposalRequest(
                amount = amount,
                contractType = contractType,
                symbol = symbol,
                duration = duration,
                durationUnit = durationUnit,
                barrier = barrier
            )
            val response = apiService.getProposal(request)
            if (response.isSuccessful && response.body()?.proposal != null) {
                val prop = response.body()!!.proposal!!
                val p = ContractProposal(
                    id = prop.id ?: "",
                    symbol = symbol,
                    contractType = contractType,
                    stake = prop.askPrice ?: amount,
                    payout = prop.payout ?: (amount * 1.95),
                    spot = prop.spot ?: 0.0,
                    longcode = prop.longcode ?: "",
                    barrier = barrier,
                    duration = duration,
                    durationUnit = durationUnit
                )
                Result.success(p)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.w(tag, "REST fetchPriceProposal fallback: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchAccounts(token: String): Result<List<RestAccountItem>> {
        return try {
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val response = apiService.getAccounts(bearer, appId)
            if (response.isSuccessful && response.body()?.accounts != null) {
                Result.success(response.body()!!.accounts!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAccountBalance(accountId: String, token: String): Result<Double> {
        return try {
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val response = apiService.getAccountBalance(accountId, bearer, appId)
            if (response.isSuccessful && response.body()?.balance?.amount != null) {
                Result.success(response.body()!!.balance!!.amount!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
