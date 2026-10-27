package com.bookkeeping.app

import android.app.Application
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.service.ListenerWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BookkeepingApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 预热 Room 数据库并补齐默认分类
        val db = AppDatabase.getInstance(this)
        appScope.launch { db.ensureDefaultCategories() }
        // 启动后台看门狗定时自检
        ListenerWatchdog.schedule(this)
    }
}
