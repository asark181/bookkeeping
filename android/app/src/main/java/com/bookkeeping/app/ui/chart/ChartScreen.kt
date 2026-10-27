package com.bookkeeping.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.ui.detail.formatCents
import com.bookkeeping.app.ui.detail.getNativeCategoryIconAndColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ChartPeriod(val label: String) {
    WEEK("周"),
    MONTH("月"),
    YEAR("年")
}

@Composable
fun ChartScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = db.transactionDao()

    var selectedPeriod by remember { mutableStateOf(ChartPeriod.MONTH) }

    // 时间范围计算
    val (startTime, endTime, periodLabel) = remember(selectedPeriod) {
        val cal = Calendar.getInstance()
        when (selectedPeriod) {
            ChartPeriod.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                val e = s + 7 * 86400000L - 1
                Triple(s, e, "本周")
            }
            ChartPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val e = cal.timeInMillis
                Triple(s, e, SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(cal.time))
            }
            ChartPeriod.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR))
                val e = cal.timeInMillis
                Triple(s, e, "${cal.get(Calendar.YEAR)}年")
            }
        }
    }

    val transactions by dao.getAllFlow().collectAsState(initial = emptyList())

    val periodExpenses = remember(transactions, startTime, endTime) {
        transactions.filter { it.type == "EXPENSE" && it.timestamp in startTime..endTime }
    }

    val totalExpenseCents = remember(periodExpenses) {
        periodExpenses.sumOf { it.amountCents }
    }

    // 分类汇总统计与扇区百分比
    val categoryRanking = remember(periodExpenses, totalExpenseCents) {
        if (totalExpenseCents == 0L) return@remember emptyList<CategorySlice>()
        val grouped = periodExpenses.groupBy { it.category }
        grouped.map { (cat, list) ->
            val sum = list.sumOf { it.amountCents }
            val (_, color) = getNativeCategoryIconAndColor(cat, "EXPENSE")
            CategorySlice(
                category = cat,
                amountCents = sum,
                color = color,
                percentage = sum.toFloat() / totalExpenseCents.toFloat()
            )
        }.sortedByDescending { it.amountCents }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部切换栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "统计报表 · $periodLabel",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 周/月/年胶囊切换
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(2.dp)
            ) {
                ChartPeriod.values().forEach { p ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedPeriod == p) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { selectedPeriod = p }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = p.label,
                            fontSize = 12.sp,
                            fontWeight = if (selectedPeriod == p) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedPeriod == p) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. 环形图卡片
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DonutChart(
                            slices = categoryRanking,
                            totalExpenseCents = totalExpenseCents,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (totalExpenseCents > 0) "支出大类构成 (${categoryRanking.size}个大类)" else "暂无支出数据",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // 2. 支出分类排行榜
            if (categoryRanking.isNotEmpty()) {
                item {
                    Text(
                        text = "支出排行榜",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(categoryRanking, key = { _, item -> item.category }) { index, slice ->
                    CategoryRankRow(
                        rank = index + 1,
                        slice = slice,
                        count = periodExpenses.count { it.category == slice.category }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryRankRow(
    rank: Int,
    slice: CategorySlice,
    count: Int
) {
    val (icon, color) = remember(slice.category) {
        getNativeCategoryIconAndColor(slice.category, "EXPENSE")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名序号
        Text(
            text = "$rank",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.width(22.dp)
        )

        // 分类图标
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = slice.category,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 分类名与进度条
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = slice.category,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format(Locale.getDefault(), "%.1f", slice.percentage * 100)}%",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { slice.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 金额
        Text(
            text = "¥${formatCents(slice.amountCents)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
