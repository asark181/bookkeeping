package com.bookkeeping.app.ui.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 分 → "1,234.50" */
fun formatCents(cents: Long, grouping: Boolean = true): String {
    val abs = kotlin.math.abs(cents)
    val yuan = abs / 100
    val fen = abs % 100
    val yuanStr = if (grouping) String.format(Locale.US, "%,d", yuan) else yuan.toString()
    return (if (cents < 0) "-" else "") + yuanStr + "." + String.format(Locale.US, "%02d", fen)
}

/** 大额紧凑显示：12.3万 */
fun formatCentsCompact(cents: Long): String {
    val yuan = cents / 100.0
    return if (kotlin.math.abs(yuan) >= 100_000) String.format(Locale.US, "%.1f万", yuan / 10_000) else formatCents(cents)
}

private val WEEK = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

fun dayStart(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** 日期分组标题：今天 / 昨天 / 10月25日 周六 */
fun formatDayHeader(dayStartMillis: Long): Pair<String, String> {
    val today = dayStart(System.currentTimeMillis())
    val cal = Calendar.getInstance().apply { timeInMillis = dayStartMillis }
    val md = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(dayStartMillis))
    val week = WEEK[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return when (dayStartMillis) {
        today -> "今天" to "$md $week"
        today - 86_400_000L -> "昨天" to "$md $week"
        else -> md to week
    }
}

fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))

fun formatFullTime(timestamp: Long): String = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timestamp))

fun formatShortDate(timestamp: Long): String {
    val today = dayStart(System.currentTimeMillis())
    val day = dayStart(timestamp)
    return when (day) {
        today -> "今天"
        today - 86_400_000L -> "昨天"
        else -> {
            val sameYear = Calendar.getInstance().get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
            SimpleDateFormat(if (sameYear) "M月d日" else "yyyy/M/d", Locale.CHINA).format(Date(timestamp))
        }
    }
}

/** 月份 [start, end] 毫秒区间 */
fun monthRange(year: Int, month: Int): Pair<Long, Long> {
    val c = Calendar.getInstance().apply {
        clear(); set(year, month, 1)
    }
    val start = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return start to c.timeInMillis - 1
}
