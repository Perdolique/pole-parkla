package com.perdolique.poleparkla.service

import com.perdolique.poleparkla.domain.RecognitionJsonParser
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import java.io.DataOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class CloudRecognitionException(
    val code: String,
    val requestId: String?,
    message: String,
) : Exception(message)

class CloudRecognitionService(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
    private val totalTimeoutMillis: Long = TOTAL_TIMEOUT_MILLIS,
) {
    suspend fun recognize(
        workerUrl: String,
        bearerToken: String,
        provider: CloudProvider,
        image: File,
    ): RecognitionResult = try {
        withTimeout(totalTimeoutMillis) {
            runInterruptible(Dispatchers.IO) {
                recognizeBlocking(workerUrl, bearerToken, provider, image)
            }
        }
    } catch (error: TimeoutCancellationException) {
        throw CloudRecognitionException("PROVIDER_UNAVAILABLE", null, "Cloud recognition timed out")
    }

    private fun recognizeBlocking(
        workerUrl: String,
        bearerToken: String,
        provider: CloudProvider,
        image: File,
    ): RecognitionResult {
        require(image.length() in 1..PhotoStore.CLOUD_MAX_BYTES) { "Cloud image must be at most 1 MB" }
        val endpoint = endpoint(workerUrl)
        val boundary = "PoleParkla-${UUID.randomUUID()}"
        val socketTimeout = totalTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val connection = connectionFactory(URL(endpoint)).apply {
            requestMethod = "POST"
            connectTimeout = socketTimeout
            readTimeout = socketTimeout
            doOutput = true
            useCaches = false
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        return try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeField(boundary, "provider", provider.wireValue)
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"vehicle.jpg\"\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                image.inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val json = runCatching { JSONObject(response) }.getOrNull()
                throw CloudRecognitionException(
                    code = json?.optString("code")?.takeIf(String::isNotBlank) ?: "PROVIDER_UNAVAILABLE",
                    requestId = json?.optString("requestId")?.takeIf(String::isNotBlank),
                    message = "Cloud recognition failed",
                )
            }
            RecognitionJsonParser.parse(response, provider.recognitionSource)
        } catch (error: CloudRecognitionException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw CancellationException("Cloud recognition was cancelled", error)
        } catch (error: SocketTimeoutException) {
            throw CloudRecognitionException("PROVIDER_UNAVAILABLE", null, "Cloud recognition timed out")
        } catch (error: InterruptedIOException) {
            throw CancellationException("Cloud recognition was cancelled", error)
        } catch (error: Exception) {
            throw CloudRecognitionException("PROVIDER_UNAVAILABLE", null, "Cloud recognition is unavailable")
        } finally {
            connection.disconnect()
        }
    }

    private fun endpoint(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) { "Worker URL must use HTTPS" }
        return if (trimmed.endsWith("/v1/recognize")) trimmed else "$trimmed/v1/recognize"
    }

    private fun DataOutputStream.writeField(boundary: String, name: String, value: String) {
        writeBytes("--$boundary\r\n")
        writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        write(value.toByteArray(StandardCharsets.UTF_8))
        writeBytes("\r\n")
    }

    private val CloudProvider.wireValue: String
        get() = when (this) {
            CloudProvider.WORKERS_AI -> "workers_ai"
            CloudProvider.OPENAI -> "openai"
        }

    private val CloudProvider.recognitionSource: RecognitionSource
        get() = when (this) {
            CloudProvider.WORKERS_AI -> RecognitionSource.WORKERS_AI
            CloudProvider.OPENAI -> RecognitionSource.OPENAI
        }

    private companion object {
        const val TOTAL_TIMEOUT_MILLIS = 60_000L
    }
}
