package com.bookkeeping.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.bookkeeping.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
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

    suspend fun register(username: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val serverUrl = "${BuildConfig.SERVER_URL}/api/auth/register"
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val responseStr = postJson(serverUrl, json.toString())
            val resObj = JSONObject(responseStr)
            if (resObj.optString("status") == "ok") {
                val token = resObj.getString("token")
                val userObj = resObj.getJSONObject("user")
                val uid = userObj.getLong("id")
                val ledgerObj = resObj.getJSONObject("ledger")
                val ledgerName = ledgerObj.optString("name", "个人账本")
                val ledgerSpace = ledgerObj.optString("space", "")

                saveLogin(uid, username, token, ledgerName, ledgerSpace)
                Result.success(UserProfile(uid, username, token, ledgerName, ledgerSpace))
            } else {
                Result.failure(Exception(resObj.optString("detail", "注册失败")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithPassword(username: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val serverUrl = "${BuildConfig.SERVER_URL}/api/auth/login-password"
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val responseStr = postJson(serverUrl, json.toString())
            val resObj = JSONObject(responseStr)
            if (resObj.optString("status") == "ok") {
                val token = resObj.getString("token")
                val userObj = resObj.getJSONObject("user")
                val uid = userObj.getLong("id")
                val ledgerObj = resObj.getJSONObject("ledger")
                val ledgerName = ledgerObj.optString("name", "个人账本")
                val ledgerSpace = ledgerObj.optString("space", "")

                saveLogin(uid, username, token, ledgerName, ledgerSpace)
                Result.success(UserProfile(uid, username, token, ledgerName, ledgerSpace))
            } else {
                Result.failure(Exception(resObj.optString("detail", "登录失败")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postJson(urlStr: String, jsonBody: String): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody) }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
    }
}
