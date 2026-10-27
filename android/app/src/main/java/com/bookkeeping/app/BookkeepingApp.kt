package com.bookkeeping.app

import android.app.Application
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.service.ListenerWatchdog

class BookkeepingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 预热 Room 数据库
        AppDatabase.getInstance(this)
        // 启动后台看门狗定时自检
        ListenerWatchdog.schedule(this)
    }
}
