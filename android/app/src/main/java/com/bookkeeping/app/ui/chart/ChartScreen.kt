package com.bookkeeping.app.ui.chart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.ui.category.categoryVisual
import com.bookkeeping.app.ui.common.formatCents
import com.bookkeeping.app.ui.components.MiCard
import com.bookkeeping.app.ui.components.MiDivider
import com.bookkeeping.app.ui.components.MiIconButton
import com.bookkeeping.app.ui.components.MiLargeTitle
import com.bookkeeping.app.ui.components.MiSegmented
import com.bookkeeping.app.ui.components.miRowClickable
import com.bookkeeping.app.ui.record.TransactionRow
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi
import java.util.Calendar
import java.util.Locale

enum class ChartPeriod(val label: String) { WEEK("周"), MONTH("月"), YEAR("年") }

private data class PeriodWindow(val start: Long, val end: Long, val title: String, val buckets: List<Pair<Long, Long>>, val labels: List<String>, val tooltips: List<String>)

private fun buildWindow(period: ChartPeriod, offset: Int): PeriodWindow {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val buckets = mutableListOf<Pair<Long, Long>>()
    val labels = mutableListOf<String>()
    val tips = mutableListOf<String>()
    return when (period) {
        ChartPeriod.WEEK -> {
            cal.firstDayOfWeek = Calendar.MONDAY
            val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            cal.add(Calendar.DAY_OF_MONTH, -dow + offset * 7)
            val start = cal.timeInMillis
            val names = listOf("一", "二", "三", "四", "五", "六", "日")
            for (i in 0 until 7) {
                val s = cal.timeInMillis
                tips += "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 周${names[i]}"
                cal.add(Calendar.DAY_OF_MONTH, 1)
                buckets += s to cal.timeInMillis - 1
                labels += names[i]
            }
            val endCal = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis - 1 }
            val startCal = Calendar.getInstance().apply { timeInMillis = start }
            val title = when (offset) {
                0 -> "本周"
                -1 -> "上周"
                else -> "${startCal.get(Calendar.MONTH) + 1}/${startCal.get(Calendar.DAY_OF_MONTH)} - ${endCal.get(Calendar.MONTH) + 1}/${endCal.get(Calendar.DAY_OF_MONTH)}"
            }
            PeriodWindow(start, cal.timeInMillis - 1, title, buckets, labels, tips)
        }
        ChartPeriod.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, offset)
            val start = cal.timeInMillis
            val title = "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月"
            val m = cal.get(Calendar.MONTH) + 1
            val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (d in 1..days) {
                val s = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                buckets += s to cal.timeInMillis - 1
                labels += "$d"
                tips += "${m}月${d}日"
            }
            PeriodWindow(start, cal.timeInMillis - 1, title, buckets, labels, tips)
        }
        ChartPeriod.YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.add(Calendar.YEAR, offset)
            val start = cal.timeInMillis
            val y = cal.get(Calendar.YEAR)
            for (m in 1..12) {
                val s = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                buckets += s to cal.timeInMillis - 1
                labels += "$m"
                tips += "${y}年${m}月"
            }
            PeriodWindow(start, cal.timeInMillis - 1, "${y}年", buckets, labels, tips)
        }
    }
}

@Composable
fun ChartScreen(onOpenRecord: (TransactionRecord) -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).transactionDao() }
    var period by remember { mutableStateOf(ChartPeriod.MONTH) }
    var offset by remember { mutableIntStateOf(0) }
    var incomeMode by remember { mutableStateOf(false) }
    var selectedBar by remember { mutableStateOf<Int?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val window = remember(period, offset) { buildWindow(period, offset) }
    val records by remember(window) { dao.getByTimeRangeFlow(window.start, window.end) }.collectAsState(initial = emptyList())
    val type = if (incomeMode) "INCOME" else "EXPENSE"
    val list = remember(records, type) { records.filter { it.type == type } }
    val total = remember(list) { list.sumOf { it.amountCents } }
    val points = remember(list, window) {
        window.buckets.mapIndexed { i, (s, e) ->
            BarPoint(window.labels[i], window.tooltips[i], list.filter { it.timestamp in s..e }.sumOf { it.amountCents })
        }
    }
    val slicesBase = remember(list, total) {
        if (total == 0L) emptyList() else list.groupBy { it.category }.map { (cat, items) ->
            val sum = items.sumOf { it.amountCents }
            Triple(cat, sum, items.size)
        }.sortedByDescending { it.second }
    }
    val slices = slicesBase.map { (cat, sum, count) ->
        CategorySlice(cat, sum, categoryVisual(cat, type).color, sum.toFloat() / total, count)
    }

    val c = Mi.colors
    val accent = if (incomeMode) c.income else c.primary
    val divisor = when (period) {
        ChartPeriod.WEEK -> 7
        ChartPeriod.MONTH -> {
            val isCurrent = offset == 0
            if (isCurrent) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) else window.buckets.size
        }
        ChartPeriod.YEAR -> 12
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            MiLargeTitle("统计") {
                MiSegmented(
                    options = listOf("支出", "收入"),
                    selectedIndex = if (incomeMode) 1 else 0,
                    onSelect = { incomeMode = it == 1; selectedBar = null; selectedCategory = null },
                    modifier = Modifier.width(120.dp),
                    height = 34.dp
                )
            }
        }
        item {
            MiSegmented(
                options = ChartPeriod.entries.map { it.label },
                selectedIndex = period.ordinal,
                onSelect = { period = ChartPeriod.entries[it]; offset = 0; selectedBar = null; selectedCategory = null },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = { offset--; selectedBar = null; selectedCategory = null })
                Text(window.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                MiIconButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = { if (offset < 0) { offset++; selectedBar = null; selectedCategory = null } },
                    tint = if (offset < 0) c.textPrimary else c.textTertiary
                )
            }
        }

        // 总览 + 趋势
        item {
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(if (incomeMode) "总收入" else "总支出", fontSize = 13.sp, color = c.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(formatCents(total), style = AmountStyle, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        StatCell("笔数", "${list.size}", Modifier.weight(1f))
                        StatCell(if (period == ChartPeriod.YEAR) "月均" else "日均", formatCents(if (divisor > 0) total / divisor else 0), Modifier.weight(1f))
                        StatCell("单笔最高", formatCents(list.maxOfOrNull { it.amountCents } ?: 0), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    val sel = selectedBar?.let { points.getOrNull(it) }
                    Text(
                        if (sel != null) "${sel.tooltip}  ${formatCents(sel.valueCents)}" else "趋势 · 点击柱子查看明细",
                        fontSize = 13.sp,
                        color = if (sel != null) accent else c.textSecondary,
                        fontWeight = if (sel != null) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(10.dp))
                    BarChart(
                        points = points,
                        selectedIndex = selectedBar,
                        onSelect = { selectedBar = it },
                        color = accent,
                        labelEvery = if (period == ChartPeriod.MONTH) 5 else 1
                    )
                }
            }
        }

        // 分类构成
        item {
            Spacer(Modifier.height(12.dp))
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "分类构成",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    if (slices.isEmpty()) {
                        Text("这段时间没有${if (incomeMode) "收入" else "支出"}记录", fontSize = 14.sp, color = c.textTertiary, modifier = Modifier.padding(vertical = 40.dp))
                    } else {
                        DonutChart(
                            slices = slices,
                            totalCents = total,
                            centerLabel = if (incomeMode) "总收入" else "总支出",
                            selected = selectedCategory
                        )
                        Spacer(Modifier.height(12.dp))
                        slices.forEachIndexed { i, s ->
                            RankRow(
                                rank = i + 1,
                                slice = s,
                                type = type,
                                expanded = selectedCategory == s.category,
                                onClick = { selectedCategory = if (selectedCategory == s.category) null else s.category }
                            )
                            AnimatedVisibility(visible = selectedCategory == s.category) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    list.filter { it.category == s.category }.take(30).forEach { r ->
                                        TransactionRow(r, onClick = { onOpenRecord(r) }, showDate = true)
                                    }
                                }
                            }
                            if (i != slices.lastIndex) MiDivider(startIndent = 72.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Mi.colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = AmountStyle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Mi.colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun RankRow(rank: Int, slice: CategorySlice, type: String, expanded: Boolean, onClick: () -> Unit) {
    val c = Mi.colors
    val v = categoryVisual(slice.category, type)
    val animated by animateFloatAsState(slice.percentage, tween(700), label = "rank")
    Row(
        modifier = Modifier.fillMaxWidth().miRowClickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(v.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(v.icon, contentDescription = null, tint = v.color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(slice.category, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.textPrimary)
                Spacer(Modifier.width(6.dp))
                Text(String.format(Locale.US, "%.1f%%", slice.percentage * 100), fontSize = 12.sp, color = c.textSecondary)
                Spacer(Modifier.weight(1f))
                Text(formatCents(slice.amountCents), style = AmountStyle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(c.fill)) {
                    Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(50)).background(v.color))
                }
                Spacer(Modifier.width(10.dp))
                Text("${slice.count}笔", fontSize = 11.sp, color = c.textTertiary)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
