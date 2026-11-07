package com.candado.app.core

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** NIP, contraseña y QR nunca se guardan en claro: solo su huella PBKDF2/SHA-256. */
object Crypto {
    private val rnd = SecureRandom()
    private const val ALPHA = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun randomBytes(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }
    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun b64d(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP or Base64.URL_SAFE)

    fun newSalt(): String = b64(randomBytes(16))

    fun hash(secret: String, salt: String): String {
        val spec = PBEKeySpec(secret.toCharArray(), b64d(salt), 120_000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return b64(f.generateSecret(spec).encoded)
    }

    fun verify(secret: String, salt: String, expected: String?): Boolean {
        if (expected == null) return false
        return MessageDigest.isEqual(hash(secret, salt).toByteArray(), expected.toByteArray())
    }

    fun newQrSecret(): String = "CANDADO:" + b64(randomBytes(32))

    fun randomText(len: Int): String = buildString {
        repeat(len) { i ->
            if (i > 0 && i % 5 == 0) append(' ')
            append(ALPHA[rnd.nextInt(ALPHA.length)])
        }
    }
}
