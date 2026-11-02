package com.bookkeeping.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bookkeeping.app.data.category.CategoryResolver
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.CapturedNotification
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.parser.AccessibilityPaymentDetector
import com.bookkeeping.app.parser.DedupHelper
import com.bookkeeping.app.parser.DedupWindow
import com.bookkeeping.app.parser.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 无障碍屏幕识别辅助服务
 * 补足：用户在微信中主动转账、发红包或前台扫码时，微信不发送系统通知的场景。
 *
 * 只在转账/红包/支付的结果页识别（见 [AccessibilityPaymentDetector]），聊天页不识别；
 * 同一笔结果按「金额 + 对象 + 结果页文案」做持久化去重（[DEDUP_WINDOW_MS]）。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 最近一次窗口切换的 Activity 类名 */
    @Volatile
    private var currentActivity: String? = null

    private val dedup: DedupWindow by lazy {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initial = prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
        DedupWindow(DEDUP_WINDOW_MS, initial) { snapshot ->
            val editor = prefs.edit().clear()
            snapshot.forEach { (k, v) -> editor.putLong(k, v) }
            editor.apply()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != NotificationParser.PKG_WECHAT) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val cls = event.className?.toString()
                // 只记录 Activity（过滤 android.widget.* 等弹层控件）
                if (cls != null && cls.startsWith("com.tencent.mm")) currentActivity = cls
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> Unit
            else -> return
        }

        val activity = currentActivity
        if (AccessibilityPaymentDetector.isChatActivity(activity)) return

        val now = System.currentTimeMillis()

        val root = rootInActiveWindow ?: return
        try {
            val texts = collectTexts(root)
            val detection = AccessibilityPaymentDetector.detect(activity, texts, now) ?: return
            if (!dedup.tryAcquire(detection.dedupKey, now)) return
            saveTransaction(detection, now)
        } catch (e: Exception) {
            Log.w(TAG, "inspect window failed", e)
        }
    }

    /** 广度优先收集节点文本，限制深度与节点数，避免大页面递归过深 */
    @Suppress("DEPRECATION")
    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            visited++
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            if (!text.isNullOrBlank()) out.add(text)
            if (!desc.isNullOrBlank() && desc != text) out.add(desc)

            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    if (visited + queue.size >= MAX_NODES) break
                    val child = node.getChild(i) ?: continue
                    queue.add(child to depth + 1)
                }
            }
            if (node !== root) node.recycle()
            if (visited >= MAX_NODES) break
        }
        queue.forEach { (n, _) -> if (n !== root) n.recycle() }
        return out
    }

    private fun saveTransaction(d: AccessibilityPaymentDetector.Detection, now: Long) {
        val dao = AppDatabase.getInstance(applicationContext).transactionDao()
        val merchant = d.counterparty ?: when (d.kind) {
            AccessibilityPaymentDetector.KIND_TRANSFER -> if (d.type == "INCOME") "收到转账" else "微信转账"
            AccessibilityPaymentDetector.KIND_RED_PACKET -> if (d.type == "INCOME") "收到红包" else "微信红包"
            else -> "微信支付"
        }
        val hint = when (d.kind) {
            AccessibilityPaymentDetector.KIND_TRANSFER -> "转账"
            AccessibilityPaymentDetector.KIND_RED_PACKET -> "红包"
            else -> ""
        }
        val rawText = "无障碍结果页识别: ${d.resultText} ¥${d.amountCents / 100.0} ${d.counterparty.orEmpty()}".trim()

        serviceScope.launch {
            try {
                val category = CategoryResolver.resolve(dao, d.type, merchant, hint)
                val record = TransactionRecord(
                    amountCents = d.amountCents,
                    type = d.type,
                    channel = "微信支付",
                    merchant = merchant,
                    category = category,
                    timestamp = now,
                    rawText = rawText,
                    confidence = "HIGH"
                )
                when (val r = DedupHelper.insertIfNotDuplicate(dao, record, now)) {
                    is DedupHelper.DedupResult.Duplicate -> Log.i(TAG, "无障碍识别到已有重复流水 #${r.existing.id}，跳过新增")
                    is DedupHelper.DedupResult.Inserted -> {
                        Log.i(TAG, "无障碍录入微信流水 #${r.id}: ${d.type} ${d.amountCents} 分")
                        dao.insertNotificationLog(
                            CapturedNotification(
                                packageName = NotificationParser.PKG_WECHAT,
                                title = "无障碍识别",
                                content = rawText,
                                parsedStatus = "PARSED_${d.type}",
                                parsedAmountCents = d.amountCents,
                                timestamp = now
                            )
                        )
                        dao.trimNotificationLogs(PaymentNotificationListener.MAX_LOGS)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "save accessibility transaction failed", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentAccessibility"
        private const val PREFS_NAME = "accessibility_payment_dedup"
        private const val DEDUP_WINDOW_MS = 2 * 60 * 60 * 1000L
        private const val MAX_DEPTH = 25
        private const val MAX_NODES = 400
    }
}
