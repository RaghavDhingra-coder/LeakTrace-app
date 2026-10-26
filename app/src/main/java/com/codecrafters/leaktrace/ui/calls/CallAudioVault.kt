package com.codecrafters.leaktrace.ui.calls

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.codecrafters.leaktrace.data.remote.ApiConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private, non-backed-up recordings encrypted with a device-bound Android Keystore key. */
object CallAudioVault {
    private const val KEY = "leaktrace_call_audio_v1"
    fun ownerKey(email: String): String = "${ApiConfig.baseUrl.trimEnd('/')}|${email.lowercase()}"
    private fun directory(context: Context, owner: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(owner.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(File(context.filesDir, "call_audio"), key).also { it.mkdirs() }
    }
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun create(context: Context, owner: String): Pair<File, OutputStream> {
        val file = File(directory(context, owner), "${UUID.randomUUID()}.partial")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val raw = FileOutputStream(file)
        raw.write(cipher.iv)
        return file to CipherOutputStream(raw, cipher)
    }
    fun complete(context: Context, owner: String, path: String): String {
        val partial = File(path).canonicalFile
        require(partial.parentFile == directory(context, owner).canonicalFile && partial.extension == "partial")
        val complete = File(partial.parentFile, partial.nameWithoutExtension + ".lta")
        check(partial.renameTo(complete)) { "Recording could not be finalized" }
        return complete.absolutePath
    }
    fun open(context: Context, owner: String, path: String): InputStream {
        val file = File(path).canonicalFile
        require(file.parentFile == directory(context, owner).canonicalFile && file.extension == "lta")
        val raw = FileInputStream(file)
        val iv = ByteArray(12)
        if (raw.read(iv) != iv.size) { raw.close(); throw IllegalArgumentException("Invalid audio file") }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv)) }
        return CipherInputStream(raw, cipher)
    }
    fun delete(context: Context, owner: String, path: String): Boolean {
        val file = File(path).canonicalFile
        return file.parentFile == directory(context, owner).canonicalFile && (file.extension == "lta" || file.extension == "partial") && file.delete()
    }
    fun list(context: Context, owner: String): List<File> = directory(context, owner).listFiles()
        ?.filter { it.isFile && it.extension == "lta" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    fun count(context: Context, owner: String): Int = list(context, owner).size
    fun deleteAll(context: Context, owner: String) {
        directory(context, owner).listFiles()?.filter { it.extension == "lta" || it.extension == "partial" }?.forEach { it.delete() }
    }
}
