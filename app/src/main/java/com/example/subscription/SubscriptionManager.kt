package com.example.subscription

import com.example.data.db.AccessCodeDao
import com.example.data.model.AccessCodeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubscriptionManager(
    private val accessCodeDao: AccessCodeDao
) {
    val activeSubscription: Flow<AccessCodeEntity?> = accessCodeDao.getActiveSubscription()

    val isSubscribed: Flow<Boolean> = activeSubscription.map { code ->
        if (code == null) false
        else if (code.status != "ACTIVE") false
        else {
            val exp = code.expirationDate ?: Long.MAX_VALUE
            System.currentTimeMillis() <= exp
        }
    }

    suspend fun redeemCode(rawCode: String): Result<AccessCodeEntity> {
        val cleanCode = rawCode.trim().uppercase()
        val found = accessCodeDao.getCode(cleanCode)
            ?: return Result.failure(IllegalArgumentException("Invalid access code. Please check code or contact admin on WhatsApp."))

        if (found.status == "REVOKED") {
            return Result.failure(IllegalStateException("This access code has been revoked by administration."))
        }
        if (found.status == "SUSPENDED") {
            return Result.failure(IllegalStateException("This subscription access code has been suspended."))
        }

        val now = System.currentTimeMillis()
        if (found.expirationDate != null && now > found.expirationDate) {
            val expired = found.copy(status = "EXPIRED")
            accessCodeDao.updateCode(expired)
            return Result.failure(IllegalStateException("This access code expired on ${java.util.Date(found.expirationDate)}."))
        }

        // Activate if UNUSED
        val activated = if (found.status == "UNUSED") {
            val exp = now + (found.durationDays.toLong() * 24 * 3600 * 1000)
            found.copy(
                status = "ACTIVE",
                activationDate = now,
                expirationDate = exp
            )
        } else {
            found.copy(status = "ACTIVE")
        }

        accessCodeDao.updateCode(activated)
        return Result.success(activated)
    }

    suspend fun createCode(
        code: String,
        plan: String,
        durationDays: Int,
        maxDevices: Int = 1,
        notes: String = ""
    ) {
        val entity = AccessCodeEntity(
            code = code.trim().uppercase(),
            subscriptionPlan = plan,
            durationDays = durationDays,
            activationDate = null,
            expirationDate = null,
            status = "UNUSED",
            maxDevices = maxDevices,
            assignedUser = "New Client",
            notes = notes
        )
        accessCodeDao.insertCode(entity)
    }

    suspend fun revokeCode(code: String) {
        val found = accessCodeDao.getCode(code)
        if (found != null) {
            accessCodeDao.updateCode(found.copy(status = "REVOKED"))
        }
    }

    suspend fun extendCode(code: String, additionalDays: Int) {
        val found = accessCodeDao.getCode(code)
        if (found != null) {
            val currentExp = found.expirationDate ?: System.currentTimeMillis()
            val newExp = currentExp + (additionalDays.toLong() * 24 * 3600 * 1000)
            accessCodeDao.updateCode(found.copy(expirationDate = newExp, status = "ACTIVE"))
        }
    }
}
