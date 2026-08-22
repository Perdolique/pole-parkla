package com.perdolique.poleparkla.service

import com.perdolique.poleparkla.model.CloudProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecognitionTimeoutTest {
    @Test
    fun `total deadline interrupts a stalled upload`() = runBlocking {
        val connection = BlockingConnection()
        val service = CloudRecognitionService(
            connectionFactory = { connection },
            totalTimeoutMillis = 100L,
        )
        val image = File.createTempFile("pole-parkla-cloud-", ".jpg", File("/tmp")).apply {
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        }

        try {
            val error = runCatching {
                service.recognize(
                    workerUrl = "https://worker.example",
                    bearerToken = "app-token",
                    provider = CloudProvider.WORKERS_AI,
                    image = image,
                )
            }.exceptionOrNull()

            assertTrue(error is CloudRecognitionException)
            assertTrue(connection.disconnected)
        } finally {
            image.delete()
        }
    }

    @Test
    fun `socket timeout is reported as provider unavailable instead of cancellation`() = runBlocking {
        val connection = SocketTimeoutConnection()
        val service = CloudRecognitionService(connectionFactory = { connection })
        val image = File.createTempFile("pole-parkla-cloud-", ".jpg", File("/tmp")).apply {
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        }

        try {
            val error = runCatching {
                service.recognize(
                    workerUrl = "https://worker.example",
                    bearerToken = "app-token",
                    provider = CloudProvider.WORKERS_AI,
                    image = image,
                )
            }.exceptionOrNull()

            assertTrue(error is CloudRecognitionException)
            assertEquals("PROVIDER_UNAVAILABLE", (error as CloudRecognitionException).code)
            assertTrue(connection.disconnected)
        } finally {
            image.delete()
        }
    }

    private class BlockingConnection : HttpURLConnection(URL("https://worker.example/v1/recognize")) {
        var disconnected = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = BlockingOutputStream()
    }

    private class SocketTimeoutConnection : HttpURLConnection(URL("https://worker.example/v1/recognize")) {
        var disconnected = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getResponseCode(): Int = throw SocketTimeoutException("Read timed out")
    }

    private class BlockingOutputStream : OutputStream() {
        private val release = CountDownLatch(1)

        override fun write(value: Int) {
            try {
                release.await()
            } catch (error: InterruptedException) {
                throw InterruptedIOException("Upload interrupted").also { it.initCause(error) }
            }
        }
    }
}
