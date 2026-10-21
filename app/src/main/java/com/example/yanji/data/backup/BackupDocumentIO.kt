package com.example.yanji.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Stream ownership stays here: an export succeeds only after flush and close succeed. */
internal object BackupDocumentIO {
    fun read(input: InputStream, maxBytes: Int = BackupCodec.MAX_IMPORT_BYTES): String = input.use {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = it.read(buffer, 0, minOf(buffer.size, maxBytes - total + 1))
            if (count < 0) break
            total += count
            if (total > maxBytes) throw BackupOperationException("备份文件过大（超过 64 MB），请选择研迹导出的备份")
            bytes.write(buffer, 0, count)
        }
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }

    fun encodeUtf8(json: String, maxBytes: Int = BackupCodec.MAX_IMPORT_BYTES): ByteArray {
        fun tooLarge(): Nothing = throw BackupOperationException("数据超过当前支持的备份大小（64 MB），未生成可用备份")
        if (json.length > maxBytes) tooLarge()
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) tooLarge()
        return bytes
    }

    fun write(json: String, openOutput: () -> OutputStream, maxBytes: Int = BackupCodec.MAX_IMPORT_BYTES) {
        val bytes = encodeUtf8(json, maxBytes)
        // Opening a provider document in write mode may truncate it. Validate before opening.
        openOutput().use {
            it.write(bytes)
            it.flush()
        }
    }
}
