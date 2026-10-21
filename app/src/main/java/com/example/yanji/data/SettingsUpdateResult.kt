package com.example.yanji.data

/** Returned only after settings persistence; credentials may also require Android Keystore. */
sealed interface SettingsUpdateResult {
    data object Saved : SettingsUpdateResult
    data object SecretUnavailable : SettingsUpdateResult
}
