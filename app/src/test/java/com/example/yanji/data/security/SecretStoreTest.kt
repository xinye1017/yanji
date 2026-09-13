package com.example.yanji.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class SecretStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun encryptionFailureIsFailClosedAndNeverWritesPlaintext() {
        val directory = temporaryFolder.newFolder("encrypt-failure")
        val store = KeystoreSecretStore(directory, FakeCrypto(encryptFailure = IOException("keystore down")))

        val result = store.saveAiApiKey("sk-sensitive")

        assertTrue(result is SecretWriteResult.Failure)
        assertFalse(FileNames.credential(directory).exists())
        assertFalse(directory.listFiles().orEmpty().any { file ->
            runCatching { file.readText().contains("sk-sensitive") }.getOrDefault(false)
        })
    }

    @Test
    fun historicalPlainPayloadIsReadOnlyForOneSuccessfulMigration() {
        val directory = temporaryFolder.newFolder("plain-migration")
        val crypto = FakeCrypto()
        val credential = FileNames.credential(directory)
        credential.writeText("plain:legacy-payload")
        val store = KeystoreSecretStore(directory, crypto)

        assertEquals("legacy-secret", store.readAiApiKey())
        assertTrue(credential.readText().startsWith("v1:"))
        assertFalse(credential.readText().startsWith("plain:"))
        assertEquals(1, crypto.legacyDecodeCount)

        assertEquals("legacy-secret", store.readAiApiKey())
        assertEquals("plain payload must never be decoded again", 1, crypto.legacyDecodeCount)
    }

    @Test
    fun historicalPlainPayloadIsNotExposedWhenMigrationCannotEncrypt() {
        val directory = temporaryFolder.newFolder("plain-failure")
        val credential = FileNames.credential(directory)
        credential.writeText("plain:legacy-payload")
        val store = KeystoreSecretStore(directory, FakeCrypto(encryptFailure = IOException("locked")))

        assertEquals("", store.readAiApiKey())
        assertEquals("plain:legacy-payload", credential.readText())
    }

    @Test
    fun databaseLegacyFileIsDeletedOnlyAfterEncryptedWriteSucceeds() {
        val directory = temporaryFolder.newFolder("db-legacy")
        val legacy = java.io.File(directory, SecretStore.LEGACY_FILE_NAME)
        legacy.writeText("sk-from-v7")
        val failing = KeystoreSecretStore(directory, FakeCrypto(encryptFailure = IOException("locked")))

        assertTrue(failing.migrateLegacyApiKeyIfPresent() is LegacySecretMigrationResult.Failure)
        assertTrue("failed migration must remain retryable", legacy.exists())

        val working = KeystoreSecretStore(directory, FakeCrypto())
        assertEquals(LegacySecretMigrationResult.Migrated, working.migrateLegacyApiKeyIfPresent())
        assertFalse(legacy.exists())
        assertEquals("sk-from-v7", working.readAiApiKey())
    }

    @Test
    fun saveAiApiKey_neverProducesPlainFallbackOnFailure() {
        val directory = temporaryFolder.newFolder("no-plain-fallback")
        val failingStore = KeystoreSecretStore(directory, FakeCrypto(encryptFailure = IOException("Keystore hardware failed")))

        val result = failingStore.saveAiApiKey("sk-my-super-secret-key")
        assertTrue(result is SecretWriteResult.Failure)

        val credential = FileNames.credential(directory)
        assertFalse("凭据文件不应该存在于磁盘上", credential.exists())
        assertEquals("", failingStore.readAiApiKey())
    }

    private class FakeCrypto(
        private val encryptFailure: Throwable? = null
    ) : SecretPayloadCrypto {
        var legacyDecodeCount: Int = 0

        override fun encrypt(plain: String): String {
            encryptFailure?.let { throw it }
            return "v1:encrypted($plain)"
        }

        override fun decrypt(stored: String): String =
            stored.removePrefix("v1:encrypted(").removeSuffix(")")

        override fun decodeLegacyPlain(stored: String): String {
            legacyDecodeCount++
            assertEquals("plain:legacy-payload", stored)
            return "legacy-secret"
        }

        override fun clearKey() = Unit
    }

    private object FileNames {
        fun credential(directory: java.io.File) = java.io.File(directory, "yanji_ai_credential")
    }
}
