package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AccessCodeEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.RiskSettingsEntity
import com.example.data.model.StrategyEntity
import com.example.data.model.TradeOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trade_orders ORDER BY entryTime DESC")
    fun getAllOrders(): Flow<List<TradeOrderEntity>>

    @Query("SELECT * FROM trade_orders WHERE mode = :mode ORDER BY entryTime DESC")
    fun getOrdersByMode(mode: String): Flow<List<TradeOrderEntity>>

    @Query("SELECT * FROM trade_orders WHERE result = 'PENDING' ORDER BY entryTime DESC")
    fun getOpenOrders(): Flow<List<TradeOrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: TradeOrderEntity): Long

    @Update
    suspend fun updateOrder(order: TradeOrderEntity)

    @Query("SELECT * FROM trade_orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrderByOrderId(orderId: String): TradeOrderEntity?

    @Query("DELETE FROM trade_orders WHERE mode = 'BACKTEST'")
    suspend fun clearBacktestOrders()
}

@Dao
interface AccessCodeDao {
    @Query("SELECT * FROM access_codes")
    fun getAllCodes(): Flow<List<AccessCodeEntity>>

    @Query("SELECT * FROM access_codes WHERE code = :code LIMIT 1")
    suspend fun getCode(code: String): AccessCodeEntity?

    @Query("SELECT * FROM access_codes WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveSubscription(): Flow<AccessCodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCode(code: AccessCodeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultCodes(codes: List<AccessCodeEntity>)

    @Update
    suspend fun updateCode(code: AccessCodeEntity)
}

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategies ORDER BY updatedAt DESC")
    fun getAllStrategies(): Flow<List<StrategyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategy(strategy: StrategyEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultStrategies(strategies: List<StrategyEntity>)

    @Update
    suspend fun updateStrategy(strategy: StrategyEntity)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)
}

@Dao
interface RiskSettingsDao {
    @Query("SELECT * FROM risk_settings WHERE id = 1 LIMIT 1")
    fun getRiskSettings(): Flow<RiskSettingsEntity?>

    @Query("SELECT * FROM risk_settings WHERE id = 1 LIMIT 1")
    suspend fun getRiskSettingsSync(): RiskSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRiskSettings(settings: RiskSettingsEntity)
}
