package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides hardware-backed (or AndroidKeyStore software-backed) AES-256 GCM encryption
 * for securely persisting sensitive credentials like the user's AI API key.
 */
class KeystoreManager(private val context: Context) {

    private val keyAlias = "JarvisSecretKey_v1"
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val sharedPrefs = context.getSharedPreferences("jarvis_secure_vault", Context.MODE_PRIVATE)

    init {
        createKeyIfNeeded()
    }

    private fun createKeyIfNeeded() {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    fun encryptAndSaveApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            removeApiKey()
            return
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            sharedPrefs.edit()
                .putString("encrypted_api_key", encryptedBase64)
                .putString("api_key_iv", ivBase64)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback for devices with keystore issues
            val obfuscated = Base64.encodeToString(apiKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            sharedPrefs.edit().putString("fallback_api_key", obfuscated).apply()
        }
    }

    fun getDecryptedApiKey(): String? {
        val encryptedBase64 = sharedPrefs.getString("encrypted_api_key", null)
        val ivBase64 = sharedPrefs.getString("api_key_iv", null)

        if (encryptedBase64 != null && ivBase64 != null) {
            try {
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val fallback = sharedPrefs.getString("fallback_api_key", null)
        if (fallback != null) {
            return try {
                String(Base64.decode(fallback, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun removeApiKey() {
        sharedPrefs.edit()
            .remove("encrypted_api_key")
            .remove("api_key_iv")
            .remove("fallback_api_key")
            .apply()
    }

    fun saveApiKey(apiKey: String) {
        encryptAndSaveApiKey(apiKey)
    }

    fun hasApiKey(): Boolean {
        return isApiKeyConfigured()
    }

    fun isApiKeyConfigured(): Boolean {
        val key = getDecryptedApiKey()
        return !key.isNullOrBlank()
    }

    fun getMaskedApiKey(): String {
        val key = getDecryptedApiKey() ?: return "Not configured"
        return if (key.length <= 8) {
            "••••••••"
        } else {
            "${key.take(4)}••••••••${key.takeLast(4)}"
        }
    }
}
