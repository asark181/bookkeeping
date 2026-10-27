package com.bookkeeping.app.ui.record

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.attachment.AttachmentStore
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.CategoryEntity
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.data.prefs.AppPrefs
import com.bookkeeping.app.ui.category.visual
import com.bookkeeping.app.ui.common.formatShortDate
import com.bookkeeping.app.ui.components.AttachmentThumb
import com.bookkeeping.app.ui.components.ImageViewer
import com.bookkeeping.app.ui.components.MiDatePickerSheet
import com.bookkeeping.app.ui.components.MiIconButton
import com.bookkeeping.app.ui.components.MiSegmented
import com.bookkeeping.app.ui.components.MiSheet
import com.bookkeeping.app.ui.components.MiListItem
import com.bookkeeping.app.ui.components.miClickable
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi
import kotlinx.coroutines.launch

val CHANNELS = listOf("微信支付", "支付宝", "银行卡", "信用卡", "现金", "美团支付", "京东支付", "抖音支付", "云闪付")

private const val MAX_IMAGES = 9

/**
 * 记一笔 / 编辑账单：鲨鱼记账式分类宫格 + 自绘计算器键盘。
 * [editing] 为空表示新建。
 */
@Composable
fun RecordEditorScreen(
    editing: TransactionRecord?,
    onClose: () -> Unit,
    onManageCategories: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val prefs = remember { AppPrefs(context) }
    val allCategories by db.categoryDao().getAllFlow().collectAsState(initial = emptyList())

    var isIncome by remember { mutableStateOf(editing?.type == "INCOME") }
    val type = if (isIncome) "INCOME" else "EXPENSE"
    val categories = remember(allCategories, type) { allCategories.filter { it.type == type } }
    var selectedName by remember { mutableStateOf(editing?.category) }

    var expression by remember { mutableStateOf(editing?.let { centsToInput(it.amountCents) } ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var merchant by remember { mutableStateOf(editing?.merchant ?: "") }
    var channel by remember { mutableStateOf(editing?.channel ?: prefs.lastChannel) }
    var timestamp by remember { mutableLongStateOf(editing?.timestamp ?: System.currentTimeMillis()) }
    val images = remember { mutableStateListOf<String>().apply { addAll(editing?.imageList.orEmpty()) } }
    /** 本次会话新加入、尚未保存的图片：放弃编辑时清理 */
    val freshImages = remember { mutableStateListOf<String>() }
    val removedImages = remember { mutableStateListOf<String>() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var savedCount by remember { mutableStateOf(0) }

    // 切换收支或分类加载后，保证有一个默认选中
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && categories.none { it.name == selectedName }) {
            if (editing == null || editing.type != type || selectedName == null) selectedName = categories.first().name
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
    ) { uris ->
        scope.launch {
            uris.take(MAX_IMAGES - images.size).forEach { uri ->
                AttachmentStore.import(context, uri)?.let { images.add(it); freshImages.add(it) }
            }
        }
    }

    fun close() {
        AttachmentStore.delete(context, freshImages)
        onClose()
    }
    BackHandler { close() }

    val evaluated = remember(expression) { Calculator.evaluate(expression) }
    val hasOperator = remember(expression) { Calculator.hasPendingOperator(expression) }

    fun save(again: Boolean) {
        val cents = evaluated ?: 0L
        if (cents <= 0L) {
            Toast.makeText(context, "请输入金额", Toast.LENGTH_SHORT).show()
            return
        }
        val categoryName = selectedName ?: categories.firstOrNull()?.name ?: "未分类"
        scope.launch {
            val dao = db.transactionDao()
            val now = System.currentTimeMillis()
            if (editing != null) {
                dao.update(
                    editing.copy(
                        amountCents = cents,
                        type = type,
                        category = categoryName,
                        channel = channel,
                        merchant = merchant.trim().ifBlank { null },
                        note = note.trim().ifBlank { null },
                        images = images.joinToString("\n").ifBlank { null },
                        timestamp = timestamp,
                        updatedAt = now
                    )
                )
                AttachmentStore.delete(context, removedImages)
            } else {
                dao.insert(
                    TransactionRecord(
                        amountCents = cents,
                        type = type,
                        channel = channel,
                        merchant = merchant.trim().ifBlank { null },
                        category = categoryName,
                        timestamp = timestamp,
                        note = note.trim().ifBlank { null },
                        images = images.joinToString("\n").ifBlank { null },
                        updatedAt = now
                    )
                )
                AttachmentStore.delete(context, removedImages)
            }
            prefs.lastChannel = channel
            freshImages.clear()
            removedImages.clear()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (again && editing == null) {
                savedCount++
                expression = ""
                note = ""
                merchant = ""
                images.clear()
                Toast.makeText(context, "已记一笔，继续记录", Toast.LENGTH_SHORT).show()
            } else {
                onClose()
            }
        }
    }

    val c = Mi.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        // 顶部：关闭 + 收支切换
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp)
        ) {
            MiIconButton(Icons.Default.Close, onClick = ::close, modifier = Modifier.align(Alignment.CenterStart), contentDescription = "关闭")
            MiSegmented(
                options = listOf("支出", "收入"),
                selectedIndex = if (isIncome) 1 else 0,
                onSelect = { isIncome = it == 1 },
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(168.dp)
            )
            if (editing != null) {
                Text(
                    "编辑",
                    fontSize = 14.sp,
                    color = c.textSecondary,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                )
            } else if (savedCount > 0) {
                Text(
                    "已记 $savedCount 笔",
                    fontSize = 13.sp,
                    color = c.primary,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                )
            }
        }

        // 分类宫格
        AnimatedContent(
            targetState = isIncome,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "categoryGrid"
        ) { _ ->
            // 分类加载完成后再渲染，避免宫格以「设置」项为锚点产生初始滚动偏移
            if (categories.isNotEmpty()) LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categories, key = { it.id }) { cat ->
                    CategoryCell(
                        category = cat,
                        selected = cat.name == selectedName,
                        onClick = {
                            selectedName = cat.name
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
                item(key = "manage") {
                    GridCell(icon = Icons.Default.Settings, label = "设置", color = c.textSecondary, selected = false, onClick = onManageCategories)
                }
            }
        }

        // 底部记账面板
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.surface)
                .navigationBarsPadding()
                .padding(top = 14.dp)
        ) {
            // 备注 + 金额
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selected = categories.firstOrNull { it.name == selectedName }
                if (selected != null) {
                    val v = selected.visual()
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(v.color),
                        contentAlignment = Alignment.Center
                    ) { Icon(v.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(selected.name, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                AnimatedContent(
                    targetState = expression.ifEmpty { "0.00" },
                    transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut()) },
                    label = "amount"
                ) { text ->
                    Text(
                        text,
                        style = AmountStyle,
                        fontSize = if (text.length > 12) 24.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) c.income else c.textPrimary,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                }
            }
            if (hasOperator && evaluated != null) {
                Text(
                    "= ${centsToInput(evaluated)}",
                    style = AmountStyle,
                    fontSize = 14.sp,
                    color = c.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(Modifier.height(10.dp))

            // 备注输入
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.fill)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (note.isEmpty()) Text("写点备注…", fontSize = 15.sp, color = c.textTertiary)
                    BasicTextField(
                        value = note,
                        onValueChange = { if (it.length <= 200) note = it },
                        textStyle = TextStyle(fontSize = 15.sp, color = c.textPrimary),
                        cursorBrush = SolidColor(c.primary),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = "添加图片",
                    tint = if (images.size >= MAX_IMAGES) c.textTertiary else c.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .miClickable(enabled = images.size < MAX_IMAGES) {
                            focus.clearFocus()
                            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                )
            }

            if (images.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    images.forEachIndexed { index, name ->
                        AttachmentThumb(
                            name = name,
                            size = 60.dp,
                            onClick = { viewerIndex = index },
                            onRemove = {
                                images.remove(name)
                                if (freshImages.remove(name)) AttachmentStore.delete(context, listOf(name)) else removedImages.add(name)
                            }
                        )
                    }
                }
            }

            // 日期 / 账户 / 商户 快捷栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolChip(Icons.Default.CalendarMonth, formatShortDate(timestamp)) { focus.clearFocus(); showDatePicker = true }
                ToolChip(Icons.Default.AccountBalanceWallet, channel) { focus.clearFocus(); showChannelPicker = true }
                MerchantChip(merchant) { merchant = it }
            }

            // 计算器键盘
            Keypad(
                hasOperator = hasOperator,
                isEditing = editing != null,
                onKey = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expression = Calculator.input(expression, key)
                },
                onEquals = { evaluated?.let { expression = centsToInput(it) } },
                onAgain = { save(again = true) },
                onDone = { save(again = false) }
            )
        }
    }

    if (showDatePicker) {
        MiDatePickerSheet(initialMillis = timestamp, onDismiss = { showDatePicker = false }, onPick = { timestamp = it })
    }
    if (showChannelPicker) {
        MiSheet(onDismissRequest = { showChannelPicker = false }, title = "选择账户") { dismiss ->
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                CHANNELS.forEach { ch ->
                    MiListItem(
                        title = ch,
                        showArrow = false,
                        trailing = if (ch == channel) {
                            { Box(Modifier.size(10.dp).clip(CircleShape).background(Mi.colors.primary)) }
                        } else null,
                        onClick = { dismiss { channel = ch } }
                    )
                }
            }
        }
    }
    viewerIndex?.let { idx ->
        ImageViewer(images = images.toList(), startIndex = idx, onDismiss = { viewerIndex = null })
    }
}

@Composable
private fun CategoryCell(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val v = category.visual()
    GridCell(icon = v.icon, label = category.name, color = v.color, selected = selected, onClick = onClick)
}

@Composable
private fun GridCell(icon: ImageVector, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val c = Mi.colors
    val bg by animateColorAsState(if (selected) color else c.surface, label = "cellBg")
    val tint by animateColorAsState(if (selected) Color.White else color, label = "cellTint")
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.5f), label = "cellScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .miClickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(50.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) c.textPrimary else c.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    val c = Mi.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.fill)
            .miClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 商户名内联输入胶囊 */
@Composable
private fun MerchantChip(value: String, onChange: (String) -> Unit) {
    val c = Mi.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.fill)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Storefront, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Box(modifier = Modifier.width(96.dp)) {
            if (value.isEmpty()) Text("商家/对象", fontSize = 13.sp, color = c.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 40) onChange(it) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Keypad(
    hasOperator: Boolean,
    isEditing: Boolean,
    onKey: (String) -> Unit,
    onEquals: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit
) {
    val c = Mi.colors
    val rows = listOf(
        listOf("1", "2", "3", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("7", "8", "9", "-"),
        listOf(if (isEditing) "C" else "再记", "0", ".", "完成")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(bottom = 10.dp, top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isDone = key == "完成"
                    val label = if (isDone && hasOperator) "=" else key
                    val bg = when {
                        isDone -> c.primary
                        key == "再记" -> c.primarySoft
                        key in listOf("⌫", "+", "-", "C") -> c.fill
                        else -> if (c.isDark) c.fill.copy(alpha = 0.55f) else Color(0xFFF7F8FA)
                    }
                    val fg = when {
                        isDone -> Color.White
                        key == "再记" -> c.primary
                        else -> c.textPrimary
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .miClickable(pressedScale = 0.92f) {
                                when (key) {
                                    "完成" -> if (hasOperator) onEquals() else onDone()
                                    "再记" -> onAgain()
                                    "C" -> onKey("C")
                                    else -> onKey(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "删除", tint = fg, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                label,
                                fontSize = if (label.length > 1) 16.sp else 22.sp,
                                fontWeight = if (label.length > 1 || isDone) FontWeight.SemiBold else FontWeight.Medium,
                                color = fg,
                                style = AmountStyle
                            )
                        }
                    }
                }
            }
        }
    }
}

fun centsToInput(cents: Long): String {
    val yuan = cents / 100
    val fen = cents % 100
    return when {
        fen == 0L -> yuan.toString()
        fen % 10 == 0L -> "$yuan.${fen / 10}"
        else -> "$yuan.${fen.toString().padStart(2, '0')}"
    }
}
