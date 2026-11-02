package com.bookkeeping.app.data.category

import com.bookkeeping.app.data.db.dao.TransactionDao

/**
 * 统一的分类解析：通知监听、无障碍识别、账单导入三处共用。
 *
 * 1. [infer] 按收支方向分别推断分类名称：支出只会得到支出分类名，收入只会得到收入分类名；
 * 2. [normalize] / [resolve] 把推断结果与 categories 表比对，表中不存在同 type 的分类时落到
 *    [DefaultCategories.fallbackFor]，保证交易表里的分类名都能关联到真实分类。
 */
object CategoryResolver {

    /** 支出默认分类（未命中任何关键词时） */
    const val DEFAULT_EXPENSE = "日常支出"

    /** 按收支方向推断分类名称（纯函数，不访问数据库） */
    fun infer(type: String, vararg texts: String?): String {
        val combined = texts.filterNotNull().joinToString(" ")
        return when (type) {
            "INCOME" -> inferIncome(combined)
            "EXPENSE" -> inferExpense(combined)
            else -> DefaultCategories.fallbackFor(type)
        }
    }

    fun inferIncome(text: String): String = when {
        containsAny(text, "退款", "退还", "退回", "返还", "已全额退款", "部分退款") -> "退款"
        containsAny(text, "工资", "薪资", "薪金", "代发", "劳务报酬") -> "工资"
        containsAny(text, "奖金", "年终奖", "绩效", "奖励金") -> "奖金"
        containsAny(text, "红包") -> "红包收入"
        containsAny(text, "理财", "基金", "余额宝", "零钱通", "收益", "利息", "分红", "定投", "证券", "股票") -> "金融理财"
        else -> DefaultCategories.FALLBACK_INCOME
    }

    fun inferExpense(text: String): String = when {
        containsAny(text, "外卖", "美团", "饿了么", "餐饮", "咖啡", "麦当劳", "肯德基", "星巴克", "便利店", "全家", "罗森", "超市", "菜场", "食品", "蛋糕", "茶", "酒", "饭", "面", "餐", "小吃", "老乡鸡", "沙县", "蜜雪冰城", "霸王茶姬") -> "餐饮美食"
        containsAny(text, "出行", "滴滴", "打车", "公交", "地铁", "铁路", "12306", "高德", "加油", "停车", "机票", "航空", "车", "青桔", "骑行", "巴士", "同程") -> "交通出行"
        containsAny(text, "京东", "淘宝", "天猫", "拼多多", "抖音", "快手", "唯品会", "网购", "商城", "店铺", "购物") -> "网络购物"
        containsAny(text, "转账", "红包", "请收款") -> "转账红包"
        containsAny(text, "人情", "份子", "礼金", "随礼") -> "人情往来"
        containsAny(text, "水费", "电费", "燃气", "话费", "宽带", "电信", "移动", "联通", "充值", "共享充电") -> "日常缴费"
        containsAny(text, "物业", "房租", "租房") -> "住房物业"
        containsAny(text, "医院", "药", "诊所", "体检", "健康") -> "医疗健康"
        containsAny(text, "银联", "云闪付") -> "刷卡消费"
        // 理财申购等不是支出分类，交给默认支出分类，避免支出记录拿到收入分类「金融理财」
        else -> DEFAULT_EXPENSE
    }

    /**
     * 名称在 existingNames（同 type 的分类名称集合）中存在则原样返回，否则返回兜底分类。
     * existingNames 为空（分类表尚未初始化）时按默认分类表比对。
     */
    fun normalize(type: String, name: String?, existingNames: Collection<String>): String {
        val names = existingNames.ifEmpty { defaultNames(type) }
        return if (!name.isNullOrBlank() && name in names) name else DefaultCategories.fallbackFor(type)
    }

    fun defaultNames(type: String): Set<String> =
        DefaultCategories.all().filter { it.type == type }.map { it.name }.toSet()

    /** 读取 categories 表中某一 type 的分类名称 */
    suspend fun loadNames(dao: TransactionDao, type: String): Set<String> =
        try {
            dao.getCategoryNamesByType(type).toSet()
        } catch (_: Exception) {
            emptySet()
        }

    /** 推断并按 categories 表校正 */
    suspend fun resolve(dao: TransactionDao, type: String, vararg texts: String?): String =
        normalize(type, infer(type, *texts), loadNames(dao, type))

    private fun containsAny(text: String, vararg keywords: String): Boolean =
        keywords.any { text.contains(it, ignoreCase = true) }
}
