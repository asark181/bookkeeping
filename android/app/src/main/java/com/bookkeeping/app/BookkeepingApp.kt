package com.bookkeeping.app

import android.app.Application
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.service.ListenerWatchdog
import com.bookkeeping.app.update.AppUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BookkeepingApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 跨页面的长任务（账单导入等）使用的主线程作用域，不随页面销毁而取消 */
    val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 应用内更新管理器 */
    val updates: AppUpdate by lazy { AppUpdate(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        // 预热 Room 数据库并补齐默认分类
        val db = AppDatabase.getInstance(this)
        appScope.launch { db.ensureDefaultCategories() }
        // 启动后台看门狗定时自检
        ListenerWatchdog.schedule(this)
        // 应用内更新：定期检查 + 启动时静默检查
        updates.start()
    }
}
