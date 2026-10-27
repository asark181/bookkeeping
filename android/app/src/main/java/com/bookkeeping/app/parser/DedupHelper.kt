package com.bookkeeping.app.parser

import android.util.Log
import com.bookkeeping.app.data.db.dao.TransactionDao
import com.bookkeeping.app.data.db.entity.TransactionRecord

/**
 * 跨渠道通知与多通道去重合并引擎
 * - 双方商户名称互相包含：窗口放宽至 120 秒
 * - 同渠道且双方无商户：收紧至 10 秒（防止短时间内两笔相同金额消费被误合并）
 * - 跨渠道常规窗口：60 秒
 */
object DedupHelper {

    private const val TAG = "DedupHelper"
    const val BASE_WINDOW_MS = 60_000L
    const val MERCHANT_WINDOW_MS = 120_000L
    const val SAME_CHANNEL_NO_MERCHANT_MS = 10_000L

    /**
     * 寻找可能重复的历史交易记录
     * @return 若存在已记录的重复交易则返回，否则返回 null
     */
    suspend fun findDuplicate(
        dao: TransactionDao,
        amountCents: Long,
        type: String,
        merchant: String?,
        channel: String,
        now: Long = System.currentTimeMillis()
    ): TransactionRecord? {
        val recentList = dao.getByTimeRangeOnce(now - MERCHANT_WINDOW_MS, now)
        return recentList.find { existing ->
            if (existing.amountCents != amountCents || existing.type != type) return@find false

            val timeDiff = now - existing.timestamp
            if (timeDiff < 0) return@find false

            val sameMerchant = merchantMatch(merchant, existing.merchant)
            val sameChannel = existing.channel == channel
            val bothNoMerchant = merchant.isNullOrBlank() && existing.merchant.isNullOrBlank()

            val window = when {
                sameMerchant -> MERCHANT_WINDOW_MS
                sameChannel && bothNoMerchant -> SAME_CHANNEL_NO_MERCHANT_MS
                else -> BASE_WINDOW_MS
            }
            timeDiff < window
        }
    }

    fun merchantMatch(new: String?, old: String?): Boolean {
        if (new.isNullOrBlank() || old.isNullOrBlank()) return false
        return new.contains(old) || old.contains(new)
    }

    /**
     * 若匹配到重复记录，则按渠道优先级与商户详细程度升级已有记录
     */
    suspend fun mergeIfDuplicate(
        dao: TransactionDao,
        existing: TransactionRecord,
        newChannel: String,
        newMerchant: String?
    ) {
        val shouldUpdateChannel = isMoreSpecificChannel(newChannel, existing.channel)
        val shouldUpdateMerchant = !newMerchant.isNullOrBlank() &&
            (existing.merchant.isNullOrBlank() || newMerchant.length > existing.merchant.length)

        if (shouldUpdateChannel || shouldUpdateMerchant) {
            val updated = existing.copy(
                channel = if (shouldUpdateChannel) newChannel else existing.channel,
                merchant = if (shouldUpdateMerchant) newMerchant else existing.merchant,
                updatedAt = System.currentTimeMillis()
            )
            dao.update(updated)
            Log.d(TAG, "已合并更新交易记录 id=${existing.id}: channel=${updated.channel}, merchant=${updated.merchant}")
        }
    }

    private fun isMoreSpecificChannel(new: String, existing: String): Boolean {
        val priority = mapOf(
            "招商银行" to 10, "工商银行" to 10, "示例银行" to 10, "农业银行" to 10,
            "中国银行" to 10, "交通银行" to 10, "浦发银行" to 10, "平安银行" to 10,
            "银行卡" to 5, "支付宝" to 3, "微信支付" to 3, "云闪付" to 3
        )
        return (priority[new] ?: 0) > (priority[existing] ?: 0)
    }
}
