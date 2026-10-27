package com.bookkeeping.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.prefs.AppPrefs
import com.bookkeeping.app.ui.components.MiDialog
import com.bookkeeping.app.ui.components.MiTextField
import com.bookkeeping.app.ui.theme.AmountStyle

/** 月度预算设置弹窗，保存后回调新预算（分） */
@Composable
fun BudgetDialog(onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val prefs = AppPrefs(LocalContext.current)
    var input by remember { mutableStateOf(prefs.monthlyBudgetCents.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    MiDialog(
        onDismissRequest = onDismiss,
        title = "月度预算",
        message = "设置每月支出上限，首页实时显示剩余额度。留空表示不设预算。",
        confirmText = "保存",
        onConfirm = {
            val cents = (input.toLongOrNull() ?: 0L).coerceAtLeast(0L) * 100
            prefs.monthlyBudgetCents = cents
            onSaved(cents)
        }
    ) {
        MiTextField(
            value = input,
            onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) input = v },
            placeholder = "例如 3000",
            keyboardType = KeyboardType.Number,
            textStyle = AmountStyle.copy(fontSize = 20.sp, textAlign = TextAlign.Center)
        )
    }
}
