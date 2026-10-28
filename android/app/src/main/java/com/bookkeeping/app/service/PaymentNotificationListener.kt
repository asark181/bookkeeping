package com.bookkeeping.app.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bookkeeping.app.data.category.CategoryResolver
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.CapturedNotification
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.parser.DedupHelper
import com.bookkeeping.app.parser.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "NotificationListenerService connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w(TAG, "NotificationListenerService disconnected, requesting rebind.")
        try {
            NotificationListenerService.requestRebind(ComponentName(applicationContext, PaymentNotificationListener::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind failed", e)
        }
        ListenerWatchdog.schedule(applicationContext)
    }

    override fun onDestroy() {
        isConnected = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        if (pkg !in NotificationParser.SUPPORTED_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val content = if (bigText.isNotBlank()) bigText else text
        if (content.isBlank()) return

        val parsed = NotificationParser.parse(pkg, title, content)
        val suspected = parsed == null && NotificationParser.isSuspectedPayment(pkg, title, content)

        // 普通聊天等无关通知：不落库、不打印正文
        if (parsed == null && !suspected) return

        val parsedStatus = when {
            parsed != null && parsed.type == "EXPENSE" -> "PARSED_EXPENSE"
            parsed != null -> "PARSED_INCOME"
            else -> "无金额(需无障碍提取)"
        }
        // 微信聊天里的「[转账]」「[微信红包]」占位：标题是联系人名，不记录
        val logTitle = if (pkg == NotificationParser.PKG_WECHAT && !NotificationParser.isWeChatServiceTitle(title)) {
            "微信聊天"
        } else {
            title
        }

        if (parsed != null) {
            Log.i(TAG, "Parsed: amount=${parsed.amountCents}分, type=${parsed.type}, channel=${parsed.channel}")
        }

        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.transactionDao()
        val postTime = sbn.postTime

        serviceScope.launch {
            val now = System.currentTimeMillis()

            // 1. 只记录解析成功或疑似支付的通知，并只保留最近 MAX_LOGS 条
            try {
                dao.insertNotificationLog(
                    CapturedNotification(
                        packageName = pkg,
                        title = logTitle,
                        content = content,
                        parsedStatus = parsedStatus,
                        parsedAmountCents = parsed?.amountCents,
                        timestamp = postTime.takeIf { it > 0 } ?: now
                    )
                )
                dao.trimNotificationLogs(MAX_LOGS)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to insert notification log", e)
            }

            if (parsed == null) return@launch

            // 2. 去重与入账（全局锁内串行「查重 + 插入」）
            val category = CategoryResolver.resolve(dao, parsed.type, parsed.merchant, parsed.channel)
            val record = TransactionRecord(
                amountCents = parsed.amountCents,
                type = parsed.type,
                channel = parsed.channel,
                merchant = parsed.merchant,
                category = category,
                timestamp = postTime.takeIf { it > 0 } ?: now,
                rawText = parsed.rawText,
                confidence = parsed.confidence.name
            )
            when (val result = DedupHelper.insertIfNotDuplicate(dao, record, now)) {
                is DedupHelper.DedupResult.Duplicate -> Log.i(TAG, "Found duplicate transaction #${result.existing.id}, merged.")
                is DedupHelper.DedupResult.Inserted -> Log.i(TAG, "Inserted new transaction record #${result.id}")
            }
        }
    }

    companion object {
        private const val TAG = "PaymentNLS"
        const val MAX_LOGS = 200

        /** 监听服务当前是否与系统保持绑定，供看门狗判断是否需要 requestRebind */
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
