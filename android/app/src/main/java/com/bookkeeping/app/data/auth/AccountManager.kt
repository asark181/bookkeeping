package com.bookkeeping.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.bookkeeping.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class UserProfile(
    val id: Long,
    val username: String,
    val token: String,
    val ledgerName: String = "个人账本",
    val ledgerSpace: String
)

class AccountManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("bookkeeping_account", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = !prefs.getString("token", null).isNullOrBlank()

    fun getUsername(): String = prefs.getString("username", "") ?: ""

    fun getToken(): String = prefs.getString("token", "") ?: ""

    fun getLedgerName(): String = prefs.getString("ledger_name", "个人账本") ?: "个人账本"

    fun getProfile(): UserProfile? {
        val token = prefs.getString("token", null) ?: return null
        val username = prefs.getString("username", "") ?: ""
        val id = prefs.getLong("user_id", 0L)
        val ledgerName = prefs.getString("ledger_name", "个人账本") ?: "个人账本"
        val ledgerSpace = prefs.getString("ledger_space", "") ?: ""
        return UserProfile(id, username, token, ledgerName, ledgerSpace)
    }

    fun saveLogin(id: Long, username: String, token: String, ledgerName: String, ledgerSpace: String) {
        prefs.edit()
            .putLong("user_id", id)
            .putString("username", username)
            .putString("token", token)
            .putString("ledger_name", ledgerName)
            .putString("ledger_space", ledgerSpace)
            .apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    suspend fun register(username: String, password: String): Result<UserProfile> =
        authenticate("/api/auth/register", username, password, "注册失败")

    suspend fun loginWithPassword(username: String, password: String): Result<UserProfile> =
        authenticate("/api/auth/login-password", username, password, "登录失败")

    private suspend fun authenticate(path: String, username: String, password: String, fallback: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("username", username).put("password", password).toString()
            val response = try {
                postJson("${BuildConfig.SERVER_URL}$path", body)
            } catch (e: IOException) {
                return@withContext Result.failure(Exception("网络连接失败，请检查网络后重试"))
            }
            val json = response.json
            if (response.status in 200..299 && json?.optString("status") == "ok") {
                runCatching {
                    val user = json.getJSONObject("user")
                    val ledger = json.optJSONObject("ledger")
                    val profile = UserProfile(
                        id = user.getLong("id"),
                        username = user.optString("username", username),
                        token = json.getString("token"),
                        ledgerName = ledger?.optString("name", "个人账本") ?: "个人账本",
                        ledgerSpace = ledger?.optString("space", "") ?: ""
                    )
                    saveLogin(profile.id, profile.username, profile.token, profile.ledgerName, profile.ledgerSpace)
                    profile
                }.recoverCatching { throw Exception("$fallback：服务器响应格式异常") }
            } else {
                Result.failure(Exception(errorMessage(response, fallback)))
            }
        }

    private class HttpResponse(val status: Int, val body: String) {
        /** 非 JSON 响应（如网关 502 页面、纯文本 500）返回 null */
        val json: JSONObject? = runCatching { JSONObject(body) }.getOrNull()
    }

    /** 服务端统一返回 {"detail": "中文提示"}；兼容 FastAPI 参数校验的列表格式与非 JSON 响应 */
    private fun errorMessage(response: HttpResponse, fallback: String): String {
        val detail = response.json?.opt("detail")
        val text = when (detail) {
            is String -> detail
            is JSONArray -> (0 until detail.length()).mapNotNull { detail.optJSONObject(it)?.optString("msg") }.firstOrNull()
            else -> null
        }
        return when {
            !text.isNullOrBlank() -> text
            response.status == 429 -> "操作太频繁，请稍后再试"
            response.status >= 500 -> "服务器开小差了（HTTP ${response.status}），请稍后重试"
            else -> "$fallback（HTTP ${response.status}）"
        }
    }

    private fun postJson(urlStr: String, jsonBody: String): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
            return HttpResponse(status, text)
        } finally {
            conn.disconnect()
        }
    }
}
