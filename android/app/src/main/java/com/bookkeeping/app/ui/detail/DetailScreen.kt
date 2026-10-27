package com.bookkeeping.app.ui.detail

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.parser.CsvStatementImporter
import com.bookkeeping.app.parser.ImportSummary
import com.bookkeeping.app.service.ListenerWatchdog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val dao = db.transactionDao()

    var isListenerEnabled by remember { mutableStateOf(ListenerWatchdog.isNotificationListenerEnabled(context)) }
    var showPasteImportSheet by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    var importSummary by remember { mutableStateOf<ImportSummary?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // 当前选中的月份（默认为当前月）
    var selectedMonthCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) })
    }

    val currentMonthStart = remember(selectedMonthCalendar) {
        val c = selectedMonthCalendar.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    val currentMonthEnd = remember(selectedMonthCalendar) {
        val c = selectedMonthCalendar.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        c.timeInMillis
    }

    val monthLabel = remember(selectedMonthCalendar) {
        SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(selectedMonthCalendar.time)
    }

    // 账单选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val summary = CsvStatementImporter.importFromStream(stream, dao)
                        importSummary = summary
                    } ?: run {
                        importSummary = ImportSummary(errorMessage = "无法读取文件")
                    }
                } catch (e: Exception) {
                    importSummary = ImportSummary(errorMessage = "导入出错: ${e.message}")
                } finally {
                    isImporting = false
                }
            }
        }
    }

    val transactions by dao.getAllFlow().collectAsState(initial = emptyList())

    val monthTransactions = remember(transactions, currentMonthStart, currentMonthEnd) {
        transactions.filter { it.timestamp in currentMonthStart..currentMonthEnd }
    }

    val totalExpenseCents = remember(monthTransactions) {
        monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
    }

    val totalIncomeCents = remember(monthTransactions) {
        monthTransactions.filter { it.type == "INCOME" }.sumOf { it.amountCents }
    }

    val balanceCents = totalIncomeCents - totalExpenseCents

    // 按天分组倒序
    val groupedTransactions = remember(monthTransactions) {
        monthTransactions.groupBy { formatGroupDate(it.timestamp) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. 顶部钱迹经典纯粹看板
        QianjiHeaderCard(
            monthLabel = monthLabel,
            expenseCents = totalExpenseCents,
            incomeCents = totalIncomeCents,
            balanceCents = balanceCents,
            isListenerEnabled = isListenerEnabled,
            onPrevMonth = {
                val c = selectedMonthCalendar.clone() as Calendar
                c.add(Calendar.MONTH, -1)
                selectedMonthCalendar = c
            },
            onNextMonth = {
                val c = selectedMonthCalendar.clone() as Calendar
                c.add(Calendar.MONTH, 1)
                selectedMonthCalendar = c
            },
            onQuickPaste = { showPasteImportSheet = true },
            onOpenFilePicker = { filePickerLauncher.launch(arrayOf("*/*")) },
            onAddManual = { showManualAddDialog = true }
        )

        // 2. 流水明细列表
        if (monthTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "本月暂无记账明细",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "微信/支付宝/美团/京东/抖音付款即自动入账\n亦可点击上方「粘贴账单」快速对账",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groupedTransactions.forEach { (dateGroup, itemsInDay) ->
                    item(key = "hdr_$dateGroup") {
                        val dayExpense = itemsInDay.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
                        val dayIncome = itemsInDay.filter { it.type == "INCOME" }.sumOf { it.amountCents }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateGroup,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                            Text(
                                text = buildString {
                                    if (dayExpense > 0) append("出 ¥${formatCents(dayExpense)} ")
                                    if (dayIncome > 0) append("入 ¥${formatCents(dayIncome)}")
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }
                    }

                    items(itemsInDay, key = { it.id }) { item ->
                        QianjiTransactionRow(
                            record = item,
                            onClick = { selectedTransaction = item }
                        )
                    }
                }
            }
        }
    }

    // 快捷粘贴账单弹窗
    if (showPasteImportSheet) {
        PasteImportBottomSheet(
            onDismiss = { showPasteImportSheet = false },
            onImport = { text ->
                isImporting = true
                coroutineScope.launch {
                    try {
                        val summary = CsvStatementImporter.importFromText(text, dao)
                        importSummary = summary
                        showPasteImportSheet = false
                    } catch (e: Exception) {
                        importSummary = ImportSummary(errorMessage = "解析出错: ${e.message}")
                    } finally {
                        isImporting = false
                    }
                }
            }
        )
    }

    // 手动快速记账弹窗
    if (showManualAddDialog) {
        ManualAddTransactionDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { amountCents, type, category, merchant, channel ->
                coroutineScope.launch {
                    val record = TransactionRecord(
                        amountCents = amountCents,
                        type = type,
                        category = category,
                        merchant = merchant,
                        channel = channel,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insert(record)
                    showManualAddDialog = false
                    Toast.makeText(context, "已记一笔", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 流水详情与操作底栏
    selectedTransaction?.let { tx ->
        TransactionDetailBottomSheet(
            record = tx,
            onDismiss = { selectedTransaction = null },
            onDelete = {
                coroutineScope.launch {
                    dao.markDeleted(tx.id)
                    selectedTransaction = null
                    Toast.makeText(context, "已删除该条流水", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 导入完成统计弹窗
    importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { importSummary = null },
            title = {
                Text(
                    text = if (summary.errorMessage == null) "账单导入完成" else "导入提示",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (summary.errorMessage != null) {
                    Text(summary.errorMessage, color = MaterialTheme.colorScheme.error)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("共解析账单明细: ${summary.totalRows} 笔", fontWeight = FontWeight.SemiBold)
                        Text("• 全新录入: ${summary.newInserted} 笔", color = Color(0xFF10B981))
                        Text("• 智能去重合并: ${summary.mergedExisting} 笔 (补全官方单号与商家)", color = Color(0xFF2563EB))
                        Text("• 重复跳过: ${summary.skipped} 笔 (防重复入账)", color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { importSummary = null }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun QianjiHeaderCard(
    monthLabel: String,
    expenseCents: Long,
    incomeCents: Long,
    balanceCents: Long,
    isListenerEnabled: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onQuickPaste: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onAddManual: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 月份选择与监听微标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = monthLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "◀",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .clickable { onPrevMonth() }
                            .padding(4.dp)
                    )
                    Text(
                        text = "▶",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .clickable { onNextMonth() }
                            .padding(4.dp)
                    )
                }

                // 监听微点
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isListenerEnabled) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isListenerEnabled) "自动记账运行中" else "监听未开启",
                        fontSize = 11.sp,
                        color = if (isListenerEnabled) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 三列数据看板（钱迹经典布局）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 本月支出
                Column(modifier = Modifier.weight(1.3f)) {
                    Text("本月支出", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "¥${formatCents(expenseCents)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 本月收入
                Column(modifier = Modifier.weight(1f)) {
                    Text("本月收入", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "¥${formatCents(incomeCents)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF10B981)
                    )
                }

                // 本月结余
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("本月结余", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    val prefix = if (balanceCents >= 0) "" else "-"
                    val color = if (balanceCents >= 0) MaterialTheme.colorScheme.onSurface else Color(0xFFEF4444)
                    Text(
                        text = "$prefix¥${formatCents(Math.abs(balanceCents))}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = color
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 快捷操作胶囊栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickCapsuleButton(
                    icon = Icons.Default.ContentPaste,
                    label = "粘贴账单",
                    modifier = Modifier.weight(1f),
                    onClick = onQuickPaste
                )
                QuickCapsuleButton(
                    icon = Icons.Default.FileUpload,
                    label = "选文件",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFilePicker
                )
                QuickCapsuleButton(
                    icon = Icons.Default.Add,
                    label = "记一笔",
                    modifier = Modifier.weight(1f),
                    onClick = onAddManual
                )
            }
        }
    }
}

@Composable
fun QuickCapsuleButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QianjiTransactionRow(
    record: TransactionRecord,
    onClick: () -> Unit
) {
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val (icon, color) = remember(record.category, record.type) {
        getNativeCategoryIconAndColor(record.category, record.type)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 原生矢量分类圆形图标
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = record.category,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 商户与渠道两行排版
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.merchant ?: record.channel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (record.externalId != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "已对账",
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${record.channel} · ${record.category}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }
        }

        // 金额等宽大字
        val isExpense = record.type == "EXPENSE"
        val prefix = if (isExpense) "-" else "+"
        val amountColor = if (isExpense) MaterialTheme.colorScheme.onSurface else Color(0xFF10B981)

        Text(
            text = "$prefix¥${formatCents(record.amountCents)}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = amountColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteImportBottomSheet(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("粘贴账单文本入账", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null && clipboard.hasPrimaryClip()) {
                            val item = clipboard.primaryClip?.getItemAt(0)
                            val clipText = item?.text?.toString().orEmpty()
                            if (clipText.isNotBlank()) {
                                textInput = clipText
                                Toast.makeText(context, "已填入剪贴板文本", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("读取剪贴板", fontSize = 12.sp)
                }
            }

            Text(
                text = "直接粘贴从微信或支付宝复制的明细内容（支持几十笔批量识别）：",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("例如：2026-10-27 15:19:46,微信红包（单发）,发给a,/,支出,0.01...", fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onImport(textInput) },
                enabled = textInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("立即智能解析入账")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ManualAddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (amountCents: Long, type: String, category: String, merchant: String, channel: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf("餐饮美食") }
    var merchant by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("微信支付") }

    val categories = listOf("餐饮美食", "交通出行", "网络购物", "日常缴费", "转账红包", "休闲娱乐", "医疗健康", "其他支出")
    val channels = listOf("微信支付", "支付宝", "银行卡", "现金")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 收支切换
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isExpense) Color(0xFFEF4444) else Color(0xFFF1F3F5))
                            .clickable { isExpense = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("支出", color = if (isExpense) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isExpense) Color(0xFF10B981) else Color(0xFFF1F3F5))
                            .clickable { isExpense = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("收入", color = if (!isExpense) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("金额 (元)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商户 / 备注") },
                    placeholder = { Text("例如：全家、星巴克") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("分类：$category", fontSize = 12.sp, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(4).forEach { cat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (category == cat) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF1F3F5))
                                .clickable { category = cat }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(cat, fontSize = 11.sp, color = if (category == cat) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = amountStr.toDoubleOrNull() ?: 0.0
                    if (d > 0) {
                        val cents = Math.round(d * 100.0)
                        onConfirm(cents, if (isExpense) "EXPENSE" else "INCOME", category, merchant.ifBlank { category }, channel)
                    }
                },
                enabled = amountStr.toDoubleOrNull() != null && (amountStr.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailBottomSheet(
    record: TransactionRecord,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val timeFull = remember(record.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("账单详情", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFEF4444))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            DetailItemRow("交易商户", record.merchant ?: record.channel)
            DetailItemRow("交易金额", "¥${formatCents(record.amountCents)} (${if (record.type == "EXPENSE") "支出" else "收入"})")
            DetailItemRow("支付渠道", record.channel)
            DetailItemRow("账目分类", record.category)
            DetailItemRow("交易时间", timeFull)
            if (record.externalId != null) {
                DetailItemRow("官方交易单号", record.externalId)
            }
            if (!record.rawText.isNullOrBlank()) {
                DetailItemRow("原始摘要", record.rawText)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DetailItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun formatCents(cents: Long): String {
    val yuan = cents / 100
    val fen = cents % 100
    return "$yuan.${String.format(Locale.getDefault(), "%02d", fen)}"
}

fun formatGroupDate(timestamp: Long): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - 86400000L

    return when {
        timestamp >= todayStart -> "今天 · " + SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(timestamp))
        timestamp >= yesterdayStart -> "昨天 · " + SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(timestamp))
    }
}

fun getNativeCategoryIconAndColor(category: String, type: String): Pair<ImageVector, Color> {
    if (type == "INCOME") return Icons.Default.MonetizationOn to Color(0xFF10B981)
    return when (category) {
        "餐饮美食" -> Icons.Default.Restaurant to Color(0xFFF97316)
        "交通出行" -> Icons.Default.DirectionsCar to Color(0xFF3B82F6)
        "网络购物" -> Icons.Default.ShoppingBag to Color(0xFFA855F7)
        "转账红包" -> Icons.Default.SwapHoriz to Color(0xFFEC4899)
        "日常缴费" -> Icons.Default.Bolt to Color(0xFFEAB308)
        "休闲娱乐" -> Icons.Default.ConfirmationNumber to Color(0xFF06B6D4)
        "医疗健康" -> Icons.Default.MedicalServices to Color(0xFF22C55E)
        else -> Icons.Default.Category to Color(0xFF64748B)
    }
}
