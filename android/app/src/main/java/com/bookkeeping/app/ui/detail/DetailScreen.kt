package com.bookkeeping.app.ui.detail

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bookkeeping.app.data.attachment.AttachmentStore
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.data.prefs.AppPrefs
import com.bookkeeping.app.service.ListenerWatchdog
import com.bookkeeping.app.ui.common.BudgetDialog
import com.bookkeeping.app.ui.common.dayStart
import com.bookkeeping.app.ui.common.formatCents
import com.bookkeeping.app.ui.common.formatDayHeader
import com.bookkeeping.app.ui.common.monthRange
import com.bookkeeping.app.ui.common.rememberBillImporter
import com.bookkeeping.app.ui.components.MiButton
import com.bookkeeping.app.ui.components.MiButtonStyle
import com.bookkeeping.app.ui.components.MiCard
import com.bookkeeping.app.ui.components.MiDialog
import com.bookkeeping.app.ui.components.MiDivider
import com.bookkeeping.app.ui.components.MiIconButton
import com.bookkeeping.app.ui.components.MiMonthPickerSheet
import com.bookkeeping.app.ui.components.miClickable
import com.bookkeeping.app.ui.record.TransactionRow
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi
import kotlinx.coroutines.launch
import java.util.Calendar

private const val VIEW_LIST = 0
private const val VIEW_CALENDAR = 1

@Composable
fun DetailScreen(
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenRecord: (TransactionRecord) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).transactionDao() }
    val prefs = remember { AppPrefs(context) }
    val importer = rememberBillImporter()

    val now = remember { Calendar.getInstance() }
    val nowYear = now.get(Calendar.YEAR)
    val nowMonth = now.get(Calendar.MONTH)
    var year by remember { mutableIntStateOf(nowYear) }
    var month by remember { mutableIntStateOf(nowMonth) }
    var viewMode by remember { mutableIntStateOf(prefs.detailViewMode) }
    var selectedDay by remember { mutableLongStateOf(dayStart(System.currentTimeMillis())) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var budgetCents by remember { mutableLongStateOf(prefs.monthlyBudgetCents) }
    var listenerOn by remember { mutableStateOf(ListenerWatchdog.isNotificationListenerEnabled(context)) }

    // 多选状态
    var selecting by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var confirmDelete by remember { mutableStateOf(false) }
    fun exitSelection() { selecting = false; selectedIds.clear() }
    fun toggle(id: Long) { if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id) }
    LaunchedEffect(selecting) { onSelectionModeChange(selecting) }
    BackHandler(enabled = selecting) { exitSelection() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) listenerOn = ListenerWatchdog.isNotificationListenerEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onSelectionModeChange(false)
        }
    }

    // 日历月份翻页与年月状态双向同步
    val pager = rememberPagerState(initialPage = monthPage(year, month, nowYear, nowMonth)) { CAL_MONTHS_BACK + CAL_MONTHS_FORWARD + 1 }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val (y, m) = pageMonth(page, nowYear, nowMonth)
            if (y != year || m != month) { year = y; month = m }
        }
    }
    LaunchedEffect(year, month) {
        val target = monthPage(year, month, nowYear, nowMonth)
        if (pager.settledPage != target) pager.animateScrollToPage(target)
        val (s, e) = monthRange(year, month)
        if (selectedDay !in s..e) {
            selectedDay = if (year == nowYear && month == nowMonth) dayStart(System.currentTimeMillis()) else s
        }
    }

    val allRecords by dao.getAllFlow().collectAsState(initial = emptyList())
    val dayTotals = remember(allRecords) {
        allRecords.groupBy { dayStart(it.timestamp) }.mapValues { (_, l) ->
            DayTotal(
                l.filter { it.type == "EXPENSE" }.sumOf { it.amountCents },
                l.filter { it.type == "INCOME" }.sumOf { it.amountCents },
                l.size
            )
        }
    }
    val (start, end) = remember(year, month) { monthRange(year, month) }
    val monthRecords = remember(allRecords, start, end) { allRecords.filter { it.timestamp in start..end } }
    val expense = remember(monthRecords) { monthRecords.filter { it.type == "EXPENSE" }.sumOf { it.amountCents } }
    val income = remember(monthRecords) { monthRecords.filter { it.type == "INCOME" }.sumOf { it.amountCents } }
    val days = remember(monthRecords) { monthRecords.groupBy { dayStart(it.timestamp) }.toSortedMap(compareByDescending { it }) }
    val dayRecords = remember(allRecords, selectedDay) { allRecords.filter { dayStart(it.timestamp) == selectedDay } }
    val visibleRecords = if (viewMode == VIEW_CALENDAR) dayRecords else monthRecords
    val isCurrentMonth = year == nowYear && month == nowMonth

    val onRowClick: (TransactionRecord) -> Unit = { r -> if (selecting) toggle(r.id) else onOpenRecord(r) }
    val onRowLongClick: (TransactionRecord) -> Unit = { r ->
        if (!selecting) { selecting = true; selectedIds.clear() }
        toggle(r.id)
    }

    val c = Mi.colors
    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item(key = "top") {
                AnimatedContent(
                    targetState = selecting,
                    transitionSpec = { (fadeIn() + slideInVertically { -it / 3 }) togetherWith (fadeOut() + slideOutVertically { it / 3 }) },
                    label = "topBar"
                ) { isSelecting ->
                    if (isSelecting) {
                        SelectionTopBar(
                            count = selectedIds.size,
                            allSelected = visibleRecords.isNotEmpty() && selectedIds.containsAll(visibleRecords.map { it.id }),
                            onCancel = ::exitSelection,
                            onToggleAll = { all ->
                                selectedIds.clear()
                                if (all) selectedIds.addAll(visibleRecords.map { it.id })
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.miClickable { showMonthPicker = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${month + 1}月", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("$year", fontSize = 16.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "切换月份", tint = c.textSecondary, modifier = Modifier.padding(top = 8.dp).size(20.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            ViewToggle(viewMode) { viewMode = it; prefs.detailViewMode = it }
                            Spacer(Modifier.width(4.dp))
                            MiIconButton(Icons.Default.Checklist, onClick = { selecting = true }, contentDescription = "批量管理")
                            MiIconButton(Icons.Default.Search, onClick = onSearch, contentDescription = "搜索")
                        }
                    }
                }
            }

            item(key = "hero") {
                HeroCard(
                    expense = expense,
                    income = income,
                    budget = budgetCents,
                    isCurrentMonth = isCurrentMonth,
                    listenerOn = listenerOn,
                    onBudgetClick = { showBudget = true }
                )
            }

            if (viewMode == VIEW_CALENDAR) {
                item(key = "calendar") {
                    Spacer(Modifier.height(12.dp))
                    MonthCalendarCard(
                        pager = pager,
                        nowYear = nowYear,
                        nowMonth = nowMonth,
                        dayTotals = dayTotals,
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it },
                        onPrev = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                        onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        onToday = {
                            year = nowYear; month = nowMonth
                            selectedDay = dayStart(System.currentTimeMillis())
                        }
                    )
                }
                item(key = "dayList") {
                    if (dayRecords.isEmpty()) {
                        val (title, sub) = formatDayHeader(selectedDay)
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$title $sub", fontSize = 14.sp, color = c.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text("这一天没有账单", fontSize = 15.sp, color = c.textTertiary)
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        DayCard(selectedDay, dayRecords, selecting, selectedIds, onRowClick, onRowLongClick)
                    }
                }
            } else {
                item(key = "actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAction(Icons.Default.EditNote, "记一笔", Color(0xFF3482FF), Modifier.weight(1f), onAdd)
                        QuickAction(Icons.Default.ContentPaste, "粘贴账单", Color(0xFF16B47A), Modifier.weight(1f), importer.openPaste)
                        QuickAction(Icons.Default.FileOpen, "导入账单", Color(0xFFFF8A3D), Modifier.weight(1f), importer.pickFile)
                        QuickAction(Icons.Default.Category, "分类", Color(0xFFA66BFF), Modifier.weight(1f), onManageCategories)
                    }
                }

                if (days.isEmpty()) {
                    item(key = "empty") { EmptyState(onAdd) }
                }

                days.forEach { (day, list) ->
                    item(key = "day_$day") {
                        DayCard(day, list, selecting, selectedIds, onRowClick, onRowLongClick)
                    }
                }
            }
        }

        // 批量操作底栏
        AnimatedVisibility(
            visible = selecting,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                MiButton(
                    text = if (selectedIds.isEmpty()) "请选择账单" else "删除 ${selectedIds.size} 笔",
                    onClick = { confirmDelete = true },
                    enabled = selectedIds.isNotEmpty(),
                    style = MiButtonStyle.DangerFilled,
                    icon = Icons.Default.DeleteOutline,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showMonthPicker) {
        MiMonthPickerSheet(year, month, onDismiss = { showMonthPicker = false }) { y, m -> year = y; month = m }
    }
    if (showBudget) {
        BudgetDialog(onDismiss = { showBudget = false }, onSaved = { budgetCents = it })
    }
    if (confirmDelete) {
        val ids = selectedIds.toList()
        val targets = allRecords.filter { it.id in ids }
        MiDialog(
            onDismissRequest = { confirmDelete = false },
            title = "删除 ${ids.size} 笔账单？",
            message = buildString {
                val out = targets.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
                val inc = targets.filter { it.type == "INCOME" }.sumOf { it.amountCents }
                if (out > 0) append("支出 ${formatCents(out)}")
                if (inc > 0) { if (isNotEmpty()) append("，"); append("收入 ${formatCents(inc)}") }
                if (isNotEmpty()) append("。")
                append("删除后不可恢复，附带的图片也会一并清除。")
            },
            confirmText = "删除",
            confirmStyle = MiButtonStyle.Danger,
            onConfirm = {
                scope.launch {
                    dao.markDeletedIds(ids)
                    AttachmentStore.delete(context, targets.flatMap { it.imageList })
                    Toast.makeText(context, "已删除 ${ids.size} 笔账单", Toast.LENGTH_SHORT).show()
                    exitSelection()
                }
            }
        )
    }
}

/** 列表 / 日历 视图切换 */
@Composable
private fun ViewToggle(mode: Int, onChange: (Int) -> Unit) {
    val c = Mi.colors
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.fill).padding(3.dp)
    ) {
        listOf(Icons.Default.ViewAgenda to "列表", Icons.Default.CalendarMonth to "日历").forEachIndexed { i, (icon, label) ->
            val selected = mode == i
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) (if (c.isDark) Color(0xFF48484C) else Color.White) else Color.Transparent)
                    .miClickable { onChange(i) },
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = if (selected) c.primary else c.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SelectionTopBar(count: Int, allSelected: Boolean, onCancel: () -> Unit, onToggleAll: (Boolean) -> Unit) {
    val c = Mi.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "取消",
            fontSize = 16.sp,
            color = c.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.miClickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Text(
            if (count == 0) "选择账单" else "已选 $count 笔",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (allSelected) "全不选" else "全选",
            fontSize = 16.sp,
            color = c.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.miClickable { onToggleAll(!allSelected) }.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun HeroCard(
    expense: Long,
    income: Long,
    budget: Long,
    isCurrentMonth: Boolean,
    listenerOn: Boolean,
    onBudgetClick: () -> Unit
) {
    val c = Mi.colors
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本月支出", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (listenerOn) Color(0xFF7CF5B5) else Color(0xFFFFB4A8)))
                Spacer(Modifier.width(5.dp))
                Text(if (listenerOn) "自动记账中" else "自动记账未开启", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("¥", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
            Spacer(Modifier.width(2.dp))
            Text(formatCents(expense), style = AmountStyle, fontSize = 38.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            HeroStat("收入", formatCents(income), Modifier.weight(1f))
            HeroStat("结余", formatCents(income - expense), Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.22f)))
        Spacer(Modifier.height(14.dp))

        if (budget > 0) {
            val remain = budget - expense
            val ratio = (expense.toFloat() / budget).coerceIn(0f, 1f)
            val animated by animateFloatAsState(ratio, tween(700), label = "budget")
            Row(modifier = Modifier.miClickable(pressedScale = 0.98f, onClick = onBudgetClick), verticalAlignment = Alignment.CenterVertically) {
                Text("预算 ${formatCents(budget)}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
                Text(
                    if (remain >= 0) "剩余 ${formatCents(remain)}" else "超支 ${formatCents(-remain)}",
                    fontSize = 13.sp,
                    color = if (remain >= 0) Color.White else Color(0xFFFFD6D1),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))) {
                Box(
                    Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(50))
                        .background(if (remain >= 0) Color.White else Color(0xFFFFB4A8))
                )
            }
            if (isCurrentMonth && remain > 0) {
                val cal = Calendar.getInstance()
                val leftDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1
                Spacer(Modifier.height(8.dp))
                Text("本月还剩 $leftDays 天，日均可用 ${formatCents(remain / leftDays)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
        } else {
            Row(modifier = Modifier.miClickable(pressedScale = 0.98f, onClick = onBudgetClick), verticalAlignment = Alignment.CenterVertically) {
                Text("设置月度预算，掌控每一笔开销", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text("去设置", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = AmountStyle, fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Mi.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .miClickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DayCard(
    day: Long,
    list: List<TransactionRecord>,
    selecting: Boolean,
    selectedIds: List<Long>,
    onClick: (TransactionRecord) -> Unit,
    onLongClick: (TransactionRecord) -> Unit
) {
    val c = Mi.colors
    val (title, sub) = formatDayHeader(day)
    val dayExpense = list.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
    val dayIncome = list.filter { it.type == "INCOME" }.sumOf { it.amountCents }
    MiCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Spacer(Modifier.width(6.dp))
            Text(sub, fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                buildString {
                    if (dayExpense > 0) append("支 ${formatCents(dayExpense)}")
                    if (dayIncome > 0) { if (isNotEmpty()) append("  "); append("收 ${formatCents(dayIncome)}") }
                },
                style = AmountStyle,
                fontSize = 12.sp,
                color = c.textSecondary
            )
        }
        list.forEachIndexed { i, r ->
            TransactionRow(
                r,
                onClick = { onClick(r) },
                selectionMode = selecting,
                selected = r.id in selectedIds,
                onLongClick = { onLongClick(r) }
            )
            if (i != list.lastIndex) MiDivider(startIndent = 68.dp)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val c = Mi.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = c.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("这个月还没有账单", fontSize = 16.sp, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "微信、支付宝等付款后会自动入账\n也可以点下方 ＋ 手动记一笔",
            fontSize = 13.sp,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "记一笔",
            fontSize = 15.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(c.primary)
                .miClickable(onClick = onAdd)
                .padding(horizontal = 28.dp, vertical = 11.dp)
        )
    }
}
