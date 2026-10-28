package com.bookkeeping.app.data.sync

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bookkeeping.app.BookkeepingApp
import com.bookkeeping.app.BuildConfig
import com.bookkeeping.app.data.auth.AccountManager
import com.bookkeeping.app.data.db.AppDatabase
import com.bookkeeping.app.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

sealed interface SyncState {
    /** 未登录 */
    data object SignedOut : SyncState
    /** 已登录但本机没有同步密钥（旧版本登录的账号），需要输入一次密码 */
    data object NeedsPassword : SyncState
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Done(val at: Long) : SyncState
    data class Failed(val reason: String) : SyncState
}

private class SyncHttpException(val code: Int, message: String) : IOException(message)

/**
 * 端到端加密的多设备同步：拉取云端快照 → 解密 → 与本机按 uid 合并 → 写回本机 → 加密上传（带版本号，冲突时重试）。
 */
class SyncManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val serverUrl: String = BuildConfig.SERVER_URL
) {
    private val db = AppDatabase.getInstance(context)
    private val account = AccountManager(context)
    private val prefs = AppPrefs(context)
    private val meta = context.getSharedPreferences("bookkeeping_sync_meta", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val gate = Mutex()

    /** 写回云端数据期间忽略数据库变更通知，避免同步自己触发自己 */
    @Volatile private var applyingRemote = false

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val lastSyncAt: Long get() = meta.getLong(KEY_LAST_SYNC, 0L)

    fun start() {
        refreshState()
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("transactions", "categories")) {
            override fun onInvalidated(tables: Set<String>) {
                if (!applyingRemote) requestSoon()
            }
        })
        schedulePeriodic(context)
        syncIfStale(0)
    }

    fun refreshState() {
        _state.value = when {
            !account.isLoggedIn() -> SyncState.SignedOut
            SyncKeyVault.load(context) == null -> SyncState.NeedsPassword
            lastSyncAt > 0 -> SyncState.Done(lastSyncAt)
            else -> SyncState.Idle
        }
    }

    /** 登录/注册成功后保存同步密钥并立即同步 */
    suspend fun onSignedIn(username: String, password: String) {
        val key = withContext(Dispatchers.Default) { SyncCrypto.deriveKey(password, username) }
        SyncKeyVault.save(context, key)
        meta.edit().putLong(KEY_LAST_SYNC, 0L).putInt(KEY_VERSION, 0).apply()
        refreshState()
        scope.launch { syncNow() }
    }

    fun onSignedOut() {
        SyncKeyVault.clear(context)
        meta.edit().clear().apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_SOON)
        _state.value = SyncState.SignedOut
    }

    /** 本机数据变化后约 30 秒同步（连续修改会顺延） */
    fun requestSoon() {
        if (!account.isLoggedIn()) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_SOON, ExistingWorkPolicy.REPLACE, req)
    }

    /** 回到前台时：距上次同步超过 [maxAgeMs] 才同步 */
    fun syncIfStale(maxAgeMs: Long = 5 * 60 * 1000L) {
        if (System.currentTimeMillis() - lastSyncAt >= maxAgeMs) scope.launch { syncNow() }
    }

    suspend fun syncNow(): SyncState = withContext(Dispatchers.IO) {
        gate.withLock {
            if (!account.isLoggedIn()) return@withLock SyncState.SignedOut.also { _state.value = it }
            val key = SyncKeyVault.load(context) ?: return@withLock SyncState.NeedsPassword.also { _state.value = it }
            _state.value = SyncState.Syncing
            val result = try {
                var attempt = 0
                while (true) {
                    try {
                        runOnce(key)
                        break
                    } catch (e: SyncHttpException) {
                        // 409：其他设备刚上传过，重新拉取合并
                        if (e.code != 409 || ++attempt >= 3) throw e
                    }
                }
                val now = System.currentTimeMillis()
                meta.edit().putLong(KEY_LAST_SYNC, now).apply()
                SyncState.Done(now)
            } catch (e: javax.crypto.AEADBadTagException) {
                SyncState.Failed("云端数据无法解密，请重新登录")
            } catch (e: SyncHttpException) {
                if (e.code == 401) SyncState.Failed("登录已失效，请重新登录") else SyncState.Failed(e.message ?: "同步失败")
            } catch (e: IOException) {
                SyncState.Failed("网络不可用，稍后自动重试")
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                SyncState.Failed("同步出错：${e.message ?: e.javaClass.simpleName}")
            }
            _state.value = result
            result
        }
    }

    private suspend fun runOnce(key: ByteArray) {
        val token = account.getToken()
        val remote = request("GET", token, null)
        val version = remote.optInt("version", 0)
        val blob = remote.optString("blob", "").takeIf { remote.has("blob") && !remote.isNull("blob") && it.isNotBlank() }
        val snapshot = blob?.let { json.decodeFromString(SyncSnapshot.serializer(), String(SyncCrypto.decrypt(it, key), Charsets.UTF_8)) }
            ?: SyncSnapshot()

        val txDao = db.transactionDao()
        val catDao = db.categoryDao()
        val localRecords = txDao.getAllIncludingDeleted()
        val txMerge = mergeTransactions(localRecords.map { it.toSync() }, snapshot.transactions)

        val localCatAt = prefs.categoriesUpdatedAt
        val useRemoteCategories = snapshot.categories.isNotEmpty() && snapshot.categoriesUpdatedAt > localCatAt
        val useRemoteBudget = snapshot.budgetUpdatedAt > prefs.budgetUpdatedAt

        if (txMerge.toApply.isNotEmpty() || useRemoteCategories) {
            applyingRemote = true
            try {
                val byUid = localRecords.associateBy { it.uid }
                db.withTransaction {
                    for (r in txMerge.toApply) {
                        val local = byUid[r.uid]
                        if (local != null) txDao.update(r.toRecord(local)) else txDao.insert(r.toRecord(null))
                    }
                    if (useRemoteCategories) catDao.replaceAll(snapshot.categories.map { it.toEntity() })
                }
            } finally {
                applyingRemote = false
            }
            if (useRemoteCategories) prefs.categoriesUpdatedAt = snapshot.categoriesUpdatedAt
        }
        if (useRemoteBudget) prefs.applySyncedBudget(snapshot.budgetCents, snapshot.budgetUpdatedAt)

        val categoriesChanged = !useRemoteCategories && (localCatAt > snapshot.categoriesUpdatedAt || snapshot.categories.isEmpty())
        val budgetChanged = !useRemoteBudget && prefs.budgetUpdatedAt > snapshot.budgetUpdatedAt
        if (blob != null && !txMerge.needsUpload && !categoriesChanged && !budgetChanged) {
            meta.edit().putInt(KEY_VERSION, version).apply()
            return
        }

        val outgoing = SyncSnapshot(
            transactions = txMerge.merged,
            categoriesUpdatedAt = if (useRemoteCategories) snapshot.categoriesUpdatedAt else localCatAt,
            categories = catDao.getAllOnce().map { it.toSync() },
            budgetUpdatedAt = prefs.budgetUpdatedAt,
            budgetCents = prefs.monthlyBudgetCents
        )
        val cipher = SyncCrypto.encrypt(json.encodeToString(SyncSnapshot.serializer(), outgoing).toByteArray(Charsets.UTF_8), key)
        val put = request("PUT", token, JSONObject().put("base_version", version).put("blob", cipher).toString())
        meta.edit().putInt(KEY_VERSION, put.optInt("version", version + 1)).apply()
    }

    private fun request(method: String, token: String, body: String?): JSONObject {
        val conn = (URL(serverUrl.trimEnd('/') + "/api/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val obj = runCatching { JSONObject(text) }.getOrNull()
            if (code !in 200..299) throw SyncHttpException(code, obj?.optString("detail")?.takeIf { it.isNotBlank() } ?: "同步失败（HTTP $code）")
            return obj ?: throw IOException("服务器响应格式异常")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_VERSION = "remote_version"
        private const val WORK_SOON = "sync-soon"
        private const val WORK_PERIODIC = "sync-periodic"

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as BookkeepingApp).sync
        return when (val s = sync.syncNow()) {
            is SyncState.Failed -> if (s.reason.startsWith("网络")) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}
