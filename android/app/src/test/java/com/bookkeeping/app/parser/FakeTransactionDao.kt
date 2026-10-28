package com.bookkeeping.app.parser

import com.bookkeeping.app.data.db.dao.TransactionDao
import com.bookkeeping.app.data.db.entity.CapturedNotification
import com.bookkeeping.app.data.db.entity.TransactionRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 单元测试用的内存 DAO，语义与 TransactionDao 中的 SQL 保持一致（isDeleted 过滤等）。
 * @param categoryNames 模拟 categories 表：type -> 名称集合
 * @param insertDelayMs 插入前挂起，用于放大并发竞态
 */
class FakeTransactionDao(
    val records: MutableList<TransactionRecord> = mutableListOf(),
    private val categoryNames: Map<String, Set<String>> = emptyMap(),
    private val insertDelayMs: Long = 0L
) : TransactionDao {

    val logs = mutableListOf<CapturedNotification>()
    private var nextId = 1000L

    override suspend fun insert(record: TransactionRecord): Long {
        if (insertDelayMs > 0) delay(insertDelayMs)
        synchronized(records) {
            val id = if (record.id != 0L) record.id else nextId++
            records.add(record.copy(id = id))
            return id
        }
    }

    override suspend fun update(record: TransactionRecord) {
        synchronized(records) {
            val idx = records.indexOfFirst { it.id == record.id }
            if (idx != -1) records[idx] = record
        }
    }

    private fun live() = synchronized(records) { records.filter { !it.isDeleted } }
    private fun all() = synchronized(records) { records.toList() }

    override fun getAllFlow(): Flow<List<TransactionRecord>> = flowOf(live())

    override suspend fun getByTimeRangeOnce(startTime: Long, endTime: Long): List<TransactionRecord> =
        live().filter { it.timestamp in startTime..endTime }.sortedByDescending { it.timestamp }

    override fun getByTimeRangeFlow(startTime: Long, endTime: Long): Flow<List<TransactionRecord>> =
        flowOf(live().filter { it.timestamp in startTime..endTime })

    override fun getTotalExpenseSince(startTime: Long): Flow<Long?> =
        flowOf(live().filter { it.type == "EXPENSE" && it.timestamp >= startTime }.sumOf { it.amountCents })

    override suspend fun getByExternalId(externalId: String): TransactionRecord? =
        live().firstOrNull { it.externalId == externalId }

    override suspend fun findNearbyByAmount(amountCents: Long, startTime: Long, endTime: Long): List<TransactionRecord> =
        live().filter { it.amountCents == amountCents && it.timestamp in startTime..endTime }

    override suspend fun getByExternalIdIncludingDeleted(externalId: String): TransactionRecord? =
        all().firstOrNull { it.externalId == externalId }

    override suspend fun findNearbyByAmountIncludingDeleted(amountCents: Long, startTime: Long, endTime: Long): List<TransactionRecord> =
        all().filter { it.amountCents == amountCents && it.timestamp in startTime..endTime }

    override suspend fun getCategoryNamesByType(type: String): List<String> =
        categoryNames[type].orEmpty().toList()

    override suspend fun getById(id: Long): TransactionRecord? = all().firstOrNull { it.id == id }

    override suspend fun getAllIncludingDeleted(): List<TransactionRecord> = all()

    override suspend fun markDeleted(id: Long, now: Long) {
        synchronized(records) {
            val idx = records.indexOfFirst { it.id == id }
            if (idx != -1) records[idx] = records[idx].copy(isDeleted = true, updatedAt = now)
        }
    }

    override suspend fun markDeletedIds(ids: List<Long>, now: Long) {
        ids.forEach { markDeleted(it, now) }
    }

    override suspend fun insertNotificationLog(log: CapturedNotification): Long {
        logs.add(log)
        return logs.size.toLong()
    }

    override suspend fun trimNotificationLogs(keep: Int) {
        val keepSet = logs.sortedByDescending { it.timestamp }.take(keep).toSet()
        logs.retainAll(keepSet)
    }

    override fun getRecentNotificationLogsFlow(limit: Int): Flow<List<CapturedNotification>> =
        flowOf(logs.sortedByDescending { it.timestamp }.take(limit))
}
