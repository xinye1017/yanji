package com.example.yanji.data

/** Synchronous acceptance result for settings writes that may touch Android Keystore. */
sealed interface SettingsUpdateResult {
    data object Saved : SettingsUpdateResult
    data object SecretUnavailable : SettingsUpdateResult
}
