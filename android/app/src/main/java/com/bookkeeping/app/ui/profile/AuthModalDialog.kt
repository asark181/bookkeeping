package com.bookkeeping.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookkeeping.app.data.auth.AccountManager
import com.bookkeeping.app.data.auth.UserProfile
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isRegisterMode) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable {
                            isRegisterMode = false
                            errorMsg = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "账号登录",
                        fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        color = if (!isRegisterMode) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRegisterMode) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable {
                            isRegisterMode = true
                            errorMsg = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "注册新账号",
                        fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        color = if (isRegisterMode) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isRegisterMode) "注册专属个人账号，账目本地端到端加密，支持多端安全同步。" else "登录你的个人账号，自动同步云端密文备份。",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名 / 手机号") },
                    placeholder = { Text("至少3位字符") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    placeholder = { Text("至少6位密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码") },
                        placeholder = { Text("请再次输入密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errorMsg?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val u = username.trim()
                    val p = password.trim()
                    if (u.length < 3) {
                        errorMsg = "用户名至少需要3个字符"
                        return@Button
                    }
                    if (p.length < 6) {
                        errorMsg = "密码至少需要6位"
                        return@Button
                    }
                    if (isRegisterMode && p != confirmPassword.trim()) {
                        errorMsg = "两次输入的密码不一致"
                        return@Button
                    }

                    isLoading = true
                    errorMsg = null
                    coroutineScope.launch {
                        val result = if (isRegisterMode) {
                            accountManager.register(u, p)
                        } else {
                            accountManager.loginWithPassword(u, p)
                        }
                        isLoading = false
                        result.onSuccess { profile ->
                            onAuthSuccess(profile)
                        }.onFailure {
                            errorMsg = it.message ?: "操作失败，请重试"
                        }
                    }
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isRegisterMode) "立即注册并登录" else "登录")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
