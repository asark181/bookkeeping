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

    /** 导入查重：包含软删除的墓碑行，避免用户删除的记录被再次导入复活 */
    @Query("SELECT * FROM transactions WHERE externalId = :externalId LIMIT 1")
    suspend fun getByExternalIdIncludingDeleted(externalId: String): TransactionRecord?

    /** 导入近邻查重：包含软删除的墓碑行 */
    @Query("SELECT * FROM transactions WHERE amountCents = :amountCents AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun findNearbyByAmountIncludingDeleted(amountCents: Long, startTime: Long, endTime: Long): List<TransactionRecord>

    /** 读取某一收支类型下已存在的分类名称（categories 表），用于把推断出的分类落到真实存在的分类上 */
    @Query("SELECT name FROM categories WHERE type = :type")
    suspend fun getCategoryNamesByType(type: String): List<String>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionRecord?

    @Query("UPDATE transactions SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun markDeletedIds(ids: List<Long>, now: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: com.bookkeeping.app.data.db.entity.CapturedNotification): Long

    /** 只保留最近 keep 条抓取日志，其余删除 */
    @Query("DELETE FROM captured_notifications WHERE id NOT IN (SELECT id FROM captured_notifications ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    suspend fun trimNotificationLogs(keep: Int = 200)

    @Query("SELECT * FROM captured_notifications ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentNotificationLogsFlow(limit: Int = 10): Flow<List<com.bookkeeping.app.data.db.entity.CapturedNotification>>
}
