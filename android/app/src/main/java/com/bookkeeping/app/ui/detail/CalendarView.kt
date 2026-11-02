package com.bookkeeping.app.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.ui.common.dayStart
import com.bookkeeping.app.ui.common.formatCents
import com.bookkeeping.app.ui.components.MiCard
import com.bookkeeping.app.ui.components.MiIconButton
import com.bookkeeping.app.ui.components.miClickable
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi
import java.util.Calendar
import java.util.Locale

/** 单日收支汇总 */
data class DayTotal(val expense: Long, val income: Long, val count: Int)

/** 日历可回看 10 年、前看 1 年 */
internal const val CAL_MONTHS_BACK = 120
internal const val CAL_MONTHS_FORWARD = 12

internal fun monthPage(year: Int, month: Int, nowYear: Int, nowMonth: Int): Int =
    (CAL_MONTHS_BACK + (year - nowYear) * 12 + (month - nowMonth)).coerceIn(0, CAL_MONTHS_BACK + CAL_MONTHS_FORWARD)

internal fun pageMonth(page: Int, nowYear: Int, nowMonth: Int): Pair<Int, Int> {
    val total = nowYear * 12 + nowMonth + (page - CAL_MONTHS_BACK)
    return total / 12 to total % 12
}

private val WEEK_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val ROWS = 6

/** 日历金额简写：1234.5 → 1234，12345 → 1.2万 */
private fun shortAmount(cents: Long): String {
    val yuan = cents / 100.0
    return when {
        yuan >= 10_000 -> String.format(Locale.US, "%.1f万", yuan / 10_000)
        yuan >= 100 -> String.format(Locale.US, "%.0f", yuan)
        else -> String.format(Locale.US, "%.1f", yuan).removeSuffix(".0")
    }
}

/**
 * 月历视图：左右滑动切月，格子底色深浅表示当日支出多少，点选某天查看当日账单。
 */
@Composable
fun MonthCalendarCard(
    pager: PagerState,
    nowYear: Int,
    nowMonth: Int,
    dayTotals: Map<Long, DayTotal>,
    selectedDay: Long,
    onSelectDay: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val c = Mi.colors
    val (curYear, curMonth) = pageMonth(pager.currentPage, nowYear, nowMonth)
    val today = remember { dayStart(System.currentTimeMillis()) }
    val selectedTotal = dayTotals[selectedDay]

    MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${curYear}年${curMonth + 1}月", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            if (curYear != nowYear || curMonth != nowMonth || selectedDay != today) {
                Text(
                    "回今天",
                    fontSize = 12.sp,
                    color = c.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.primarySoft)
                        .miClickable(onClick = onToday)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            MiIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = onPrev, size = 34.dp, iconSize = 22.dp, contentDescription = "上个月")
            MiIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = onNext, size = 34.dp, iconSize = 22.dp, contentDescription = "下个月")
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            WEEK_HEADERS.forEachIndexed { i, d ->
                Text(
                    d,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = if (i >= 5) c.primary.copy(alpha = 0.8f) else c.textTertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height((ROWS * 54).dp),
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top
        ) { page ->
            val (y, m) = pageMonth(page, nowYear, nowMonth)
            MonthGrid(y, m, dayTotals, today, selectedDay, onSelectDay)
        }

        // 选中日小结 + 图例
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDay }
            Text(
                "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (selectedTotal == null) "无账单"
                else buildString {
                    append("支 ").append(formatCents(selectedTotal.expense))
                    if (selectedTotal.income > 0) append("  收 ").append(formatCents(selectedTotal.income))
                    append("  ${selectedTotal.count}笔")
                },
                style = AmountStyle,
                fontSize = 12.sp,
                color = c.textSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text("少", fontSize = 10.sp, color = c.textTertiary)
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = 36.dp, height = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(c.primary.copy(alpha = 0.08f), c.primary.copy(alpha = 0.55f))))
            )
            Text("多", fontSize = 10.sp, color = c.textTertiary)
        }
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    dayTotals: Map<Long, DayTotal>,
    today: Long,
    selectedDay: Long,
    onSelectDay: (Long) -> Unit
) {
    val first = remember(year, month) { Calendar.getInstance().apply { clear(); set(year, month, 1) } }
    val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val dayStarts = remember(year, month) {
        (1..days).map { d -> (first.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }.timeInMillis }
    }
    val maxExpense = remember(dayTotals, year, month) {
        dayStarts.maxOfOrNull { dayTotals[it]?.expense ?: 0L }?.coerceAtLeast(1L) ?: 1L
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        for (r in 0 until ROWS) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 7) {
                    val idx = r * 7 + col - lead
                    Box(modifier = Modifier.weight(1f).fillMaxSize().padding(2.dp)) {
                        if (idx in 0 until days) {
                            val ds = dayStarts[idx]
                            DayCell(
                                day = idx + 1,
                                total = dayTotals[ds],
                                intensity = ((dayTotals[ds]?.expense ?: 0L).toFloat() / maxExpense),
                                isToday = ds == today,
                                isSelected = ds == selectedDay,
                                isFuture = ds > today,
                                onClick = { onSelectDay(ds) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    total: DayTotal?,
    intensity: Float,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit
) {
    val c = Mi.colors
    val hasExpense = (total?.expense ?: 0L) > 0
    val heat = if (hasExpense) 0.08f + 0.47f * intensity.coerceIn(0f, 1f) else 0f
    val bg by animateColorAsState(
        when {
            isSelected -> c.primary
            hasExpense -> c.primary.copy(alpha = heat)
            else -> Color.Transparent
        },
        label = "cellBg"
    )
    val strong = isSelected || heat > 0.42f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, c.primary, RoundedCornerShape(12.dp)) else Modifier)
            .miClickable(pressedScale = 0.9f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$day",
            fontSize = 15.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                strong -> Color.White
                isToday -> c.primary
                isFuture -> c.textTertiary
                else -> c.textPrimary
            },
            style = AmountStyle
        )
        when {
            hasExpense -> Text(
                "-" + shortAmount(total!!.expense),
                fontSize = 10.sp,
                color = if (strong) Color.White.copy(alpha = 0.92f) else c.textSecondary,
                style = AmountStyle,
                maxLines = 1
            )
            (total?.income ?: 0L) > 0 -> Text(
                "+" + shortAmount(total!!.income),
                fontSize = 10.sp,
                color = if (isSelected) Color.White else c.income,
                style = AmountStyle,
                maxLines = 1
            )
            else -> Spacer(Modifier.height(13.dp))
        }
    }
}
