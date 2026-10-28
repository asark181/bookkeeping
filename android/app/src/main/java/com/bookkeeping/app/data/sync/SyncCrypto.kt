package com.bookkeeping.app.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 端到端加密：同步密钥由「密码 + 用户名」在本机派生，服务器只拿到密文。
 * 格式：base64(12 字节 IV + AES-256-GCM(gzip(JSON)))。
 */
object SyncCrypto {

    private const val ROUNDS = 200_000
    private const val IV_LEN = 12

    /** 同一账号在任何设备上用同一密码都派生出同一把密钥 */
    fun deriveKey(password: String, username: String): ByteArray {
        val salt = "bookkeeping-sync-v1:${username.trim().lowercase()}".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password.toCharArray(), salt, ROUNDS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun encrypt(plain: ByteArray, key: ByteArray): String {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + cipher.doFinal(gzip(plain)), Base64.NO_WRAP)
    }

    /** 密钥不对或数据被篡改时抛 AEADBadTagException */
    fun decrypt(blob: String, key: ByteArray): ByteArray {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw, 0, IV_LEN))
        return gunzip(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN))
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}

/** 同步密钥保管：用 Android Keystore 中不可导出的密钥包裹后存入私有偏好 */
object SyncKeyVault {

    private const val ALIAS = "bookkeeping_sync_wrap"
    private const val PREFS = "bookkeeping_sync"
    private const val KEY_WRAPPED = "wrapped_key"

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun save(context: Context, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val wrapped = cipher.iv + cipher.doFinal(key)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): ByteArray? = runCatching {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WRAPPED, null) ?: return null
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, raw, 0, 12))
        cipher.doFinal(raw, 12, raw.size - 12)
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_WRAPPED).apply()
    }
}
