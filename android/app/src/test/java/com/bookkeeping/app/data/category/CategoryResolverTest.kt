package com.bookkeeping.app.data.category

import com.bookkeeping.app.parser.FakeTransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryResolverTest {

    @Test
    fun testIncomeRules() {
        assertEquals("退款", CategoryResolver.infer("INCOME", "美团外卖", "订单退款"))
        assertEquals("工资", CategoryResolver.infer("INCOME", "某某公司", "10月工资"))
        assertEquals("红包收入", CategoryResolver.infer("INCOME", "张三", "微信红包"))
        assertEquals("金融理财", CategoryResolver.infer("INCOME", "余额宝", "收益发放"))
        assertEquals("其他收入", CategoryResolver.infer("INCOME", "李四", "转账"))
    }

    @Test
    fun testExpenseNeverGetsIncomeCategory() {
        val expenseNames = CategoryResolver.defaultNames("EXPENSE")
        listOf("余额宝 理财申购", "基金定投", "美团外卖", "转账给张三", "随便什么").forEach {
            val name = CategoryResolver.infer("EXPENSE", it)
            assertNotEquals("金融理财", name)
            assertTrue("$it -> $name 应为支出分类", name in expenseNames)
        }
    }

    @Test
    fun testIncomeNeverGetsExpenseCategory() {
        val incomeNames = CategoryResolver.defaultNames("INCOME")
        listOf("美团外卖退款", "滴滴出行退款", "京东", "随便什么").forEach {
            val name = CategoryResolver.infer("INCOME", it)
            assertTrue("$it -> $name 应为收入分类", name in incomeNames)
        }
    }

    @Test
    fun testNormalize() {
        assertEquals("其他支出", CategoryResolver.normalize("EXPENSE", "日常消费", CategoryResolver.defaultNames("EXPENSE")))
        assertEquals("其他收入", CategoryResolver.normalize("INCOME", "餐饮美食", CategoryResolver.defaultNames("INCOME")))
        assertEquals("餐饮美食", CategoryResolver.normalize("EXPENSE", "餐饮美食", emptyList()))
        assertEquals("其他支出", CategoryResolver.normalize("EXPENSE", "餐饮美食", listOf("其他支出")))
    }

    @Test
    fun testResolveUsesCategoryTable() = runBlocking {
        val dao = FakeTransactionDao(categoryNames = mapOf("EXPENSE" to setOf("其他支出", "外卖")))
        assertEquals("其他支出", CategoryResolver.resolve(dao, "EXPENSE", "美团外卖"))
        val dao2 = FakeTransactionDao(categoryNames = mapOf("EXPENSE" to setOf("其他支出", "餐饮美食")))
        assertEquals("餐饮美食", CategoryResolver.resolve(dao2, "EXPENSE", "美团外卖"))
    }
}
