package com.bookkeeping.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.auth.AccountManager
import com.bookkeeping.app.data.auth.UserProfile
import com.bookkeeping.app.ui.components.MiButton
import com.bookkeeping.app.ui.components.MiSegmented
import com.bookkeeping.app.ui.components.MiSheet
import com.bookkeeping.app.ui.components.MiTextField
import com.bookkeeping.app.ui.theme.Mi
import kotlinx.coroutines.launch

@Composable
fun AuthModalDialog(
    accountManager: AccountManager,
    onDismiss: () -> Unit,
    onAuthSuccess: (UserProfile) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    MiSheet(onDismissRequest = onDismiss, title = if (isRegisterMode) "注册账号" else "登录") { dismiss ->
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            MiSegmented(
                options = listOf("账号登录", "注册新账号"),
                selectedIndex = if (isRegisterMode) 1 else 0,
                onSelect = { isRegisterMode = it == 1; errorMsg = null },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isRegisterMode) "注册个人账号。账单始终保存在本机，不会上传。" else "登录你的个人账号。账单始终保存在本机。",
                fontSize = 13.sp,
                color = Mi.colors.textSecondary
            )
            Spacer(Modifier.height(16.dp))
            MiTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = "用户名 / 手机号",
                leadingIcon = Icons.Default.Person,
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(10.dp))
            MiTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "密码（至少 6 位）",
                leadingIcon = Icons.Default.Lock,
                password = true
            )
            if (isRegisterMode) {
                Spacer(Modifier.height(10.dp))
                MiTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = "再次输入密码",
                    leadingIcon = Icons.Default.Lock,
                    password = true
                )
            }
            errorMsg?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Mi.colors.danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            MiButton(
                text = if (isRegisterMode) "注册并登录" else "登录",
                loading = isLoading,
                enabled = username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val u = username.trim()
                    val p = password.trim()
                    errorMsg = when {
                        u.length < 3 -> "用户名至少需要 3 个字符"
                        p.length < 6 -> "密码至少需要 6 位"
                        isRegisterMode && p != confirmPassword.trim() -> "两次输入的密码不一致"
                        else -> null
                    }
                    if (errorMsg != null) return@MiButton
                    isLoading = true
                    coroutineScope.launch {
                        val result = if (isRegisterMode) accountManager.register(u, p) else accountManager.loginWithPassword(u, p)
                        isLoading = false
                        result
                            .onSuccess { profile -> dismiss { onAuthSuccess(profile) } }
                            .onFailure { errorMsg = it.message ?: "操作失败，请重试" }
                    }
                }
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
