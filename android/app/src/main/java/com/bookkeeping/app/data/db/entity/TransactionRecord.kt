package com.bookkeeping.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["channel"]),
        Index(value = ["type"]),
        Index(value = ["externalId"])
    ]
)
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 金额（单位：分，例如 2500 表示 25.00 元） */
    val amountCents: Long,
    /** 类型：EXPENSE（支出） / INCOME（收入） / TRANSFER（转账） */
    val type: String,
    /** 支付渠道：微信支付 / 支付宝 / 美团支付 / 京东支付 / 抖音支付 / 银行卡等 */
    val channel: String,
    /** 商家 / 收款方 / 交易对象 */
    val merchant: String?,
    /** 分类名称（如：餐饮、交通、日常购物） */
    val category: String = "未分类",
    /** 交易发生时间毫秒时间戳 */
    val timestamp: Long = System.currentTimeMillis(),
    /** 原始通知或短信文本摘要，方便复核 */
    val rawText: String? = null,
    /** 微信/支付宝/官方对账单的全局唯一交易单号（用于精准去重） */
    val externalId: String? = null,
    /** 解析置信度：HIGH（高置信自动入账） / UNCERTAIN（需人工确认） */
    val confidence: String = "HIGH",
    /** 记录更新时间戳（用于增量合并同步） */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 软删除墓碑标记 */
    val isDeleted: Boolean = false
)
