package com.bookkeeping.app.data.prefs

import android.content.Context

/** 轻量本地偏好：月度预算等 */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("bookkeeping_prefs", Context.MODE_PRIVATE)

    var monthlyBudgetCents: Long
        get() = sp.getLong(KEY_BUDGET, 0L)
        set(value) = sp.edit().putLong(KEY_BUDGET, value).apply()

    var lastChannel: String
        get() = sp.getString(KEY_LAST_CHANNEL, "微信支付") ?: "微信支付"
        set(value) = sp.edit().putString(KEY_LAST_CHANNEL, value).apply()

    /** 明细页视图：0 列表，1 日历 */
    var detailViewMode: Int
        get() = sp.getInt(KEY_DETAIL_VIEW, 0)
        set(value) = sp.edit().putInt(KEY_DETAIL_VIEW, value).apply()

    companion object {
        private const val KEY_DETAIL_VIEW = "detail_view_mode"
        private const val KEY_BUDGET = "monthly_budget_cents"
        private const val KEY_LAST_CHANNEL = "last_channel"
    }
}
