package com.bookkeeping.app.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.ui.category.categoryVisual
import com.bookkeeping.app.ui.common.formatCents
import com.bookkeeping.app.ui.common.formatShortDate
import com.bookkeeping.app.ui.common.formatTime
import com.bookkeeping.app.ui.components.MiCard
import com.bookkeeping.app.ui.components.MiDivider
import com.bookkeeping.app.ui.components.MiIconButton
import com.bookkeeping.app.ui.components.MiTextField
import com.bookkeeping.app.ui.components.miRowClickable
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos

/** 账单行：分类圆标 + 标题/备注 + 金额 */
@Composable
fun TransactionRow(
    record: TransactionRecord,
    onClick: () -> Unit,
    showDate: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val c = Mi.colors
    val v = categoryVisual(record.category, record.type)
    val isIncome = record.type == "INCOME"
    val title = record.merchant?.takeIf { it.isNotBlank() } ?: record.note?.takeIf { it.isNotBlank() } ?: record.category
    val noteLine = record.note?.takeIf { it.isNotBlank() && it != title }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .miRowClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = selectionMode) {
            Row {
                SelectMark(selected)
                Spacer(Modifier.width(12.dp))
            }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(v.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(v.icon, contentDescription = record.category, tint = v.color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.sp,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (record.externalId != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Verified, contentDescription = "已对账", tint = c.primary, modifier = Modifier.size(13.dp))
                }
                if (record.imageList.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Image, contentDescription = "有图片", tint = c.textTertiary, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    if (showDate) append(formatShortDate(record.timestamp)).append(' ')
                    append(formatTime(record.timestamp))
                    append(" · ").append(record.category)
                    append(" · ").append(record.channel)
                    if (noteLine != null) append(" · ").append(noteLine)
                },
                fontSize = 12.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            (if (isIncome) "+" else "-") + formatCents(record.amountCents),
            style = AmountStyle,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isIncome) c.income else c.textPrimary
        )
    }
}

/** 多选圆形勾选标记 */
@Composable
fun SelectMark(selected: Boolean) {
    val c = Mi.colors
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) c.primary else Color.Transparent)
            .border(1.6.dp, if (selected) c.primary else c.textTertiary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = "已选", tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/** 全局搜索：商家、备注、分类、账户、金额 */
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenRecord: (TransactionRecord) -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).transactionDao() }
    val all by dao.getAllFlow().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onBack)

    val results = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else all.filter { r ->
            r.merchant?.contains(q, true) == true ||
                r.note?.contains(q, true) == true ||
                r.category.contains(q, true) ||
                r.channel.contains(q, true) ||
                formatCents(r.amountCents, grouping = false).startsWith(q)
        }
    }
    val c = Mi.colors

    Column(modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        Row(modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MiIconButton(Icons.AutoMirrored.Filled.ArrowBackIos, onClick = onBack, iconSize = 18.dp)
            MiTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索商家、备注、分类或金额",
                leadingIcon = Icons.Default.Search,
                minHeight = 44.dp,
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        if (query.isNotBlank()) {
            val expense = results.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
            val income = results.filter { it.type == "INCOME" }.sumOf { it.amountCents }
            Text(
                "找到 ${results.size} 笔 · 支出 ${formatCents(expense)} · 收入 ${formatCents(income)}",
                fontSize = 12.sp,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.weight(1f)) {
            if (results.isNotEmpty()) {
                item {
                    MiCard {
                        results.take(300).forEachIndexed { i, r ->
                            TransactionRow(r, onClick = { onOpenRecord(r) }, showDate = true)
                            if (i != minOf(results.size, 300) - 1) MiDivider(startIndent = 68.dp)
                        }
                    }
                }
            } else if (query.isNotBlank()) {
                item {
                    Text(
                        "没有找到相关账单",
                        fontSize = 14.sp,
                        color = c.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp)
                    )
                }
            }
        }
    }
}
