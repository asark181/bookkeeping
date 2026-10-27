package com.bookkeeping.app.ui.profile

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bookkeeping.app.data.auth.AccountManager
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.parser.CsvStatementImporter
import com.bookkeeping.app.parser.ImportSummary
import com.bookkeeping.app.service.ListenerWatchdog
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val dao = db.transactionDao()
    val accountManager = remember { AccountManager(context) }

    var userProfile by remember { mutableStateOf(accountManager.getProfile()) }
    var isListenerEnabled by remember { mutableStateOf(ListenerWatchdog.isNotificationListenerEnabled(context)) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var importSummary by remember { mutableStateOf<ImportSummary?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
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
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerEnabled = ListenerWatchdog.isNotificationListenerEnabled(context)
                userProfile = accountManager.getProfile()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("个人中心", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 用户登录卡片
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            if (userProfile != null) {
                                Text(
                                    text = userProfile!!.username,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("个人账本 · 零知识密文同步", fontSize = 11.sp, color = Color(0xFF10B981))
                                }
                            } else {
                                Text(
                                    text = "本地离线账本",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "登录后支持多设备加密备份",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (userProfile != null) {
                            TextButton(
                                onClick = {
                                    accountManager.logout()
                                    userProfile = null
                                    Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("退出", color = Color(0xFFEF4444), fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { showAuthDialog = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("登录 / 注册", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 功能菜单
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        SettingItemRow(
                            icon = Icons.Default.NotificationsActive,
                            iconColor = if (isListenerEnabled) Color(0xFF10B981) else Color(0xFFEF4444),
                            title = "自动记账通知监听",
                            subtitle = if (isListenerEnabled) "服务正常运行中" else "未开启，点击去系统设置允许",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        )

                        SettingItemRow(
                            icon = Icons.Default.FileUpload,
                            iconColor = Color(0xFF2563EB),
                            title = "导入官方对账单",
                            subtitle = "支持从手机存储选取 .csv 或 .zip 压缩包",
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )

                        SettingItemRow(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            iconColor = Color(0xFF8B5CF6),
                            title = "微信/支付宝账单导出指引",
                            subtitle = "如何从微信和支付宝导出个人对账单",
                            onClick = { showGuideDialog = true }
                        )

                        SettingItemRow(
                            icon = Icons.Default.Shield,
                            iconColor = Color(0xFF0284C7),
                            title = "隐私与端到端加密",
                            subtitle = "账目本地 AES-GCM 密文落盘，绝无第三方窥探",
                            onClick = {}
                        )

                        SettingItemRow(
                            icon = Icons.Default.Info,
                            iconColor = Color(0xFF6B7280),
                            title = "关于智能记账",
                            subtitle = "v1.1.0 · 小米 15 Pro 深度适配",
                            onClick = { showAboutDialog = true }
                        )
                    }
                }
            }
        }
    }

    // 登录注册弹窗
    if (showAuthDialog) {
        AuthModalDialog(
            accountManager = accountManager,
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = { profile ->
                userProfile = profile
                showAuthDialog = false
                Toast.makeText(context, "欢迎回来，${profile.username}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 关于弹窗
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("智能记账 v1.1.0", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "针对小米 15 Pro 与 Xiaomi HyperOS 2 深度定制的纯粹记账工具。\n\n" +
                            "• 毫秒级闭环自动记账（支付宝、微信、美团外卖、京东白条、抖音商城）\n" +
                            "• 1:1 原生钱迹极简风，多维分类统计与图表\n" +
                            "• 微信好友转账/红包官方对账单智能导入与去重\n" +
                            "• 零知识端到端加密备份",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("关闭") }
            }
        )
    }

    // 导出指引弹窗
    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text("官方账单导出方法", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "1. 打开微信 →「我」→「服务」→「钱包」→ 右上角「账单」\n" +
                            "2. 再次点击右上角「常见问题」或「...」→ 选择「账单导出」→「用于个人对账」\n" +
                            "3. 微信会将 CSV 或 ZIP 发送至你的邮箱。下载后可直接在「明细」页点击「粘贴账单」或选择该文件导入！",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(onClick = { showGuideDialog = false }) { Text("知道了") }
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
fun SettingItemRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
    }
}
