package com.bookkeeping.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.ui.category.categoryVisual
import com.bookkeeping.app.ui.common.formatCents
import com.bookkeeping.app.ui.common.formatFullTime
import com.bookkeeping.app.ui.components.AttachmentThumb
import com.bookkeeping.app.ui.components.ImageViewer
import com.bookkeeping.app.ui.components.MiButton
import com.bookkeeping.app.ui.components.MiButtonStyle
import com.bookkeeping.app.ui.components.MiDialog
import com.bookkeeping.app.ui.components.MiDivider
import com.bookkeeping.app.ui.components.MiSheet
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi

/** 账单详情面板：金额、分类、备注、图片、来源信息，以及编辑/删除 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
    record: TransactionRecord,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = Mi.colors
    val v = categoryVisual(record.category, record.type)
    val isIncome = record.type == "INCOME"
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    MiSheet(onDismissRequest = onDismiss) { dismiss ->
        Column(
            modifier = Modifier
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.82f).dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(56.dp).clip(CircleShape).background(v.color), contentAlignment = Alignment.Center) {
                    Icon(v.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(record.category, fontSize = 15.sp, color = c.textSecondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (isIncome) "+" else "-") + formatCents(record.amountCents),
                    style = AmountStyle,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) c.income else c.textPrimary
                )
                if (record.externalId != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.primarySoft).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = c.primary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("已与官方账单核对", fontSize = 11.sp, color = c.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.fill.copy(alpha = if (c.isDark) 0.6f else 0.6f))) {
                val rows = buildList {
                    add("时间" to formatFullTime(record.timestamp))
                    add("账户" to record.channel)
                    record.merchant?.takeIf { it.isNotBlank() }?.let { add("商家" to it) }
                    record.externalId?.let { add("交易单号" to it) }
                }
                rows.forEachIndexed { i, (label, value) ->
                    InfoRow(label, value)
                    if (i != rows.lastIndex) MiDivider(startIndent = 16.dp)
                }
            }

            if (!record.note.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("备注", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    record.note,
                    fontSize = 15.sp,
                    color = c.textPrimary,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.fill.copy(alpha = 0.6f)).padding(14.dp)
                )
            }

            val images = record.imageList
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("图片（${images.size}）", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    images.forEachIndexed { index, name ->
                        AttachmentThumb(name = name, size = 96.dp, onClick = { viewerIndex = index })
                    }
                }
            }

            if (!record.rawText.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("原始通知", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    record.rawText,
                    fontSize = 12.sp,
                    color = c.textSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.fill.copy(alpha = 0.6f)).padding(14.dp)
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiButton("删除", onClick = { confirmDelete = true }, style = MiButtonStyle.Danger, icon = Icons.Default.DeleteOutline, modifier = Modifier.weight(1f))
                MiButton("编辑", onClick = { dismiss(onEdit) }, icon = Icons.Default.Edit, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
        }

        if (confirmDelete) {
            MiDialog(
                onDismissRequest = { confirmDelete = false },
                title = "删除这笔账单？",
                message = "删除后不可恢复，附带的图片也会一并清除。",
                confirmText = "删除",
                confirmStyle = MiButtonStyle.Danger,
                onConfirm = { dismiss(onDelete) }
            )
        }
    }

    viewerIndex?.let { ImageViewer(record.imageList, it) { viewerIndex = null } }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(label, fontSize = 14.sp, color = Mi.colors.textSecondary, modifier = Modifier.width(76.dp))
        Text(value, fontSize = 14.sp, color = Mi.colors.textPrimary, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
