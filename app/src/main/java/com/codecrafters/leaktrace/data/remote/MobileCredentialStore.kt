package com.codecrafters.leaktrace.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable data class PendingPairing(val pairingId: Long, val code: String, val expiresAt: String, val secret: String)

interface MobileCredentialStore {
    fun token(): MobileTokenDto?
    fun saveToken(value: MobileTokenDto)
    fun clearToken()
    fun pending(): PendingPairing?
    fun savePending(value: PendingPairing)
    fun clearPending()
}

/** SharedPreferences contains only AES-GCM ciphertext; its key never leaves Android Keystore. */
internal fun credentialNamespace(origin: String): String = "mobile_credentials_v2_" +
    MessageDigest.getInstance("SHA-256").digest(origin.trimEnd('/').toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

class KeystoreMobileCredentialStore(context: Context, origin: String) : MobileCredentialStore {
    // Never send an earlier backend's token or pairing secret to a newly configured server.
    private val prefs = context.applicationContext.getSharedPreferences(credentialNamespace(origin), Context.MODE_PRIVATE)
    private val alias = "leaktrace_mobile_credentials_v1"

    override fun token(): MobileTokenDto? = read("token")?.let { value -> decodeOrClear("token") { LeakTraceJson.decodeFromString<MobileTokenDto>(value) } }
    override fun saveToken(value: MobileTokenDto) = write("token", LeakTraceJson.encodeToString(value))
    override fun clearToken() { prefs.edit().remove("token").commit() }
    override fun pending(): PendingPairing? = read("pending")?.let { value -> decodeOrClear("pending") { LeakTraceJson.decodeFromString<PendingPairing>(value) } }
    override fun savePending(value: PendingPairing) = write("pending", LeakTraceJson.encodeToString(value))
    override fun clearPending() { prefs.edit().remove("pending").commit() }

    private fun <T> decodeOrClear(name: String, decode: () -> T): T? = try {
        decode()
    } catch (_: Exception) {
        prefs.edit().remove(name).commit()
        null
    }

    private fun write(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }

    private fun read(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            prefs.edit().remove(name).commit()
            null
        }
    }

    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
