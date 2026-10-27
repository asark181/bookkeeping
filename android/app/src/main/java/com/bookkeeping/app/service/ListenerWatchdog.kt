package com.bookkeeping.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
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
     * 针对已开启权限但在系统后台被临时解除绑定的情况，重新切换组件状态触发系统重新绑定
     */
    fun rebindServiceIfEnabled(context: Context) {
        if (!isNotificationListenerEnabled(context)) return
        val app = context.applicationContext
        val cn = ComponentName(app, PaymentNotificationListener::class.java)
        val pm = app.packageManager
        try {
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "已触发 NotificationListenerService 重新绑定")
        } catch (e: Exception) {
            Log.w(TAG, "重新绑定服务异常", e)
        }
    }

    fun tick(context: Context) {
        val app = context.applicationContext
        rebindServiceIfEnabled(app)
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
