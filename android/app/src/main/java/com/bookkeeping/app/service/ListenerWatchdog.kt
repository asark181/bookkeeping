package com.bookkeeping.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

object ListenerWatchdog {

    private const val TAG = "ListenerWatchdog"
    const val ACTION = "com.bookkeeping.action.NLS_WATCHDOG"
    private const val REQ_CODE = 8102
    private const val INTERVAL_MS = 25 * 60 * 1000L // 25 分钟一次

    fun schedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(app)
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        try {
            am.cancel(pi)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "已调度下一次看门狗自检: ${INTERVAL_MS / 1000}s 后")
        } catch (e: Exception) {
            Log.w(TAG, "调度看门狗失败", e)
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return try {
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        } catch (_: Exception) {
            val cn = ComponentName(context, PaymentNotificationListener::class.java)
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            flat != null && (flat.contains(cn.flattenToString()) || flat.contains(cn.flattenToShortString()))
        }
    }

    /**
     * 只在监听确实断开时（已授权但服务未与系统绑定）请求系统重新绑定。
     * 不再通过 disable/enable 组件强制重启：那样无论是否正常都会断开监听，期间的通知会丢失。
     */
    fun rebindIfDisconnected(context: Context): Boolean {
        val app = context.applicationContext
        if (!isNotificationListenerEnabled(app)) return false
        if (PaymentNotificationListener.isConnected) return false
        return try {
            NotificationListenerService.requestRebind(ComponentName(app, PaymentNotificationListener::class.java))
            Log.i(TAG, "监听未连接，已请求 NotificationListenerService 重新绑定")
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind 异常", e)
            false
        }
    }

    fun tick(context: Context) {
        val app = context.applicationContext
        rebindIfDisconnected(app)
        schedule(app)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ListenerWakeReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
    }
}

class ListenerWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d("ListenerWakeReceiver", "收到唤醒广播 action=$action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT,
            ListenerWatchdog.ACTION -> {
                ListenerWatchdog.tick(context)
            }
        }
    }
}
