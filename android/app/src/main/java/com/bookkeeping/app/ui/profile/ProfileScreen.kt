package com.bookkeeping.app.ui.profile

import kotlinx.coroutines.launch
import com.bookkeeping.app.ui.components.MiTextField
import com.bookkeeping.app.ui.common.formatTime
import com.bookkeeping.app.ui.common.formatShortDate
import com.bookkeeping.app.data.sync.SyncState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.CloudSync
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bookkeeping.app.BookkeepingApp
import com.bookkeeping.app.BuildConfig
import com.bookkeeping.app.update.ManualCheck
import com.bookkeeping.app.data.auth.AccountManager
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.prefs.AppPrefs
import com.bookkeeping.app.service.ListenerWatchdog
import com.bookkeeping.app.service.PaymentAccessibilityService
import com.bookkeeping.app.ui.common.BudgetDialog
import com.bookkeeping.app.ui.common.dayStart
import com.bookkeeping.app.ui.common.formatCents
import com.bookkeeping.app.ui.common.rememberBillImporter
import com.bookkeeping.app.ui.components.MiButton
import com.bookkeeping.app.ui.components.MiButtonStyle
import com.bookkeeping.app.ui.components.MiCard
import com.bookkeeping.app.ui.components.MiDialog
import com.bookkeeping.app.ui.components.MiDivider
import com.bookkeeping.app.ui.components.MiLargeTitle
import com.bookkeeping.app.ui.components.MiListItem
import com.bookkeeping.app.ui.components.MiSectionTitle
import com.bookkeeping.app.ui.components.MiSwitch
import com.bookkeeping.app.ui.components.miClickable
import com.bookkeeping.app.ui.theme.AmountStyle
import com.bookkeeping.app.ui.theme.Mi

private fun isAccessibilityOn(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains("${context.packageName}/${PaymentAccessibilityService::class.java.name}") ||
        enabled.contains("${context.packageName}/.service.PaymentAccessibilityService")
}

@Composable
fun ProfileScreen(onManageCategories: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accountManager = remember { AccountManager(context) }
    val prefs = remember { AppPrefs(context) }
    val dao = remember { AppDatabase.getInstance(context).transactionDao() }
    val all by dao.getAllFlow().collectAsState(initial = emptyList())
    val importer = rememberBillImporter()
    val updates = remember { (context.applicationContext as BookkeepingApp).updates }
    val updateUi by updates.ui.collectAsState()
    val manual by updates.manual.collectAsState()
    val sync = remember { (context.applicationContext as BookkeepingApp).sync }
    val syncState by sync.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showSyncPassword by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf(accountManager.getProfile()) }
    var listenerOn by remember { mutableStateOf(ListenerWatchdog.isNotificationListenerEnabled(context)) }
    var a11yOn by remember { mutableStateOf(isAccessibilityOn(context)) }
    var showAuth by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var budget by remember { mutableLongStateOf(prefs.monthlyBudgetCents) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                listenerOn = ListenerWatchdog.isNotificationListenerEnabled(context)
                a11yOn = isAccessibilityOn(context)
                profile = accountManager.getProfile()
                sync.refreshState()
                budget = prefs.monthlyBudgetCents
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val days = remember(all) { all.map { dayStart(it.timestamp) }.distinct().size }
    val c = Mi.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item { MiLargeTitle("我的") }

        // 账号卡片
        item {
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .miClickable(pressedScale = 0.98f) { if (profile == null) showAuth = true }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = profile?.username?.firstOrNull()?.uppercase()
                        if (initial != null) {
                            Text(initial, fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile?.username ?: "登录 / 注册", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (profile != null) "个人账本 · 已登录" else "登录后多台手机自动加密同步",
                            fontSize = 13.sp,
                            color = c.textSecondary
                        )
                    }
                    if (profile != null) {
                        MiButton("退出", onClick = { confirmLogout = true }, style = MiButtonStyle.Secondary, height = 34.dp)
                    }
                }
                MiDivider(startIndent = 18.dp)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    StatBlock("记账天数", "$days", Modifier.weight(1f))
                    StatBlock("账单总数", "${all.size}", Modifier.weight(1f))
                    StatBlock("月度预算", if (budget > 0) formatCents(budget).substringBefore('.') else "未设置", Modifier.weight(1f))
                }
            }
        }

        item {
            MiSectionTitle("云同步")
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                val state = syncState
                MiListItem(
                    title = "多设备加密同步",
                    summary = when (state) {
                        SyncState.SignedOut -> "登录后自动同步账单、分类与预算，数据端到端加密"
                        SyncState.NeedsPassword -> "需要验证一次密码以开启同步"
                        SyncState.Idle -> "已开启，等待首次同步"
                        SyncState.Syncing -> "正在同步…"
                        is SyncState.Done -> "已开启 · 上次同步 ${formatShortDate(state.at)} ${formatTime(state.at)}"
                        is SyncState.Failed -> state.reason
                    },
                    icon = Icons.Default.CloudSync,
                    iconColor = if (state is SyncState.Failed) Color(0xFFF2453D) else Color(0xFF3482FF),
                    trailing = when (state) {
                        SyncState.Syncing -> { { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mi.colors.primary) } }
                        SyncState.Idle, is SyncState.Done, is SyncState.Failed -> {
                            {
                                Text(
                                    "立即同步",
                                    fontSize = 13.sp,
                                    color = Mi.colors.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Mi.colors.primarySoft).padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        else -> null
                    },
                    onClick = {
                        when (state) {
                            SyncState.SignedOut -> showAuth = true
                            SyncState.NeedsPassword -> showSyncPassword = true
                            SyncState.Syncing -> Unit
                            else -> scope.launch { sync.syncNow() }
                        }
                    }
                )
            }
        }

        item {
            MiSectionTitle("自动记账")
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                MiListItem(
                    title = "通知监听",
                    summary = if (listenerOn) "正在监听微信、支付宝等支付通知" else "未开启，付款后无法自动入账",
                    icon = Icons.Default.NotificationsActive,
                    iconColor = if (listenerOn) Color(0xFF16B47A) else Color(0xFFF2453D),
                    trailing = { MiSwitch(listenerOn, { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) },
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                )
                MiDivider(startIndent = 66.dp)
                MiListItem(
                    title = "无障碍补充识别",
                    summary = "识别微信转账、红包等不弹通知的支付（可选）",
                    icon = Icons.Default.AccessibilityNew,
                    iconColor = Color(0xFF5B6CFF),
                    trailing = { MiSwitch(a11yOn, { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) },
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                MiDivider(startIndent = 66.dp)
                MiListItem(
                    title = "后台保活设置",
                    summary = "在应用详情中将省电策略设为「无限制」并允许自启动",
                    icon = Icons.Default.BatterySaver,
                    iconColor = Color(0xFFFF9500),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
            }
        }

        item {
            MiSectionTitle("记账设置")
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                MiListItem(
                    title = "分类管理",
                    summary = "新建、编辑、删除收支分类",
                    icon = Icons.Default.Category,
                    iconColor = Color(0xFFA66BFF),
                    onClick = onManageCategories
                )
                MiDivider(startIndent = 66.dp)
                MiListItem(
                    title = "月度预算",
                    value = if (budget > 0) formatCents(budget) else "未设置",
                    icon = Icons.Default.Savings,
                    iconColor = Color(0xFF3482FF),
                    onClick = { showBudget = true }
                )
            }
        }

        item {
            MiSectionTitle("账单导入")
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                MiListItem(
                    title = "导入账单文件",
                    summary = "微信/支付宝官方 CSV、XLSX、ZIP",
                    icon = Icons.Default.FileOpen,
                    iconColor = Color(0xFFFF8A3D),
                    onClick = importer.pickFile
                )
                MiDivider(startIndent = 66.dp)
                MiListItem(
                    title = "粘贴账单文本",
                    icon = Icons.Default.ContentPaste,
                    iconColor = Color(0xFF16B47A),
                    onClick = importer.openPaste
                )
                MiDivider(startIndent = 66.dp)
                MiListItem(
                    title = "如何导出官方账单",
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    iconColor = Color(0xFF00B8D9),
                    onClick = { showGuide = true }
                )
            }
        }

        item {
            MiSectionTitle("关于")
            MiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                MiListItem(
                    title = "隐私与数据",
                    icon = Icons.Default.Shield,
                    iconColor = Color(0xFF0284C7),
                    onClick = { showPrivacy = true }
                )
                MiDivider(startIndent = 66.dp)
                MiListItem(
                    title = "检查更新",
                    summary = when {
                        updateUi != null -> "发现新版本 ${updateUi!!.offer.versionName}，点击应用内更新"
                        manual is ManualCheck.Failed -> "检查失败：${(manual as ManualCheck.Failed).reason}，点击重试"
                        else -> null
                    },
                    value = when {
                        updateUi != null -> null
                        manual == ManualCheck.Checking -> "检查中…"
                        manual == ManualCheck.UpToDate -> "已是最新"
                        else -> "v${BuildConfig.VERSION_NAME}"
                    },
                    icon = Icons.Default.SystemUpdate,
                    iconColor = Color(0xFF16B47A),
                    trailing = if (updateUi != null) {
                        {
                            Text(
                                "NEW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Mi.colors.danger).padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else null,
                    onClick = { updates.checkFromUser() }
                )
                MiDivider(startIndent = 66.dp)
                MiListItem(
                    title = "关于智能记账",
                    value = "v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Default.Info,
                    iconColor = Color(0xFF8E99A8),
                    onClick = { showAbout = true }
                )
            }
        }
    }

    if (showAuth) {
        AuthModalDialog(
            accountManager = accountManager,
            onDismiss = { showAuth = false },
            onAuthSuccess = {
                profile = it
                showAuth = false
                Toast.makeText(context, "欢迎，${it.username}", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (showSyncPassword) {
        var pwd by remember { mutableStateOf("") }
        MiDialog(
            onDismissRequest = { showSyncPassword = false },
            title = "验证密码",
            message = "同步数据用你的密码在本机加密，服务器无法读取。请输入「${profile?.username.orEmpty()}」的登录密码。",
            confirmText = "开启同步",
            onConfirm = {
                val username = profile?.username ?: return@MiDialog
                scope.launch {
                    accountManager.loginWithPassword(username, pwd)
                        .onSuccess { p ->
                            profile = p
                            sync.onSignedIn(p.username, pwd)
                        }
                        .onFailure { Toast.makeText(context, it.message ?: "验证失败", Toast.LENGTH_SHORT).show() }
                }
            }
        ) {
            MiTextField(value = pwd, onValueChange = { pwd = it }, placeholder = "登录密码", password = true)
        }
    }
    if (showBudget) {
        BudgetDialog(onDismiss = { showBudget = false }, onSaved = { budget = it })
    }
    if (confirmLogout) {
        MiDialog(
            onDismissRequest = { confirmLogout = false },
            title = "退出登录？",
            message = "本地账单不会被删除。",
            confirmText = "退出",
            confirmStyle = MiButtonStyle.Danger,
            onConfirm = {
                accountManager.logout()
                sync.onSignedOut()
                profile = null
            }
        )
    }
    if (showGuide) {
        MiDialog(
            onDismissRequest = { showGuide = false },
            title = "导出官方账单",
            message = "微信：我 → 服务 → 钱包 → 账单 → 右上角「…」→ 账单下载 → 用于个人对账。\n\n" +
                "支付宝：我的 → 账单 → 右上角「…」→ 开具交易流水证明 → 用于个人对账。\n\n" +
                "收到文件后，在微信中「用其他应用打开」选择智能记账，或在此页导入。",
            confirmText = "知道了",
            dismissText = null
        )
    }
    if (showPrivacy) {
        MiDialog(
            onDismissRequest = { showPrivacy = false },
            title = "隐私与数据",
            message = "账单优先保存在本机。登录后开启的云同步采用端到端加密：密钥由你的密码在手机上生成，服务器只保存密文，无法读取账目；图片只保存在本机。不接入任何广告或统计 SDK，通知与无障碍识别全程在本地完成，不读取密码。",
            confirmText = "好的",
            dismissText = null
        )
    }
    if (showAbout) {
        MiDialog(
            onDismissRequest = { showAbout = false },
            title = "智能记账 v${BuildConfig.VERSION_NAME}",
            message = "为小米 15 Pro 与 HyperOS 打造的自动记账工具。\n支付通知自动入账 · 官方账单导入去重 · 自定义分类 · 备注与图片 · 预算与统计",
            confirmText = "好的",
            dismissText = null
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AmountStyle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mi.colors.textPrimary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = Mi.colors.textSecondary)
    }
}
