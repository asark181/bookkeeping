package com.bookkeeping.app.data.prefs

import android.content.Context

/** 轻量本地偏好：月度预算等 */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("bookkeeping_prefs", Context.MODE_PRIVATE)

    /** 用户修改预算时同步刷新修改时间，云同步按修改时间合并 */
    var monthlyBudgetCents: Long
        get() = sp.getLong(KEY_BUDGET, 0L)
        set(value) = sp.edit().putLong(KEY_BUDGET, value).putLong(KEY_BUDGET_UPDATED, System.currentTimeMillis()).apply()

    val budgetUpdatedAt: Long get() = sp.getLong(KEY_BUDGET_UPDATED, 0L)

    /** 云同步写入预算：保留云端的修改时间 */
    fun applySyncedBudget(cents: Long, updatedAt: Long) {
        sp.edit().putLong(KEY_BUDGET, cents).putLong(KEY_BUDGET_UPDATED, updatedAt).apply()
    }

    /** 分类表最近一次被用户修改的时间（新建、编辑、删除） */
    var categoriesUpdatedAt: Long
        get() = sp.getLong(KEY_CATEGORIES_UPDATED, 0L)
        set(value) = sp.edit().putLong(KEY_CATEGORIES_UPDATED, value).apply()

    var lastChannel: String
        get() = sp.getString(KEY_LAST_CHANNEL, "微信支付") ?: "微信支付"
        set(value) = sp.edit().putString(KEY_LAST_CHANNEL, value).apply()

    /** 明细页视图：0 列表，1 日历 */
    var detailViewMode: Int
        get() = sp.getInt(KEY_DETAIL_VIEW, 0)
        set(value) = sp.edit().putInt(KEY_DETAIL_VIEW, value).apply()

    companion object {
        private const val KEY_DETAIL_VIEW = "detail_view_mode"
        private const val KEY_BUDGET_UPDATED = "budget_updated_at"
        private const val KEY_CATEGORIES_UPDATED = "categories_updated_at"
        private const val KEY_BUDGET = "monthly_budget_cents"
        private const val KEY_LAST_CHANNEL = "last_channel"
    }
}
