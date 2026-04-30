package com.asyachz.eyepayapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(context: Context) {
    private val keyAlias = "EyePayDbKey"
    private val provider = "AndroidKeyStore"
    private val sharedPrefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    init {
        generateKeyIfMissing()
    }

    private fun generateKeyIfMissing() {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
            val spec = KeyGenParameterSpec.Builder(keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    fun getDatabasePassword(): ByteArray {
        val encryptedPass = sharedPrefs.getString("db_pass", null)
        return if (encryptedPass == null) {
            val newPass = java.security.SecureRandom().generateSeed(32)
            savePassword(newPass)
            newPass
        } else {
            decryptPassword(encryptedPass)
        }
    }

    private fun savePassword(pass: ByteArray) {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(pass)
        val iv = cipher.iv

        sharedPrefs.edit()
            .putString("db_pass", android.util.Base64.encodeToString(encryptedBytes, 0))
            .putString("db_iv", android.util.Base64.encodeToString(iv, 0))
            .apply()
    }

    private fun decryptPassword(encryptedBase64: String): ByteArray {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        val iv = android.util.Base64.decode(sharedPrefs.getString("db_iv", ""), 0)
        val encryptedBytes = android.util.Base64.decode(encryptedBase64, 0)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(encryptedBytes)
    }
}