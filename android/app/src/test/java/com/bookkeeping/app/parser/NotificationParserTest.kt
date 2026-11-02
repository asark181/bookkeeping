package com.bookkeeping.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {

    @Test
    fun testWeChatNormalExpense() {
        val parsed = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "已支付¥25.00"
        )
        assertNotNull(parsed)
        assertEquals(2500L, parsed?.amountCents)
        assertEquals("EXPENSE", parsed?.type)
        assertEquals("微信支付", parsed?.channel)
    }

    @Test
    fun testWeChatMerchantExpense() {
        val parsed = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "向【全家便利店】付款¥15.50"
        )
        assertNotNull(parsed)
        assertEquals(1550L, parsed?.amountCents)
        assertEquals("EXPENSE", parsed?.type)
        assertEquals("全家便利店", parsed?.merchant)
    }

    @Test
    fun testWeChatPaymentCodeExpense() {
        val parsed = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "付款码已支付¥16.00"
        )
        assertNotNull(parsed)
        assertEquals(1600L, parsed?.amountCents)
        assertEquals("EXPENSE", parsed?.type)
    }

    @Test
    fun testWeChatRefund() {
        val parsed = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "退款¥12.80到账"
        )
        assertNotNull(parsed)
        assertEquals(1280L, parsed?.amountCents)
        assertEquals("INCOME", parsed?.type)
    }

    @Test
    fun testAlipayMerchantExpense() {
        val parsed = NotificationParser.parse(
            packageName = NotificationParser.PKG_ALIPAY,
            title = "支付宝",
            content = "向【星巴克咖啡】成功付款38.00元"
        )
        assertNotNull(parsed)
        assertEquals(3800L, parsed?.amountCents)
        assertEquals("EXPENSE", parsed?.type)
        assertEquals("星巴克咖啡", parsed?.merchant)
        assertEquals("支付宝", parsed?.channel)
    }

    @Test
    fun testAlipayGeneralExpense() {
        val parsed = NotificationParser.parse(
            packageName = NotificationParser.PKG_ALIPAY,
            title = "支付宝",
            content = "你有一笔42.75元的支出"
        )
        assertNotNull(parsed)
        assertEquals(4275L, parsed?.amountCents)
        assertEquals("EXPENSE", parsed?.type)
    }

    @Test
    fun testWeChatTransferExpense() {
        val parsed1 = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "向【张三】转账¥0.01"
        )
        assertNotNull(parsed1)
        assertEquals(1L, parsed1?.amountCents)
        assertEquals("EXPENSE", parsed1?.type)
        assertEquals("张三", parsed1?.merchant)

        val parsed2 = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "微信转账：已转账¥10.00"
        )
        assertNotNull(parsed2)
        assertEquals(1000L, parsed2?.amountCents)
        assertEquals("EXPENSE", parsed2?.type)
    }

    @Test
    fun testWeChatRedPacketExpense() {
        val parsed = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "微信红包已发出0.01元"
        )
        assertNotNull(parsed)
        assertEquals(1L, parsed?.amountCents)
        assertEquals("EXPENSE", parsed?.type)
    }

    @Test
    fun testNoiseFiltering() {
        val noise = NotificationParser.parse(
            packageName = NotificationParser.PKG_WECHAT,
            title = "微信支付",
            content = "干饭人，来领外卖优惠券"
        )
        assertNull(noise)
    }

    @Test
    fun testMeituanExpense() {
        val parsed1 = NotificationParser.parse(
            packageName = NotificationParser.PKG_MEITUAN,
            title = "美团",
            content = "您的外卖订单已支付¥28.50"
        )
        assertNotNull(parsed1)
        assertEquals(2850L, parsed1?.amountCents)
        assertEquals("EXPENSE", parsed1?.type)
        assertEquals("美团支付", parsed1?.channel)

        val parsed2 = NotificationParser.parse(
            packageName = NotificationParser.PKG_MEITUAN_TAKEOUT,
            title = "美团外卖",
            content = "扣款25.00元成功"
        )
        assertNotNull(parsed2)
        assertEquals(2500L, parsed2?.amountCents)
        assertEquals("EXPENSE", parsed2?.type)
    }

    @Test
    fun testJdExpense() {
        val parsed1 = NotificationParser.parse(
            packageName = NotificationParser.PKG_JD,
            title = "京东",
            content = "使用【京东白条】支付16.90元"
        )
        assertNotNull(parsed1)
        assertEquals(1690L, parsed1?.amountCents)
        assertEquals("EXPENSE", parsed1?.type)
        assertEquals("京东支付", parsed1?.channel)

        val parsed2 = NotificationParser.parse(
            packageName = NotificationParser.PKG_JD_JR,
            title = "京东金融",
            content = "订单已支付¥99.00"
        )
        assertNotNull(parsed2)
        assertEquals(9900L, parsed2?.amountCents)
        assertEquals("EXPENSE", parsed2?.type)
    }

    @Test
    fun testDouyinExpense() {
        val parsed1 = NotificationParser.parse(
            packageName = NotificationParser.PKG_DOUYIN,
            title = "抖音",
            content = "抖音支付成功 ¥49.00"
        )
        assertNotNull(parsed1)
        assertEquals(4900L, parsed1?.amountCents)
        assertEquals("EXPENSE", parsed1?.type)
        assertEquals("抖音支付", parsed1?.channel)

        val parsed2 = NotificationParser.parse(
            packageName = NotificationParser.PKG_DOUYIN_LIVE,
            title = "抖音商城",
            content = "您购买的商品已付款¥12.80"
        )
        assertNotNull(parsed2)
        assertEquals(1280L, parsed2?.amountCents)
        assertEquals("EXPENSE", parsed2?.type)
    }

    // ---------- 方向优先判断 / 微信仅解析服务号 ----------

    @Test
    fun testAlipayIncomingTransferIsIncome() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_ALIPAY, "支付宝", "张三向你转账10.00元")
        assertNotNull(parsed)
        assertEquals("INCOME", parsed?.type)
        assertEquals(1000L, parsed?.amountCents)
        assertEquals("张三", parsed?.merchant)
    }

    @Test
    fun testAlipayOutgoingTransferStillExpense() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_ALIPAY, "支付宝", "你向李四转账20.00元")
        assertEquals("EXPENSE", parsed?.type)
        assertEquals(2000L, parsed?.amountCents)
    }

    @Test
    fun testAlipayRefundIsIncome() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_ALIPAY, "支付宝", "退款到账：美团订单退款12.50元")
        assertEquals("INCOME", parsed?.type)
        assertEquals(1250L, parsed?.amountCents)
    }

    @Test
    fun testWeChatReceivedTransferIsIncome() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_WECHAT, "微信支付", "收到转账¥100.00")
        assertEquals("INCOME", parsed?.type)
        assertEquals(10000L, parsed?.amountCents)

        val parsed2 = NotificationParser.parse(NotificationParser.PKG_WECHAT, "微信转账", "张三向你转账¥66.00，已收款")
        assertEquals("INCOME", parsed2?.type)
        assertEquals(6600L, parsed2?.amountCents)
        assertEquals("张三", parsed2?.merchant)
    }

    @Test
    fun testWeChatReceivedRedPacketIsIncome() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_WECHAT, "微信红包", "你收到一个红包0.88元，已存入零钱")
        assertEquals("INCOME", parsed?.type)
        assertEquals(88L, parsed?.amountCents)
    }

    @Test
    fun testWeChatCollectionAssistantIsIncome() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_WECHAT, "微信收款助手", "微信支付收款12.00元")
        assertEquals("INCOME", parsed?.type)
        assertEquals(1200L, parsed?.amountCents)
    }

    @Test
    fun testWeChatOutgoingTransferAcceptedIsExpense() {
        // 「对方已收款」包含「已收款」，但对自己是支出
        val parsed = NotificationParser.parse(NotificationParser.PKG_WECHAT, "微信支付", "转账¥50.00 对方已收款")
        assertEquals("EXPENSE", parsed?.type)
        assertEquals(5000L, parsed?.amountCents)
    }

    @Test
    fun testWeChatFriendChatIsIgnored() {
        assertNull(NotificationParser.parse(NotificationParser.PKG_WECHAT, "李四", "我转账100元给你"))
        assertNull(NotificationParser.parse(NotificationParser.PKG_WECHAT, "李四", "[转账]请收款"))
        assertNull(NotificationParser.parse(NotificationParser.PKG_WECHAT, "相亲相爱一亲友", "妈妈: [微信红包]恭喜发财，大吉大利 5元"))
        // 群名里带「微信支付」也不算服务号
        assertNull(NotificationParser.parse(NotificationParser.PKG_WECHAT, "微信支付交流群", "已支付¥25.00"))
    }

    @Test
    fun testWeChatServiceTitleWithCount() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_WECHAT, "微信支付(2条)", "[2条]已支付¥25.00")
        assertEquals("EXPENSE", parsed?.type)
        assertEquals(2500L, parsed?.amountCents)
    }

    @Test
    fun testSuspectedPayment() {
        assertTrue(NotificationParser.isSuspectedPayment(NotificationParser.PKG_WECHAT, "李四", "[转账]请收款"))
        assertTrue(NotificationParser.isSuspectedPayment(NotificationParser.PKG_WECHAT, "微信支付", "你有一笔交易"))
        assertFalse(NotificationParser.isSuspectedPayment(NotificationParser.PKG_WECHAT, "李四", "晚上一起吃饭吗"))
        assertFalse(NotificationParser.isSuspectedPayment(NotificationParser.PKG_WECHAT, "李四", "我转账100元给你"))
        assertFalse(NotificationParser.isSuspectedPayment(NotificationParser.PKG_JD, "京东", "新品上架，快来看看"))
    }

    @Test
    fun testGeneralRefundIsIncome() {
        val parsed = NotificationParser.parse(NotificationParser.PKG_MEITUAN, "美团", "您的订单退款25.00元已到账")
        assertEquals("INCOME", parsed?.type)
        assertEquals(2500L, parsed?.amountCents)
        assertEquals("美团支付退款", parsed?.merchant)
    }

    @Test
    fun testDetectDirection() {
        assertEquals("INCOME", NotificationParser.detectDirection("张三向你转账10元"))
        assertEquals("EXPENSE", NotificationParser.detectDirection("向张三转账10元"))
        assertEquals("EXPENSE", NotificationParser.detectDirection("对方已收款"))
        assertEquals("EXPENSE", NotificationParser.detectDirection("收款方：全家便利店 已支付¥10"))
    }
}
