package com.bookkeeping.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
