package com.candado.app.core

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import com.candado.app.data.BioKind
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Biométricos. Android no deja elegir "qué dedo" exactamente, pero sí:
 *  - el tipo (fuerte / cualquiera),
 *  - ligar el candado a los biométricos registrados HOY: una llave del chip seguro
 *    se invalida si alguien agrega una huella o rostro nuevo.
 */
object BioGate {
    fun authenticators(kind: BioKind): Int = when (kind) {
        BioKind.HUELLAS_ACTUALES, BioKind.FUERTE -> BIOMETRIC_STRONG
        BioKind.CUALQUIERA -> BIOMETRIC_WEAK
    }

    fun status(ctx: Context, kind: BioKind): String? =
        when (BiometricManager.from(ctx).canAuthenticate(authenticators(kind))) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No hay biométricos registrados en el teléfono. Regístralos en Ajustes > Seguridad."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Este teléfono no tiene ese tipo de biométrico."
            else -> "Ese biométrico no está disponible ahora en este teléfono."
        }

    fun createKey(alias: String) {
        val b = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(b.build())
        kg.generateKey()
    }

    /** null si la llave ya no existe o se invalidó porque cambiaron los biométricos. */
    fun cipherFor(alias: String): Cipher? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(alias, null) as? SecretKey
        if (key == null) null else Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        null
    } catch (e: Exception) {
        null
    }

    fun deleteKey(alias: String?) {
        if (alias == null) return
        try { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) } catch (_: Exception) {}
    }
}
