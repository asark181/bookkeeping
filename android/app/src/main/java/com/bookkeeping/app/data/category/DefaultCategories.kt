package com.bookkeeping.app.data.category

import com.bookkeeping.app.data.db.entity.CategoryEntity

/**
 * 默认分类。名称与自动记账/账单导入推断出的分类保持一致，保证开箱即有图标与颜色。
 */
object DefaultCategories {

    const val FALLBACK_EXPENSE = "其他支出"
    const val FALLBACK_INCOME = "其他收入"

    fun fallbackFor(type: String): String = if (type == "INCOME") FALLBACK_INCOME else FALLBACK_EXPENSE

    private val expense = listOf(
        Triple("餐饮美食", "restaurant", 0xFFFF8A3DL),
        Triple("交通出行", "car", 0xFF3482FFL),
        Triple("网络购物", "shopping_bag", 0xFFA66BFFL),
        Triple("日常支出", "cart", 0xFF22B5A4L),
        Triple("日常缴费", "bolt", 0xFFF5B400L),
        Triple("休闲娱乐", "movie", 0xFF00B8D9L),
        Triple("医疗健康", "medical", 0xFF2EC27EL),
        Triple("转账红包", "red_packet", 0xFFFF5A5FL),
        Triple("人情往来", "celebration", 0xFFFF6FA8L),
        Triple("住房物业", "home", 0xFF8D6E63L),
        Triple("服饰美容", "checkroom", 0xFFE86FD6L),
        Triple("学习教育", "school", 0xFF5B6CFFL),
        Triple("数码电器", "devices", 0xFF607D8BL),
        Triple("旅行度假", "flight", 0xFF26A9E0L),
        Triple("宠物", "pets", 0xFFFFA94DL),
        Triple("刷卡消费", "credit_card", 0xFF4C6EF5L),
        Triple(FALLBACK_EXPENSE, "category", 0xFF8E99A8L)
    )

    private val income = listOf(
        Triple("工资", "payments", 0xFF22B573L),
        Triple("奖金", "trophy", 0xFFF5B400L),
        Triple("金融理财", "trending_up", 0xFFFF5A5FL),
        Triple("兼职外快", "work", 0xFF3482FFL),
        Triple("红包收入", "red_packet", 0xFFFF6FA8L),
        Triple("退款", "undo", 0xFF00B8D9L),
        Triple(FALLBACK_INCOME, "savings", 0xFF8E99A8L)
    )

    fun all(): List<CategoryEntity> {
        fun build(type: String, list: List<Triple<String, String, Long>>) = list.mapIndexed { i, (name, icon, color) ->
            CategoryEntity(
                name = name,
                type = type,
                iconKey = icon,
                colorArgb = color,
                sortOrder = i,
                isProtected = name == FALLBACK_EXPENSE || name == FALLBACK_INCOME
            )
        }
        return build("EXPENSE", expense) + build("INCOME", income)
    }
}
