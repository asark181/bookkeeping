package com.bookkeeping.app.data.sync

import com.bookkeeping.app.data.db.entity.CategoryEntity
import com.bookkeeping.app.data.db.entity.TransactionRecord
import kotlinx.serialization.Serializable

/** 云端快照（加密前的 JSON）。图片只保存在本机，不进入快照 */
@Serializable
data class SyncSnapshot(
    val schema: Int = 1,
    val transactions: List<SyncTx> = emptyList(),
    val categoriesUpdatedAt: Long = 0,
    val categories: List<SyncCategory> = emptyList(),
    val budgetUpdatedAt: Long = 0,
    val budgetCents: Long = 0
)

@Serializable
data class SyncTx(
    val uid: String,
    val amountCents: Long,
    val type: String,
    val channel: String,
    val merchant: String? = null,
    val category: String,
    val timestamp: Long,
    val rawText: String? = null,
    val externalId: String? = null,
    val confidence: String = "HIGH",
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val note: String? = null
)

@Serializable
data class SyncCategory(
    val name: String,
    val type: String,
    val iconKey: String,
    val colorArgb: Long,
    val sortOrder: Int,
    val isProtected: Boolean
)

fun TransactionRecord.toSync() = SyncTx(
    uid, amountCents, type, channel, merchant, category, timestamp, rawText, externalId, confidence, updatedAt, isDeleted, note
)

/** 云端记录写入本机：保留本机 id 与图片 */
fun SyncTx.toRecord(local: TransactionRecord?) = TransactionRecord(
    id = local?.id ?: 0,
    amountCents = amountCents,
    type = type,
    channel = channel,
    merchant = merchant,
    category = category,
    timestamp = timestamp,
    rawText = rawText,
    externalId = externalId,
    confidence = confidence,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    note = note,
    images = local?.images,
    uid = uid
)

fun CategoryEntity.toSync() = SyncCategory(name, type, iconKey, colorArgb, sortOrder, isProtected)
fun SyncCategory.toEntity() = CategoryEntity(name = name, type = type, iconKey = iconKey, colorArgb = colorArgb, sortOrder = sortOrder, isProtected = isProtected)

data class TxMergeResult(
    /** 需要写入本机的云端记录（新增或比本机更新） */
    val toApply: List<SyncTx>,
    /** 合并后的完整列表，上传云端 */
    val merged: List<SyncTx>,
    /** 本机有云端没有、或本机更新：需要上传 */
    val needsUpload: Boolean
)

/**
 * 按 uid 合并，修改时间较新者胜出（相同时保留本机）；删除以墓碑形式参与合并。
 * 两台设备各自导入了同一笔官方账单（externalId 相同、uid 不同）时，不再插入对方那条，避免重复。
 */
fun mergeTransactions(local: List<SyncTx>, remote: List<SyncTx>): TxMergeResult {
    val localByUid = local.associateBy { it.uid }
    val remoteByUid = remote.associateBy { it.uid }
    // 已有的官方单号（本机未删除记录 + 本次已决定写入的云端记录），用于跳过跨设备重复导入的「孪生」账单
    val seenExternal = local.filter { !it.isDeleted && it.externalId != null }.mapNotNull { it.externalId }.toMutableSet()

    val toApply = mutableListOf<SyncTx>()
    val merged = LinkedHashMap<String, SyncTx>()
    var needsUpload = false

    for (r in remote) {
        val l = localByUid[r.uid]
        when {
            l == null -> {
                val ext = r.externalId
                if (r.isDeleted || ext == null || seenExternal.add(ext)) toApply += r
                merged[r.uid] = r
            }
            r.updatedAt > l.updatedAt -> { toApply += r; merged[r.uid] = r }
            else -> {
                merged[r.uid] = l
                if (l != r) needsUpload = true
            }
        }
    }
    for (l in local) {
        if (l.uid !in remoteByUid) {
            merged[l.uid] = l
            needsUpload = true
        }
    }
    return TxMergeResult(toApply, merged.values.toList(), needsUpload)
}
