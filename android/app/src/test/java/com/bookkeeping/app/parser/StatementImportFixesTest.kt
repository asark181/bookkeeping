package com.bookkeeping.app.parser

import com.bookkeeping.app.data.category.CategoryResolver
import com.bookkeeping.app.data.db.entity.TransactionRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class StatementImportFixesTest {

    private val defaultNames = mapOf(
        "EXPENSE" to CategoryResolver.defaultNames("EXPENSE"),
        "INCOME" to CategoryResolver.defaultNames("INCOME")
    )

    // ---------- 收/支精确判断 ----------

    @Test
    fun testFlowToType() {
        assertEquals("EXPENSE", CsvStatementImporter.flowToType("支出"))
        assertEquals("INCOME", CsvStatementImporter.flowToType(" 收入 "))
        assertNull(CsvStatementImporter.flowToType("不计收支"))
        assertNull(CsvStatementImporter.flowToType("/"))
        assertNull(CsvStatementImporter.flowToType(""))
    }

    @Test
    fun testAlipayNeutralRowsSkippedAndStatusFilter() {
        val csv = """
            交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,资金状态
            A001,M1,2024-05-01 10:00:00,2024-05-01 10:00:05,2024-05-01 10:00:05,手机,即时到账,余额宝,转入,100.00,不计收支,交易成功,0.00,0.00,资金转移
            A002,M2,2024-05-01 11:00:00,2024-05-01 11:00:05,2024-05-01 11:00:05,手机,即时到账,天猫,耳机,199.00,支出,等待确认收货,0.00,0.00,已支出
            A003,M3,2024-05-01 12:00:00,2024-05-01 12:00:05,2024-05-01 12:00:05,手机,即时到账,淘宝,衣服,88.00,支出,等待付款,0.00,0.00,
            A004,M4,2024-05-01 13:00:00,2024-05-01 13:00:05,2024-05-01 13:00:05,手机,即时到账,淘宝,裤子,66.00,支出,交易关闭,0.00,0.00,
            A005,M5,2024-05-01 14:00:00,2024-05-01 14:00:05,2024-05-01 14:00:05,手机,即时到账,淘宝,退款,30.00,收入,退款成功,0.00,30.00,
            A006,M6,2024-05-01 15:00:00,2024-05-01 15:00:05,2024-05-01 15:00:05,手机,即时到账,公司,工资,8000.00,收入,交易成功,0.00,0.00,已收入
        """.trimIndent()
        val rows = CsvStatementImporter.parseStatementRows(csv.lines())
        assertEquals(listOf("A002", "A006"), rows.map { it.externalId })
        assertEquals("EXPENSE", rows[0].type)
        assertEquals(19900L, rows[0].amountCents)
        assertEquals("INCOME", rows[1].type)
        assertEquals("工资", rows[1].category)
    }

    @Test
    fun testWeChatNeutralRowSkipped() {
        val csv = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2024-05-01 08:30:15,零钱提现,招商银行(1234),/,/,¥100.00,零钱,提现已到账,W1,/,/
            2024-05-01 09:30:15,零钱通,零钱通,/,不计收支,¥50.00,零钱,成功,W2,/,/
            2024-05-01 10:30:15,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,零钱,支付成功,W3,/,/
        """.trimIndent()
        val rows = CsvStatementImporter.parseStatementRows(csv.lines())
        assertEquals(listOf("W3"), rows.map { it.externalId })
    }

    // ---------- 新版支付宝表头 ----------

    @Test
    fun testNewAlipayHeaderNotTreatedAsWeChat() {
        val csv = """
            支付宝交易明细
            交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
            2024-05-01 12:00:00,餐饮美食,肯德基,kfc***@x.com,香辣鸡腿堡,支出,21.00,花呗,交易成功,8180715437675713929,T1,
            2024-05-01 13:00:00,退款,淘宝,tb***@x.com,退款-衣服,不计收支,30.00,余额,退款成功,1401740433404052783,T2,
            2024-05-01 14:00:00,转账红包,张三,138****0000,转账,收入,50.00,余额,交易成功,6386952355458201165,T3,
        """.trimIndent()
        val lines = csv.lines()
        assertTrue(CsvStatementImporter.isAlipayHeader(lines[1]))
        assertFalse(CsvStatementImporter.isAlipayHeader("交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注"))

        val rows = CsvStatementImporter.parseStatementRows(lines)
        assertEquals(2, rows.size)
        assertEquals("支付宝", rows[0].channel)
        assertEquals("8180715437675713929", rows[0].externalId)
        assertEquals("肯德基 - 香辣鸡腿堡", rows[0].merchant)
        assertEquals("餐饮美食", rows[0].category)
        assertEquals("INCOME", rows[1].type)
        assertEquals(5000L, rows[1].amountCents)
    }

    // ---------- 时区 ----------

    @Test
    fun testDateParsedInShanghaiRegardlessOfDeviceZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(1714523415000L, CsvStatementImporter.parseDateTime("2024-05-01 08:30:15"))
            assertEquals(1714523400000L, CsvStatementImporter.parseDateTime("2024/5/1 8:30"))
            // Excel 序列日期 45413.35434 ≈ 2024-05-01 08:30:15（北京时间）
            assertEquals(1714523415000L, CsvStatementImporter.parseDateTime("45413.354340277778"))
            val rows = CsvStatementImporter.parseStatementRows(
                listOf(
                    "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号",
                    "2024-05-01 08:30:15,商户消费,全家,早餐,支出,15.50,零钱,支付成功,TZ1"
                )
            )
            assertEquals(1714523415000L, rows[0].timestamp)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // ---------- 墓碑不复活 ----------

    @Test
    fun testDeletedRecordByExternalIdNotResurrected() = runBlocking {
        val dao = FakeTransactionDao(
            mutableListOf(
                TransactionRecord(
                    id = 1, amountCents = 1500, type = "EXPENSE", channel = "微信支付", merchant = "瑞幸",
                    category = "餐饮美食", timestamp = 1714523415000L, externalId = "4200000001", isDeleted = true
                )
            ),
            defaultNames
        )
        val csv = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号\n" +
            "2024-05-01 08:30:15,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,零钱,支付成功,4200000001"
        val summary = CsvStatementImporter.importFromText(csv, dao)
        assertEquals(1, summary.skipped)
        assertEquals(0, summary.newInserted)
        assertEquals(1, dao.records.size)
        assertTrue(dao.records[0].isDeleted)
    }

    @Test
    fun testDeletedNearbyRecordWithoutExternalIdNotResurrected() = runBlocking {
        val dao = FakeTransactionDao(
            mutableListOf(
                TransactionRecord(
                    id = 1, amountCents = 1500, type = "EXPENSE", channel = "微信支付", merchant = "微信支付",
                    category = "日常支出", timestamp = 1714523410000L, isDeleted = true
                )
            ),
            defaultNames
        )
        val csv = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号\n" +
            "2024-05-01 08:30:15,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,零钱,支付成功,4200000009"
        val summary = CsvStatementImporter.importFromText(csv, dao)
        assertEquals(1, summary.skipped)
        assertEquals(0, summary.newInserted)
        assertEquals(0, summary.mergedExisting)
        assertEquals(1, dao.records.size)
        assertTrue(dao.records[0].isDeleted)
    }

    // ---------- 分类按分类表校正 ----------

    @Test
    fun testImportCategoryFallsBackWhenMissingInTable() = runBlocking {
        // 用户把「餐饮美食」删掉了，只剩兜底分类
        val dao = FakeTransactionDao(
            categoryNames = mapOf("EXPENSE" to setOf("其他支出", "交通出行"), "INCOME" to setOf("其他收入", "退款"))
        )
        val csv = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号\n" +
            "2024-05-01 08:30:15,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,零钱,支付成功,C1\n" +
            "2024-05-02 08:30:15,退款,美团外卖,美团外卖订单退款,收入,¥20.00,零钱,已退款,C2"
        val summary = CsvStatementImporter.importFromText(csv, dao)
        assertEquals(2, summary.newInserted)
        assertEquals("其他支出", dao.records.first { it.externalId == "C1" }.category)
        // 收入退款不再拿到支出分类「餐饮美食」
        assertEquals("退款", dao.records.first { it.externalId == "C2" }.category)
    }
}
