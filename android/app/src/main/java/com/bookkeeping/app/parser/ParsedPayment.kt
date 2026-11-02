package com.bookkeeping.app.parser

enum class ParseConfidence {
    /** 明确支付/动账，可直接静默入账 */
    HIGH,
    /** 截断、模糊或疑似营销，建议弹出悬浮确认 */
    UNCERTAIN
}

data class ParsedPayment(
    /** 解析出的金额（分） */
    val amountCents: Long,
    /** 交易方向：EXPENSE（支出） / INCOME（收入） */
    val type: String,
    /** 支付渠道（如：微信支付、支付宝、云闪付、招商银行等） */
    val channel: String,
    /** 商家 / 收款方（如：美团外卖、全家便利店等，可能为空） */
    val merchant: String?,
    /** 置信度评级 */
    val confidence: ParseConfidence = ParseConfidence.HIGH,
    /** 存疑原因（若置信度为 UNCERTAIN） */
    val uncertainReason: String? = null,
    /** 原始通知文本摘要 */
    val rawText: String
)
