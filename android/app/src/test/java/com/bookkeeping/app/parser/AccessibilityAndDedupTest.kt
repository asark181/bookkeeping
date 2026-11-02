package com.bookkeeping.app.parser

import com.bookkeeping.app.data.db.entity.TransactionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AccessibilityAndDedupTest {

    private val now = LocalDateTime.of(2026, 10, 28, 12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
    private val wx = "com.tencent.mm.plugin"

    // ---------- 无障碍结果页识别 ----------

    @Test
    fun testChatPageWithHistoryBubbleIgnored() {
        val texts = listOf("张三", "微信转账", "¥10.00", "已收款", "恭喜发财，大吉大利", "微信红包", "5元")
        assertNull(AccessibilityPaymentDetector.detect("com.tencent.mm.ui.LauncherUI", texts, now))
        assertNull(AccessibilityPaymentDetector.detect("com.tencent.mm.ui.chatting.ChattingUI", texts, now))
        // 未知 Activity 时，「已收款」这种气泡也会出现的弱文案不认
        assertNull(AccessibilityPaymentDetector.detect(null, texts, now))
    }

    @Test
    fun testRedPacketBlessingWithoutResultIgnored() {
        val texts = listOf("恭喜发财，大吉大利", "微信红包", "8元")
        assertNull(AccessibilityPaymentDetector.detect("$wx.luckymoney.ui.LuckyMoneyNewReceiveUI", texts, now))
    }

    @Test
    fun testTransferResultPageIsExpense() {
        val d = AccessibilityPaymentDetector.detect(
            "$wx.remittance.ui.RemittanceResultNewUI",
            listOf("转账成功", "待张三确认收款", "¥100.00", "完成"),
            now
        )
        assertNotNull(d)
        assertEquals("EXPENSE", d!!.type)
        assertEquals(10000L, d.amountCents)
        assertEquals("张三", d.counterparty)
        assertEquals(AccessibilityPaymentDetector.KIND_TRANSFER, d.kind)
    }

    @Test
    fun testReceivedRedPacketIsIncome() {
        val d = AccessibilityPaymentDetector.detect(
            "$wx.luckymoney.ui.LuckyMoneyDetailUI",
            listOf("李四的红包", "恭喜发财，大吉大利", "0.88", "元", "已存入零钱，可直接消费"),
            now
        )
        assertNotNull(d)
        assertEquals("INCOME", d!!.type)
        assertEquals(88L, d.amountCents)
        assertEquals("李四", d.counterparty)
        assertEquals(AccessibilityPaymentDetector.KIND_RED_PACKET, d.kind)
    }

    @Test
    fun testReceivedTransferDetail() {
        val recent = AccessibilityPaymentDetector.detect(
            "$wx.remittance.ui.RemittanceDetailUI",
            listOf("你已收款", "¥50.00", "收款时间：2026年10月28日 11:55:00"),
            now
        )
        assertEquals("INCOME", recent?.type)
        assertEquals(5000L, recent?.amountCents)

        // 查看很久以前的转账详情：不记账
        val stale = AccessibilityPaymentDetector.detect(
            "$wx.remittance.ui.RemittanceDetailUI",
            listOf("你已收款", "¥50.00", "收款时间：2026年10月01日 10:00:00"),
            now
        )
        assertNull(stale)

        // 尚未确认收款：不记账
        val pending = AccessibilityPaymentDetector.detect(
            "$wx.remittance.ui.RemittanceDetailUI",
            listOf("待你确认收款", "¥50.00", "确认收款"),
            now
        )
        assertNull(pending)
    }

    @Test
    fun testOutgoingAcceptedTransferIsExpense() {
        val d = AccessibilityPaymentDetector.detect(
            "$wx.remittance.ui.RemittanceDetailUI",
            listOf("对方已收款", "¥20.00"),
            now
        )
        assertEquals("EXPENSE", d?.type)
        assertEquals("转账支出", d?.resultText)
    }

    @Test
    fun testStrongPhraseOnUnknownPage() {
        val other = "$wx.wallet_core.ui.SomeUnknownUI"
        assertNull(AccessibilityPaymentDetector.detect(other, listOf("已收款", "¥12.00"), now))
        val d = AccessibilityPaymentDetector.detect(other, listOf("支付成功", "全家便利店", "¥12.00"), now)
        assertEquals("EXPENSE", d?.type)
        assertEquals(1200L, d?.amountCents)
    }

    // ---------- 持久化去重窗口 ----------

    @Test
    fun testDedupWindow() {
        var persisted: Map<String, Long> = emptyMap()
        val w = DedupWindow(60_000L) { persisted = it }
        assertTrue(w.tryAcquire("k", 1_000L))
        assertFalse(w.tryAcquire("k", 30_000L))
        assertEquals(setOf("k"), persisted.keys)
        assertTrue(w.tryAcquire("k", 70_000L))

        // 模拟服务重启：从持久化内容恢复
        val restored = DedupWindow(60_000L, persisted)
        assertFalse(restored.tryAcquire("k", 80_000L))
    }

    // ---------- 并发查重 + 插入 ----------

    @Test
    fun testConcurrentInsertIsSerialized() = runBlocking {
        val dao = FakeTransactionDao(insertDelayMs = 5)
        val ts = System.currentTimeMillis()
        val jobs = (1..20).map {
            async(Dispatchers.Default) {
                DedupHelper.insertIfNotDuplicate(
                    dao,
                    TransactionRecord(amountCents = 2500, type = "EXPENSE", channel = "微信支付", merchant = "全家", timestamp = ts),
                    now = ts
                )
            }
        }
        val results = jobs.awaitAll()
        assertEquals(1, dao.records.size)
        assertEquals(1, results.count { it is DedupHelper.DedupResult.Inserted })
    }
}
