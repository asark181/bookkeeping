package com.bookkeeping.app.parser

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 无障碍页面识别（纯逻辑，便于单元测试）。
 *
 * 只在转账/红包/支付的「结果页」识别，聊天页一律忽略：
 * - 结果页判定：当前 Activity 是已知的结果/详情页，或节点中出现明确的结果文案（支付成功、转账成功、已收款……）；
 * - 收支方向由结果文案决定：「已收款」「已存入零钱」为收入，「转账成功」「待对方确认收款」「已被领取」为支出；
 * - 「恭喜发财」「微信红包」等聊天气泡文字不再触发记账；
 * - 页面上显示的交易时间早于 [STALE_MS] 的（查看历史详情）不记账。
 */
object AccessibilityPaymentDetector {

    const val KIND_TRANSFER = "TRANSFER"
    const val KIND_RED_PACKET = "RED_PACKET"
    const val KIND_PAYMENT = "PAYMENT"

    /** 页面上显示的交易时间早于该阈值视为查看历史，不入账 */
    const val STALE_MS = 30 * 60 * 1000L

    data class Detection(
        val amountCents: Long,
        val type: String,
        val kind: String,
        val counterparty: String?,
        /** 归一化后的结果文案（用于去重键） */
        val resultText: String
    ) {
        /** 去重键：金额 + 对象 + 结果页文案 */
        val dedupKey: String get() = "$kind|$type|$amountCents|${counterparty.orEmpty()}|$resultText"
    }

    /** 已知的微信结果/详情页 Activity（类名片段，忽略大小写） */
    private val RESULT_ACTIVITY_HINTS = listOf(
        "RemittanceResult", "RemittanceDetail", "RemittanceBusinessResult",
        "LuckyMoneyDetail", "LuckyMoneyBeforeDetail", "LuckyMoneyNotHookReceive", "LuckyMoneyNewReceive",
        "WalletPayResult", "PayResult", "OrderHandlerUI", "WalletOrderInfo"
    )

    /** 聊天页、首页：有历史转账/红包气泡，永远不在这里识别 */
    private val CHAT_ACTIVITY_HINTS = listOf("ChattingUI", "LauncherUI", "ChatroomInfoUI", "SnsTimeLineUI")

    /** 支出方向结果文案（需优先于收入判断，因为「对方已收款」包含「已收款」） */
    private val EXPENSE_RESULT = listOf(
        "待对方确认收款", "待对方确认收钱", "对方已收款", "对方已收钱", "已被接收", "已被领取",
        "等待对方领取", "转账成功", "支付成功", "付款成功", "发送成功"
    )

    /** 收入方向结果文案 */
    private val INCOME_RESULT = listOf("你已收款", "已收款", "已收钱", "已存入零钱", "已存入你的零钱", "收款成功")

    /** 只会出现在结果页上的强文案 */
    private val STRONG_RESULT = setOf("转账成功", "支付成功", "付款成功", "收款成功")

    private val AMOUNT_NODE = Regex("^[￥¥]?\\s*([\\d,]*\\d\\.\\d{2})\\s*元?$")
    private val AMOUNT_INLINE = listOf(
        Regex("[￥¥]\\s*([\\d,]*\\d(?:\\.\\d{1,2})?)"),
        Regex("([\\d,]*\\d\\.\\d{1,2})\\s*元")
    )

    private val COUNTERPARTY_PATTERNS = listOf(
        Regex("^(.{1,20}?)的红包$"),
        Regex("^来自(.{1,20}?)的(?:红包|转账)$"),
        Regex("转账给(.{1,20})$"),
        Regex("^待(.{1,20}?)确认收(?:款|钱)$"),
        Regex("^(.{1,20}?)向你转账$")
    )

    private val CN_TIME = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    private val ISO_TIME = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    fun isChatActivity(className: String?): Boolean =
        className != null && CHAT_ACTIVITY_HINTS.any { className.contains(it, ignoreCase = true) }

    fun isResultActivity(className: String?): Boolean =
        className != null && RESULT_ACTIVITY_HINTS.any { className.contains(it, ignoreCase = true) }

    /**
     * @param activityClass 最近一次 TYPE_WINDOW_STATE_CHANGED 的 event.className
     * @param texts 当前窗口节点文本（已限制深度与数量）
     */
    fun detect(activityClass: String?, texts: List<String>, now: Long = System.currentTimeMillis()): Detection? {
        if (texts.isEmpty() || isChatActivity(activityClass)) return null
        val nodes = texts.map { it.trim() }.filter { it.isNotEmpty() }

        // 结果文案必须是独立节点或节点内的完整短语，避免长段聊天文本误触发
        fun nodeHas(phrase: String) = nodes.any { it.length <= 30 && it.contains(phrase) }

        val expensePhrase = EXPENSE_RESULT.firstOrNull { nodeHas(it) }
        val incomePhrase = if (expensePhrase == null) INCOME_RESULT.firstOrNull { nodeHas(it) } else null
        val resultPhrase = expensePhrase ?: incomePhrase

        if (resultPhrase == null) return null
        // 气泡里也会出现「已收款」「已被接收」等弱文案：只有在已知结果页 Activity 上才认；
        // 非结果页（且非聊天页）只认「支付成功」「转账成功」这类只会出现在结果页的强文案
        if (!isResultActivity(activityClass) && (activityClass == null || resultPhrase !in STRONG_RESULT)) return null

        val joined = nodes.joinToString(" ")
        val activity = activityClass.orEmpty().lowercase()
        val kind = when {
            activity.contains("remittance") || joined.contains("转账") -> KIND_TRANSFER
            activity.contains("luckymoney") || joined.contains("红包") -> KIND_RED_PACKET
            else -> KIND_PAYMENT
        }

        val type = if (expensePhrase != null) "EXPENSE" else "INCOME"

        val shownTime = findShownTime(nodes)
        if (shownTime != null && now - shownTime > STALE_MS) return null

        val cents = findAmount(nodes) ?: return null
        val counterparty = nodes.firstNotNullOfOrNull { node ->
            COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { it.find(node)?.groupValues?.get(1)?.trim() }
        }?.takeIf { it.isNotBlank() && it != "对方" && it != "你" }

        return Detection(
            amountCents = cents,
            type = type,
            kind = kind,
            counterparty = counterparty,
            resultText = normalizePhrase(resultPhrase)
        )
    }

    /** 同一笔交易在不同结果页上的文案归一，避免「转账成功」与「待对方确认收款」各记一次 */
    private fun normalizePhrase(phrase: String): String = when (phrase) {
        "待对方确认收款", "待对方确认收钱", "对方已收款", "对方已收钱", "已被接收", "转账成功" -> "转账支出"
        "已被领取", "等待对方领取", "发送成功" -> "红包支出"
        "你已收款", "已收款", "已收钱", "收款成功" -> "收款"
        "已存入零钱", "已存入你的零钱" -> "存入零钱"
        else -> phrase
    }

    private fun findAmount(nodes: List<String>): Long? {
        for (node in nodes) {
            val m = AMOUNT_NODE.find(node) ?: continue
            val cents = NotificationParser.toCents(m.groupValues[1]) ?: continue
            if (cents > 0) return cents
        }
        for (pattern in AMOUNT_INLINE) {
            for (node in nodes) {
                val m = pattern.find(node) ?: continue
                val cents = NotificationParser.toCents(m.groupValues[1]) ?: continue
                if (cents > 0) return cents
            }
        }
        return null
    }

    private fun findShownTime(nodes: List<String>): Long? {
        for (node in nodes) {
            val m = CN_TIME.find(node) ?: ISO_TIME.find(node) ?: continue
            return try {
                val g = m.groupValues
                LocalDateTime.of(
                    g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(),
                    g[6].ifEmpty { "0" }.toInt()
                ).atZone(ZONE).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
}
