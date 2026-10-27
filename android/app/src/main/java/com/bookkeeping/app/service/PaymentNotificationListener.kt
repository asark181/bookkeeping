package com.bookkeeping.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.parser.DedupHelper
import com.bookkeeping.app.parser.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListenerService connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationListenerService disconnected! Attempting watchdog wake...")
        ListenerWatchdog.schedule(applicationContext)
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

        Log.d(TAG, "Captured notification from $pkg: title='$title', content='$content'")

        val parsed = NotificationParser.parse(pkg, title, content)
        val parsedStatus = when {
            parsed != null && parsed.type == "EXPENSE" -> "PARSED_EXPENSE"
            parsed != null -> "PARSED_INCOME"
            content.contains("[转账]") || content.contains("[微信红包]") || content.contains("红包") -> "无金额(需无障碍提取)"
            else -> "UNMATCHED"
        }

        if (parsed != null) {
            Log.i(TAG, "Successfully parsed: amount=${parsed.amountCents}分, type=${parsed.type}, channel=${parsed.channel}, merchant=${parsed.merchant}")
        } else {
            Log.w(TAG, "Notification from $pkg not matched by rules: title='$title', content='$content'")
        }

        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.transactionDao()

        serviceScope.launch {
            val now = System.currentTimeMillis()

            // 1. 记录原始抓取日志，供真机透明排查
            try {
                dao.insertNotificationLog(
                    com.bookkeeping.app.data.db.entity.CapturedNotification(
                        packageName = pkg,
                        title = title,
                        content = content,
                        parsedStatus = parsedStatus,
                        parsedAmountCents = parsed?.amountCents,
                        timestamp = sbn.postTime.takeIf { it > 0 } ?: now
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to insert notification log", e)
            }

            if (parsed == null) return@launch

            // 2. 去重与入账
            val duplicate = DedupHelper.findDuplicate(
                dao = dao,
                amountCents = parsed.amountCents,
                type = parsed.type,
                merchant = parsed.merchant,
                channel = parsed.channel,
                now = now
            )

            if (duplicate != null) {
                Log.i(TAG, "Found duplicate transaction #${duplicate.id}, merging...")
                DedupHelper.mergeIfDuplicate(dao, duplicate, parsed.channel, parsed.merchant)
            } else {
                val record = TransactionRecord(
                    amountCents = parsed.amountCents,
                    type = parsed.type,
                    channel = parsed.channel,
                    merchant = parsed.merchant,
                    category = guessCategory(parsed.merchant, parsed.channel),
                    timestamp = sbn.postTime.takeIf { it > 0 } ?: now,
                    rawText = parsed.rawText,
                    confidence = parsed.confidence.name
                )
                val newId = dao.insert(record)
                Log.i(TAG, "Inserted new transaction record #$newId")
            }
        }
    }

    private fun guessCategory(merchant: String?, channel: String = ""): String {
        val combined = "$merchant $channel"
        return when {
            combined.contains("美团") || combined.contains("饿了么") ||
            combined.contains("麦当劳") || combined.contains("肯德基") ||
            combined.contains("咖啡") || combined.contains("便利店") ||
            combined.contains("超市") || combined.contains("餐饮") ||
            combined.contains("饭") || combined.contains("面") -> "餐饮美食"

            combined.contains("滴滴") || combined.contains("铁路") ||
            combined.contains("12306") || combined.contains("地铁") ||
            combined.contains("公交") || combined.contains("加油") ||
            combined.contains("出行") -> "交通出行"

            combined.contains("淘宝") || combined.contains("天猫") ||
            combined.contains("京东") || combined.contains("拼多多") ||
            combined.contains("抖音") || combined.contains("快手") ||
            combined.contains("唯品会") -> "网络购物"

            combined.contains("银联") || combined.contains("云闪付") -> "刷卡消费"

            else -> "日常消费"
        }
    }

    companion object {
        private const val TAG = "PaymentNLS"
    }
}
