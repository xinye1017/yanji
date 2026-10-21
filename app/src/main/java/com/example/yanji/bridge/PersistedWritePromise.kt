package com.example.yanji.bridge

import com.facebook.react.bridge.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Keep write failures on the originating RN Promise, rather than a detached coroutine. */
internal fun CoroutineScope.launchPersistedWrite(
    promise: Promise,
    errorCode: String,
    write: suspend () -> Unit
) = launch {
    try {
        write()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        promise.reject(errorCode, "数据写入失败，请重试", error)
    }
}
