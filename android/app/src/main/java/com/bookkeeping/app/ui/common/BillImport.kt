package com.bookkeeping.app.ui.common

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.BookkeepingApp
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.parser.CsvStatementImporter
import com.bookkeeping.app.parser.ImportSummary
import com.bookkeeping.app.ui.components.MiButton
import com.bookkeeping.app.ui.components.MiButtonStyle
import com.bookkeeping.app.ui.components.MiDialog
import com.bookkeeping.app.ui.components.MiSheet
import com.bookkeeping.app.ui.components.MiTextField
import com.bookkeeping.app.ui.theme.Mi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 外部应用分享进来的账单（文件或文本），用户确认后才导入 */
data class SharedImport(val uri: Uri?, val text: String?)

/** 账单导入入口（选文件 / 粘贴文本 / 分享导入）与结果弹窗 */
class BillImporter internal constructor(
    val pickFile: () -> Unit,
    val openPaste: () -> Unit,
    val importUri: (Uri) -> Unit,
    val importText: (String) -> Unit
)

@Composable
fun rememberBillImporter(): BillImporter {
    val context = LocalContext.current
    // 应用级作用域：切换 Tab 导致页面离开组合时，导入不会被中途取消
    val scope = remember { (context.applicationContext as BookkeepingApp).mainScope }
    val db = remember { AppDatabase.getInstance(context) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    fun runImport(block: suspend () -> ImportSummary, onDone: () -> Unit = {}) {
        if (importing) return
        importing = true
        scope.launch {
            summary = try {
                block()
            } catch (e: Exception) {
                ImportSummary(errorMessage = "导入出错：${e.message ?: e.javaClass.simpleName}")
            } finally {
                importing = false
            }
            onDone()
        }
    }

    val importUri: (Uri) -> Unit = { uri ->
        runImport({
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { CsvStatementImporter.importFromStream(it, db.transactionDao(), db) }
            } ?: ImportSummary(errorMessage = "无法读取文件")
        })
    }
    val importText: (String) -> Unit = { text ->
        runImport({ CsvStatementImporter.importFromText(text, db.transactionDao(), db) }, onDone = { showPaste = false })
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri(uri)
    }

    if (showPaste) {
        PasteImportSheet(
            loading = importing,
            onDismiss = { showPaste = false },
            onImport = importText
        )
    }

    summary?.let { ImportSummaryDialog(it) { summary = null } }

    return remember {
        BillImporter(
            pickFile = { picker.launch(arrayOf("*/*")) },
            openPaste = { showPaste = true },
            importUri = importUri,
            importText = importText
        )
    }
}

/** 分享导入确认：避免外部应用静默往账本写入流水 */
@Composable
fun SharedImportPrompt(shared: SharedImport, importer: BillImporter, onHandled: () -> Unit) {
    val preview = shared.text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(40)
    MiDialog(
        onDismissRequest = onHandled,
        title = "导入分享的账单？",
        message = if (shared.uri != null) "收到一个账单文件，导入后会自动识别并去重。" else "收到一段账单文本：\n$preview…",
        confirmText = "导入",
        onConfirm = {
            shared.uri?.let(importer.importUri) ?: shared.text?.let(importer.importText)
            onHandled()
        }
    )
}

@Composable
private fun ImportSummaryDialog(summary: ImportSummary, onDismiss: () -> Unit) {
    if (summary.errorMessage != null) {
        MiDialog(onDismissRequest = onDismiss, title = "导入未完成", message = summary.errorMessage, confirmText = "知道了", dismissText = null)
        return
    }
    MiDialog(
        onDismissRequest = onDismiss,
        title = "导入完成",
        confirmText = "好的",
        dismissText = null
    ) {
        val c = Mi.colors
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryLine("解析账单", "${summary.totalRows} 笔", c.textPrimary)
            SummaryLine("新增入账", "${summary.newInserted} 笔", c.income)
            SummaryLine("合并补全", "${summary.mergedExisting} 笔", c.primary)
            SummaryLine("重复跳过", "${summary.skipped} 笔", c.textSecondary)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = Mi.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PasteImportSheet(loading: Boolean, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    MiSheet(onDismissRequest = onDismiss, title = "粘贴账单") { _ ->
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "粘贴从微信/支付宝账单复制的明细文本，支持批量识别并自动去重。",
                fontSize = 13.sp,
                color = Mi.colors.textSecondary,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            MiTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "2026-10-27 15:19:46,微信红包,发给 a,/,支出,0.01…",
                singleLine = false,
                minHeight = 150.dp,
                textStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp)
            )
            Spacer(Modifier.height(16.dp))
            Row {
                MiButton(
                    "读取剪贴板",
                    icon = Icons.Default.ContentPaste,
                    style = MiButtonStyle.Secondary,
                    onClick = {
                        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clip.isNotBlank()) text = clip else Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.padding(6.dp))
                MiButton("识别入账", enabled = text.isNotBlank(), loading = loading, onClick = { onImport(text) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
