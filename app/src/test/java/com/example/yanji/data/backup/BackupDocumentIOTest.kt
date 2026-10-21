package com.example.yanji.data.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BackupDocumentIOTest {
    @Test fun utf8FileRoundTripClosesBothStreams() {
        val json = "{\"随笔\":\"保留昨天的学习记录 🌱\"}"
        var outputClosed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { outputClosed = true; super.close() }
        }
        BackupDocumentIO.write(json, { output })
        assertTrue(outputClosed)
        var inputClosed = false
        val input = object : ByteArrayInputStream(output.toByteArray()) {
            override fun close() { inputClosed = true; super.close() }
        }
        assertEquals(json, BackupDocumentIO.read(input))
        assertTrue(inputClosed)
    }

    @Test fun oversizedUnknownLengthStreamIsRejectedAndClosedBeforeReadingAllBytes() {
        var consumed = 0
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int { consumed++; return 'x'.code }
            override fun close() { closed = true }
        }
        val failure = runCatching { BackupDocumentIO.read(input, maxBytes = 16) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("过大"))
        assertTrue(closed)
        assertTrue("Must stop at the byte limit, even without provider size metadata", consumed <= 17)
    }

    @Test fun malformedUtf8IsRejectedInsteadOfSilentlyReplacingStudyText() {
        val invalid = ByteArrayInputStream(byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D))
        assertTrue(runCatching { BackupDocumentIO.read(invalid) }.isFailure)
    }

    @Test fun exportCannotSucceedWithAFileLargerThanTheImportByteLimit() {
        val output = ByteArrayOutputStream()
        var opened = false
        // Four UTF-8 bytes despite only two UTF-16 code units.
        val failure = runCatching {
            BackupDocumentIO.write("🌱", { opened = true; output }, maxBytes = 3)
        }.exceptionOrNull()
        assertTrue(failure is BackupOperationException)
        assertFalse("Validation must happen before a provider can truncate the destination", opened)
        assertEquals("An oversized export must not write an unusable backup", 0, output.size())
    }

    @Test fun safetySnapshotEncodingUsesTheSameUtf8ByteLimitAsExport() {
        val bytes = BackupDocumentIO.encodeUtf8("🌱", maxBytes = 4)
        assertArrayEquals("🌱".toByteArray(Charsets.UTF_8), bytes)
        assertTrue(runCatching {
            BackupDocumentIO.encodeUtf8("🌱", maxBytes = 3)
        }.exceptionOrNull() is BackupOperationException)
    }

    @Test fun readFailureClosesStreamAndPropagates() {
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("injected read failure")
            override fun close() { closed = true }
        }
        assertTrue(runCatching { BackupDocumentIO.read(input) }.exceptionOrNull() is IOException)
        assertTrue(closed)
    }

    @Test fun writeAndCloseFailuresCannotBeReportedAsSuccessfulExport() {
        for (failOnClose in listOf(false, true)) {
            var closed = false
            val output = object : OutputStream() {
                override fun write(value: Int) {
                    if (!failOnClose) throw IOException("injected write failure")
                }
                override fun close() { closed = true; if (failOnClose) throw IOException("injected close failure") }
            }
            assertTrue(runCatching { BackupDocumentIO.write("{}", { output }) }.exceptionOrNull() is IOException)
            assertTrue(closed)
        }
    }
}
