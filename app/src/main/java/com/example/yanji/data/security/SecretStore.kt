package com.example.yanji.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AI 凭据（API Key）的秘密存储。
 *
 * 设计约束：
 * 1. **绝不进入 Room**。凭据与普通学习数据共享数据库文件，会一起进入 Android Auto Backup；
 *    用户的学习记录可以云备份，凭据不行。
 * 2. **绝不进入 SharedPreferences**。SharedPreferences 同样默认属于 Auto Backup 范围。
 * 3. 因此凭据落在 [Context.getNoBackupFilesDir]（系统保证永不参与备份/迁移），
 *    并用 Android Keystore 中的 AES-GCM 密钥加密后落盘。
 *
 * 实现不使用任何新增三方依赖：Keystore（API 23+）与框架 `Base64` 均可用，
 * 满足本项目 `minSdk 24` 的要求。
 */
interface SecretStore {

    /** 保存 API Key。传入空白字符串等价于清空。 */
    fun saveAiApiKey(value: String)

    /** 读取 API Key；不存在时返回空字符串。 */
    fun readAiApiKey(): String

    fun clearAiApiKey()

    /**
     * 处理数据库迁移阶段临时落盘的遗留明文 Key：加密后写入正式位置并删除明文文件。
     * @return 是否真的迁移了一份遗留凭据（用于日志/诊断）。
     */
    fun migrateLegacyApiKeyIfPresent(): Boolean

    companion object {
        /** 迁移期间明文 Key 的临时落点（仍在 no-backup 目录内，不会被云备份）。 */
        const val LEGACY_FILE_NAME = "yanji_legacy_ai_api_key"
    }
}

/**
 * 基于 Android Keystore + AES-GCM 的 [SecretStore] 实现。
 *
 * 存储格式：`v1:<base64(iv||ciphertext)>`。
 * 若设备 Keystore 不可用（极少数定制 ROM），降级为 `plain:<base64(utf8)>` 明文存储——
 * 依然位于 no-backup 目录，不会进入云备份；只在日志中留一条 WARN。
 */
class KeystoreSecretStore(context: Context) : SecretStore {

    private val appContext = context.applicationContext
    private val credentialFile = File(appContext.noBackupFilesDir, CREDENTIAL_FILE_NAME)
    private val legacyFile = File(appContext.noBackupFilesDir, SecretStore.LEGACY_FILE_NAME)

    override fun saveAiApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            clearAiApiKey()
            return
        }
        val payload = runCatching { encrypt(trimmed) }.getOrElse { error ->
            Log.w(TAG, "Keystore 加密不可用，降级为 no-backup 明文存储：${error.message}")
            PLAIN_PREFIX + Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        writeAtomically(credentialFile, payload)
    }

    override fun readAiApiKey(): String {
        if (!credentialFile.exists()) return ""
        val raw = runCatching { credentialFile.readText() }.getOrElse { return "" }
        if (raw.isBlank()) return ""
        return runCatching { decrypt(raw) }.getOrElse { error ->
            // 典型场景：用户在系统设置里清除过应用数据后 Keystore 密钥被重置。
            // 此时密文已无法还原，直接清掉，避免每次启动都抛异常。
            Log.w(TAG, "凭据解密失败，已清除本地副本：${error.message}")
            runCatching { credentialFile.delete() }
            ""
        }
    }

    override fun clearAiApiKey() {
        runCatching { credentialFile.delete() }
        runCatching { legacyFile.delete() }
        runCatching {
            val ks = keyStore()
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        }
    }

    override fun migrateLegacyApiKeyIfPresent(): Boolean {
        if (!legacyFile.exists()) return false
        val legacyValue = runCatching { legacyFile.readText().trim() }.getOrDefault("")
        legacyFile.delete()
        if (legacyValue.isEmpty()) return false
        saveAiApiKey(legacyValue)
        Log.i(TAG, "已将遗留明文 API Key 迁移至 Keystore 加密存储")
        return true
    }

    // ---------- internals ----------

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(cipherText, 0, packed, iv.size, cipherText.size)
        return VERSION_PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        if (stored.startsWith(PLAIN_PREFIX)) {
            val body = stored.substring(PLAIN_PREFIX.length)
            return String(Base64.decode(body, Base64.NO_WRAP), Charsets.UTF_8)
        }
        require(stored.startsWith(VERSION_PREFIX)) { "未知的凭据存储格式" }
        val packed = Base64.decode(stored.substring(VERSION_PREFIX.length), Base64.NO_WRAP)
        require(packed.size > IV_LENGTH) { "凭据内容损坏" }
        val iv = packed.copyOfRange(0, IV_LENGTH)
        val cipherText = packed.copyOfRange(IV_LENGTH, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
        // noBackupFilesDir 本身即位于 data/data/<pkg>/no_backup，不需要额外权限设置。
    }

    private companion object {
        const val TAG = "YanjiSecret"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "yanji_ai_credential_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val IV_LENGTH = 12
        const val VERSION_PREFIX = "v1:"
        const val PLAIN_PREFIX = "plain:"
        const val CREDENTIAL_FILE_NAME = "yanji_ai_credential"
    }
}
