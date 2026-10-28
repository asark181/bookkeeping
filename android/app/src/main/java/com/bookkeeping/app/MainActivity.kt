package com.bookkeeping.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bookkeeping.app.ui.common.SharedImport
import com.bookkeeping.app.ui.main.MainScreen
import com.bookkeeping.app.ui.theme.BookkeepingTheme
import com.bookkeeping.app.update.AppUpdate

class MainActivity : ComponentActivity() {

    /** 其他应用分享或「用其他应用打开」进来的账单，等待用户确认后才导入 */
    private var sharedImport by mutableStateOf<SharedImport?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 页面重建或从最近任务恢复时，不再重复处理启动 Intent
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) handleIncomingIntent(intent)
        setContent {
            BookkeepingTheme {
                MainScreen(
                    sharedImport = sharedImport,
                    onSharedImportHandled = { sharedImport = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从「安装未知应用」授权页返回后继续安装
        (application as BookkeepingApp).updates.onHostResume(this)
        // 回到前台时，距上次同步超过 5 分钟则同步一次
        (application as BookkeepingApp).sync.syncIfStale()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(AppUpdate.EXTRA_OPEN_UPDATE, false)) {
            intent.removeExtra(AppUpdate.EXTRA_OPEN_UPDATE)
            (application as BookkeepingApp).updates.checkFromUser()
            return
        }
        val action = intent.action
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return
        val uri: Uri? = intent.data ?: if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        sharedImport = when {
            uri != null -> SharedImport(uri = uri, text = null)
            !text.isNullOrBlank() && action == Intent.ACTION_SEND -> SharedImport(uri = null, text = text)
            else -> null
        }
    }
}
