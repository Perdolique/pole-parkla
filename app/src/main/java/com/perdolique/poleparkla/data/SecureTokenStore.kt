package com.perdolique.poleparkla.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_cloud_token", Context.MODE_PRIVATE)

    @Synchronized
    fun save(token: String, workerUrl: String) {
        val origin = workerOrigin(workerUrl)
        if (token.isBlank() || origin == null) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val saved = preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_WORKER_ORIGIN, origin)
            .commit()
        check(saved) { "Unable to persist cloud token" }
    }

    @Synchronized
    fun read(workerUrl: String): String = runCatching {
        val requestedOrigin = workerOrigin(workerUrl) ?: return@runCatching ""
        val savedOrigin = preferences.getString(KEY_WORKER_ORIGIN, null) ?: return@runCatching ""
        if (savedOrigin != requestedOrigin) return@runCatching ""
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return@runCatching ""
        val iv = preferences.getString(KEY_IV, null) ?: return@runCatching ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrElse {
        clear()
        ""
    }

    @Synchronized
    fun clear() {
        check(preferences.edit().clear().commit()) { "Unable to clear cloud token" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun workerOrigin(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
        val host = uri.host.lowercase(Locale.ROOT)
        val port = uri.port.takeIf { it != -1 && it != 443 }?.let { ":$it" }.orEmpty()
        return "https://$host$port"
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pole_parkla_worker_bearer_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val KEY_WORKER_ORIGIN = "worker_origin"
    }
}
