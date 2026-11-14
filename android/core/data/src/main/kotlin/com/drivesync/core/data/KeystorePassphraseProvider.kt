package com.drivesync.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates a random secret once, stores it encrypted with an AES-GCM key that never leaves
 * the Android Keystore, and returns it on demand. Used for the SQLCipher passphrase and for
 * the server session token (기획서 3-⑥: 서버 토큰은 Android Keystore에 보관).
 */
class KeystoreSecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOrCreate(name: String, sizeBytes: Int = 32): ByteArray {
        read(name)?.let { return it }
        val secret = ByteArray(sizeBytes).also(SecureRandom()::nextBytes)
        write(name, secret)
        return secret
    }

    fun read(name: String): ByteArray? {
        val stored = prefs.getString(name, null) ?: return null
        val (ivB64, dataB64) = stored.split(SEPARATOR).takeIf { it.size == 2 } ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(dataB64, Base64.NO_WRAP))
    }

    fun write(name: String, secret: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(secret)
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(name, value).apply()
    }

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "drivesync.master"
        const val PREFS = "drivesync.secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val SEPARATOR = ":"
    }
}

class KeystorePassphraseProvider(private val secrets: KeystoreSecretStore) {
    fun databasePassphrase(): ByteArray = secrets.getOrCreate("db.passphrase")
}
