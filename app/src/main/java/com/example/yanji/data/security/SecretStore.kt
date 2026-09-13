package com.example.yanji.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Result of a credential write. A failure always means no plaintext was written. */
sealed interface SecretWriteResult {
    data object Saved : SecretWriteResult
    data object Cleared : SecretWriteResult
    data class Failure(val cause: Throwable) : SecretWriteResult
}

sealed interface LegacySecretMigrationResult {
    data object NothingToMigrate : LegacySecretMigrationResult
    data object Migrated : LegacySecretMigrationResult
    data class Failure(val cause: Throwable) : LegacySecretMigrationResult
}

/**
 * AI credentials never enter Room, SharedPreferences, Auto Backup, or a reversible fallback.
 * Implementations must fail closed when encryption or durable file replacement is unavailable.
 */
interface SecretStore {
    fun saveAiApiKey(value: String): SecretWriteResult
    fun readAiApiKey(): String
    fun clearAiApiKey(): SecretWriteResult
    fun migrateLegacyApiKeyIfPresent(): LegacySecretMigrationResult

    companion object {
        /** Temporary v7 -> v8 hand-off. Deleted only after an encrypted write succeeds. */
        const val LEGACY_FILE_NAME = "yanji_legacy_ai_api_key"
    }
}

/** Small seam that keeps file/migration behaviour testable without an Android Keystore. */
internal interface SecretPayloadCrypto {
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
    fun decodeLegacyPlain(stored: String): String
    fun clearKey()
}

/**
 * Android Keystore + AES-GCM credential storage.
 *
 * Current format is `v1:<base64(iv||ciphertext)>`. Historical `plain:<base64>` payloads are
 * accepted only by the migration path in [readAiApiKey]: they are immediately replaced by an
 * encrypted payload before the value is exposed. If that replacement fails, the read returns an
 * empty value and keeps the historical file for a later retry.
 */
class KeystoreSecretStore private constructor(
    private val credentialFile: File,
    private val legacyFile: File,
    private val crypto: SecretPayloadCrypto
) : SecretStore {

    constructor(context: Context) : this(
        credentialFile = File(context.applicationContext.noBackupFilesDir, CREDENTIAL_FILE_NAME),
        legacyFile = File(context.applicationContext.noBackupFilesDir, SecretStore.LEGACY_FILE_NAME),
        crypto = AndroidKeystorePayloadCrypto()
    )

    internal constructor(directory: File, crypto: SecretPayloadCrypto) : this(
        credentialFile = File(directory, CREDENTIAL_FILE_NAME),
        legacyFile = File(directory, SecretStore.LEGACY_FILE_NAME),
        crypto = crypto
    )

    override fun saveAiApiKey(value: String): SecretWriteResult {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return clearAiApiKey()

        return runCatching {
            val encrypted = crypto.encrypt(trimmed)
            require(encrypted.startsWith(VERSION_PREFIX)) { "加密器返回了不受支持的凭据格式" }
            replaceAtomically(credentialFile, encrypted)
            SecretWriteResult.Saved
        }.getOrElse { error ->
            Log.e(TAG, "Keystore 加密或凭据落盘失败；拒绝保存未加密内容", error)
            SecretWriteResult.Failure(error)
        }
    }

    override fun readAiApiKey(): String {
        if (!credentialFile.exists()) return ""
        val stored = runCatching { credentialFile.readText(Charsets.UTF_8) }.getOrElse {
            Log.e(TAG, "读取凭据文件失败", it)
            return ""
        }
        if (stored.isBlank()) return ""

        if (stored.startsWith(PLAIN_PREFIX)) {
            val legacyValue = runCatching { crypto.decodeLegacyPlain(stored) }.getOrElse {
                quarantineUnreadableCredential(it)
                return ""
            }.trim()
            if (legacyValue.isEmpty()) {
                runCatching { credentialFile.delete() }
                return ""
            }
            return when (saveAiApiKey(legacyValue)) {
                SecretWriteResult.Saved -> {
                    Log.i(TAG, "已将历史 plain 凭据一次性迁移至 Keystore")
                    legacyValue
                }
                else -> ""
            }
        }

        return runCatching { crypto.decrypt(stored) }.getOrElse { error ->
            quarantineUnreadableCredential(error)
            ""
        }
    }

    override fun clearAiApiKey(): SecretWriteResult = runCatching {
        deleteIfPresent(credentialFile)
        deleteIfPresent(legacyFile)
        crypto.clearKey()
        SecretWriteResult.Cleared
    }.getOrElse { error ->
        Log.e(TAG, "清除凭据失败", error)
        SecretWriteResult.Failure(error)
    }

    override fun migrateLegacyApiKeyIfPresent(): LegacySecretMigrationResult {
        if (!legacyFile.exists()) return LegacySecretMigrationResult.NothingToMigrate
        val legacyValue = runCatching { legacyFile.readText(Charsets.UTF_8).trim() }.getOrElse {
            return LegacySecretMigrationResult.Failure(it)
        }
        if (legacyValue.isEmpty()) {
            return runCatching {
                deleteIfPresent(legacyFile)
                LegacySecretMigrationResult.NothingToMigrate
            }.getOrElse { LegacySecretMigrationResult.Failure(it) }
        }

        return when (val write = saveAiApiKey(legacyValue)) {
            SecretWriteResult.Saved -> runCatching {
                deleteIfPresent(legacyFile)
                Log.i(TAG, "已将数据库遗留 API Key 迁移至 Keystore")
                LegacySecretMigrationResult.Migrated
            }.getOrElse { LegacySecretMigrationResult.Failure(it) }
            is SecretWriteResult.Failure -> LegacySecretMigrationResult.Failure(write.cause)
            SecretWriteResult.Cleared -> LegacySecretMigrationResult.NothingToMigrate
        }
    }

    private fun quarantineUnreadableCredential(error: Throwable) {
        Log.w(TAG, "凭据无法解密，已隔离本地副本", error)
        val unreadable = File(credentialFile.parentFile, "${credentialFile.name}.unreadable")
        runCatching {
            if (unreadable.exists()) unreadable.delete()
            if (!credentialFile.renameTo(unreadable)) credentialFile.delete()
        }
    }

    private fun replaceAtomically(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val backup = File(target.parentFile, "${target.name}.bak")
        FileOutputStream(temporary).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }

        if (backup.exists() && !backup.delete()) {
            temporary.delete()
            error("无法清理旧凭据备份")
        }
        if (target.exists() && !target.renameTo(backup)) {
            temporary.delete()
            error("无法暂存旧凭据")
        }
        if (!temporary.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            temporary.delete()
            error("无法原子替换凭据文件")
        }
        if (backup.exists() && !backup.delete()) {
            Log.w(TAG, "凭据已更新，但旧的加密备份未能删除")
        }
    }

    private fun deleteIfPresent(file: File) {
        if (file.exists() && !file.delete()) error("无法删除 ${file.name}")
    }

    private companion object {
        const val TAG = "YanjiSecret"
        const val VERSION_PREFIX = "v1:"
        const val PLAIN_PREFIX = "plain:"
        const val CREDENTIAL_FILE_NAME = "yanji_ai_credential"
    }
}

private class AndroidKeystorePayloadCrypto : SecretPayloadCrypto {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return VERSION_PREFIX + Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String): String {
        require(stored.startsWith(VERSION_PREFIX)) { "未知的凭据存储格式" }
        val packed = Base64.decode(stored.substring(VERSION_PREFIX.length), Base64.NO_WRAP)
        require(packed.size > IV_LENGTH) { "凭据内容损坏" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, packed.copyOfRange(0, IV_LENGTH))
        )
        return String(cipher.doFinal(packed.copyOfRange(IV_LENGTH, packed.size)), Charsets.UTF_8)
    }

    override fun decodeLegacyPlain(stored: String): String {
        require(stored.startsWith(PLAIN_PREFIX)) { "不是历史 plain 凭据" }
        return String(
            Base64.decode(stored.substring(PLAIN_PREFIX.length), Base64.NO_WRAP),
            Charsets.UTF_8
        )
    }

    override fun clearKey() {
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
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
            generateKey()
        }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "yanji_ai_credential_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val IV_LENGTH = 12
        const val VERSION_PREFIX = "v1:"
        const val PLAIN_PREFIX = "plain:"
    }
}
