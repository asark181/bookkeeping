package com.bookkeeping.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bookkeeping.app.data.db.entity.TransactionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransactionRecord): Long

    @Update
    suspend fun update(record: TransactionRecord)

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<TransactionRecord>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRangeOnce(startTime: Long, endTime: Long): List<TransactionRecord>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getByTimeRangeFlow(startTime: Long, endTime: Long): Flow<List<TransactionRecord>>

    @Query("SELECT SUM(amountCents) FROM transactions WHERE isDeleted = 0 AND type = 'EXPENSE' AND timestamp >= :startTime")
    fun getTotalExpenseSince(startTime: Long): Flow<Long?>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND externalId = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: String): TransactionRecord?

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND amountCents = :amountCents AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun findNearbyByAmount(amountCents: Long, startTime: Long, endTime: Long): List<TransactionRecord>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionRecord?

    @Query("UPDATE transactions SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: com.bookkeeping.app.data.db.entity.CapturedNotification): Long

    @Query("SELECT * FROM captured_notifications ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentNotificationLogsFlow(limit: Int = 10): Flow<List<com.bookkeeping.app.data.db.entity.CapturedNotification>>
}
