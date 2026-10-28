package com.bookkeeping.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import com.bookkeeping.app.update.AppUpdate
import com.bookkeeping.app.update.UpdatePhase
import com.bookkeeping.app.update.UpdateUi
import com.bookkeeping.app.ui.components.MiButton
import com.bookkeeping.app.ui.components.MiButtonStyle
import com.bookkeeping.app.ui.components.MiIconBadge
import com.bookkeeping.app.ui.components.MiSheet
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi
import java.util.Locale

private fun buttonLabel(phase: UpdatePhase) = when (phase) {
    UpdatePhase.Available -> "下载并安装"
    UpdatePhase.Downloading -> "取消下载"
    UpdatePhase.Ready -> "立即安装"
    UpdatePhase.NeedsPermission -> "去允许安装"
    UpdatePhase.Failed -> "重新下载"
}

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

/** 应用内更新面板：版本说明 + 下载进度 + 一个主按钮 */
@Composable
fun UpdateSheet(ui: UpdateUi, updates: AppUpdate) {
    val context = LocalContext.current
    val c = Mi.colors
    MiSheet(onDismissRequest = { updates.dismissSheet() }) { _ ->
        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Row {
                MiIconBadge(Icons.Default.SystemUpdate, c.primary, size = 44.dp, iconSize = 24.dp)
                Spacer(Modifier.padding(6.dp))
                Column {
                    Text("发现新版本 ${ui.offer.versionName}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Text("应用内下载，下载完成后直接安装", fontSize = 13.sp, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                ui.offer.notes.ifBlank { "修复问题并优化体验。" },
                fontSize = 15.sp,
                color = c.textPrimary,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.fill)
                    .padding(14.dp)
            )
            if (ui.phase == UpdatePhase.Downloading) {
                Spacer(Modifier.height(16.dp))
                val fraction = if (ui.total > 0) (ui.received.toFloat() / ui.total).coerceIn(0f, 1f) else 0f
                val animated by animateFloatAsState(fraction, label = "updateProgress")
                Column(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(c.fill)) {
                    Spacer(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.primary))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (ui.total > 0) "已下载 ${(fraction * 100).toInt()}% · ${mb(ui.received)} / ${mb(ui.total)}" else "正在连接…",
                    style = AmountStyle,
                    fontSize = 12.sp,
                    color = c.textSecondary
                )
            }
            if (ui.message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(ui.message, fontSize = 13.sp, color = if (ui.phase == UpdatePhase.Failed) c.danger else c.textSecondary)
            }
            Spacer(Modifier.height(20.dp))
            MiButton(
                buttonLabel(ui.phase),
                onClick = { updates.onPrimary(context) },
                style = if (ui.phase == UpdatePhase.Downloading) MiButtonStyle.Secondary else MiButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}
