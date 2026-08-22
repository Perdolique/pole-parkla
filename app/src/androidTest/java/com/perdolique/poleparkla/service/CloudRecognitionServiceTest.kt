package com.perdolique.poleparkla.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.model.CloudProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudRecognitionServiceTest {
    @Test
    fun postsAuthenticatedMultipartRequestAndParsesResponse() = runBlocking {
        val connection = FakeConnection(
            responseBody = """
                {
                  "plateCandidates": ["123 ABC"],
                  "vehicleMake": "Toyota",
                  "vehicleModel": "Corolla",
                  "suggestedViolationType": null
                }
            """.trimIndent(),
        )
        val service = CloudRecognitionService(connectionFactory = { connection })
        val image = jpegFile()

        try {
            val result = service.recognize(
                workerUrl = "https://worker.example",
                bearerToken = "app-token",
                provider = CloudProvider.WORKERS_AI,
                image = image,
            )

            assertEquals(listOf("123 ABC"), result.plateCandidates)
            assertEquals("Bearer app-token", connection.getRequestProperty("Authorization"))
            assertTrue(connection.writtenBody().contains("name=\"provider\""))
            assertTrue(connection.writtenBody().contains("workers_ai"))
            assertTrue(connection.disconnected)
        } finally {
            image.delete()
        }
    }

    @Test
    fun totalDeadlineInterruptsAStalledUpload() = runBlocking {
        val connection = FakeConnection(output = BlockingOutputStream())
        val service = CloudRecognitionService(
            connectionFactory = { connection },
            totalTimeoutMillis = 100L,
        )
        val image = jpegFile()

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

    private fun jpegFile(): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return File.createTempFile("cloud-recognition-", ".jpg", context.cacheDir).apply {
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        }
    }

    private class FakeConnection(
        private val responseBody: String = "{}",
        private val output: OutputStream = ByteArrayOutputStream(),
    ) : HttpURLConnection(URL("https://worker.example/v1/recognize")) {
        var disconnected = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = output

        override fun getResponseCode(): Int = 200

        override fun getInputStream(): InputStream =
            ByteArrayInputStream(responseBody.toByteArray(StandardCharsets.UTF_8))

        fun writtenBody(): String = (output as ByteArrayOutputStream).toString(StandardCharsets.ISO_8859_1.name())
    }

    private class BlockingOutputStream : OutputStream() {
        private val release = CountDownLatch(1)

        override fun write(value: Int) {
            awaitRelease()
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            awaitRelease()
        }

        private fun awaitRelease() {
            try {
                release.await()
            } catch (error: InterruptedException) {
                throw InterruptedIOException("Upload interrupted").also { it.initCause(error) }
            }
        }
    }
}
