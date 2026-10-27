package com.bookkeeping.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.db.entity.CapturedNotification
import com.bookkeeping.app.data.db.entity.TransactionRecord
import com.bookkeeping.app.parser.DedupHelper
import com.bookkeeping.app.parser.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 无障碍屏幕识别辅助服务
 * 专门用于补足：用户在微信聊天中主动发起转账、发红包或前台扫码时，
 * 微信不发送系统通知栏消息的场景。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 内存去重防抖，避免同一个界面节点在滑动过程中高频重复入账
    private val recentHits = ConcurrentHashMap<String, Long>()
    private var lastScanLogTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != NotificationParser.PKG_WECHAT && pkg != NotificationParser.PKG_ALIPAY) return

        val root = rootInActiveWindow ?: event.source ?: return
        try {
            if (pkg == NotificationParser.PKG_WECHAT) {
                inspectWeChat(root)
            }
        } catch (e: Exception) {
            Log.w(TAG, "inspect window failed", e)
        }
    }

    private fun inspectWeChat(root: AccessibilityNodeInfo) {
        val texts = mutableListOf<String>()
        collectAllTexts(root, texts)

        val fullSnippet = texts.joinToString(" ")
        if (fullSnippet.isBlank()) return

        val now = System.currentTimeMillis()
        recentHits.entries.removeIf { now - it.value > 30_000L }

        // 场景 1：微信聊天中的转账气泡（涵盖：待朋友确认收钱 / 请收款 / 已收钱 / 微信转账）
        val isTransfer = fullSnippet.contains("微信转账") ||
                (fullSnippet.contains("转账") && (fullSnippet.contains("请收款") || fullSnippet.contains("待朋友确认收钱") || fullSnippet.contains("已存入零钱") || fullSnippet.contains("已收钱")))

        if (isTransfer) {
            val amountRegex = Regex("[￥¥]([\\d.]+)")
            val match = amountRegex.find(fullSnippet)
            if (match != null) {
                val amountStr = match.groupValues[1]
                val cents = NotificationParser.toCents(amountStr)
                if (cents != null && cents > 0) {
                    val isExpense = fullSnippet.contains("待朋友确认收钱") || (!fullSnippet.contains("请收款") && !fullSnippet.contains("已收钱"))
                    val type = if (isExpense) "EXPENSE" else "INCOME"
                    val dedupKey = "WX_TRANSFER_${cents}_${type}_${now / 20_000L}" // 20秒桶去重

                    if (recentHits.putIfAbsent(dedupKey, now) == null) {
                        saveTransaction(
                            amountCents = cents,
                            type = type,
                            channel = "微信支付",
                            merchant = if (isExpense) "微信转账" else "收到转账",
                            category = "人情往来",
                            rawText = "无障碍节点识别: $fullSnippet"
                        )
                        logToDb("com.tencent.mm", "无障碍识别转账", fullSnippet, "PARSED_$type", cents)
                        return
                    }
                }
            }
        }

        // 场景 2：微信发红包完成 / 红包详情页 / 弹窗 (涵盖：恭喜发财 / 微信红包 / 发红包 / 零钱支付)
        val isRedPacket = fullSnippet.contains("微信红包") || fullSnippet.contains("发红包") || fullSnippet.contains("恭喜发财")
        if (isRedPacket) {
            val amountRegex = Regex("([\\d.]+)元|[￥¥]([\\d.]+)")
            val match = amountRegex.find(fullSnippet)
            if (match != null) {
                val amountStr = if (match.groupValues[1].isNotBlank()) match.groupValues[1] else match.groupValues[2]
                val cents = NotificationParser.toCents(amountStr)
                if (cents != null && cents > 0) {
                    val dedupKey = "WX_REDPACKET_${cents}_${now / 20_000L}"
                    if (recentHits.putIfAbsent(dedupKey, now) == null) {
                        saveTransaction(
                            amountCents = cents,
                            type = "EXPENSE",
                            channel = "微信支付",
                            merchant = "微信红包",
                            category = "休闲娱乐",
                            rawText = "无障碍节点识别: $fullSnippet"
                        )
                        logToDb("com.tencent.mm", "无障碍识别红包", fullSnippet, "PARSED_EXPENSE", cents)
                        return
                    }
                }
            }
        }

        // 偶发排查日志：当检测到微信活跃窗口但没有金额时，限制每 6 秒输出一条到排查面板
        if (now - lastScanLogTime > 6_000L && (fullSnippet.contains("微信") || fullSnippet.contains("转账") || fullSnippet.contains("红包"))) {
            lastScanLogTime = now
            logToDb("com.tencent.mm", "无障碍扫描微信", fullSnippet.take(80), "ACCESSIBILITY_SCAN", null)
        }
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrBlank()) outList.add(text)
        if (!desc.isNullOrBlank() && desc != text) outList.add(desc)

        for (i in 0 until node.childCount) {
            collectAllTexts(node.getChild(i), outList)
        }
    }

    private fun saveTransaction(
        amountCents: Long,
        type: String,
        channel: String,
        merchant: String,
        category: String,
        rawText: String
    ) {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.transactionDao()
        val now = System.currentTimeMillis()

        serviceScope.launch {
            val duplicate = DedupHelper.findDuplicate(
                dao = dao,
                amountCents = amountCents,
                type = type,
                merchant = merchant,
                channel = channel,
                now = now
            )

            if (duplicate != null) {
                Log.i(TAG, "无障碍识别到已有重复流水 #${duplicate.id}，跳过新增")
            } else {
                val record = TransactionRecord(
                    amountCents = amountCents,
                    type = type,
                    channel = channel,
                    merchant = merchant,
                    category = category,
                    timestamp = now,
                    rawText = rawText,
                    confidence = "HIGH"
                )
                val id = dao.insert(record)
                Log.i(TAG, "无障碍成功录入微信流水 #$id: $merchant $amountCents 分")
            }
        }
    }

    private fun logToDb(pkg: String, title: String, content: String, status: String, cents: Long?) {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.transactionDao()
        serviceScope.launch {
            try {
                dao.insertNotificationLog(
                    CapturedNotification(
                        packageName = pkg,
                        title = title,
                        content = content,
                        parsedStatus = status,
                        parsedAmountCents = cents,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "failed to insert scan log", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    companion object {
        private const val TAG = "PaymentAccessibility"
    }
}
