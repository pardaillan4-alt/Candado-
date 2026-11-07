package com.candado.app.ui

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.candado.app.core.BioGate
import com.candado.app.core.Crypto
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.data.BioKind
import com.candado.app.data.Lock
import com.candado.app.data.Method
import com.candado.app.data.Store
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class UnlockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val id = intent.getStringExtra("lockId")
        if (id == null || Store.lock(id) == null) { finish(); return }
        setContent { CandadoTheme { UnlockScreen(id) } }
    }

    fun authBiometric(lock: Lock, onOk: () -> Unit, onFail: (String) -> Unit) {
        val kind = lock.bioKind ?: BioKind.FUERTE
        BioGate.status(this, kind)?.let { onFail(it); return }
        val needCrypto = kind == BioKind.HUELLAS_ACTUALES
        val cipher = if (needCrypto) lock.bioAlias?.let { BioGate.cipherFor(it) } else null
        if (needCrypto && cipher == null) {
            onFail("Los biométricos del teléfono cambiaron desde que pusiste el candado (o se borró la llave). Este método ya no se puede usar: usa otro método del candado.")
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (needCrypto) {
                    try { result.cryptoObject?.cipher?.doFinal(byteArrayOf(1, 2, 3)) ?: throw IllegalStateException() ; onOk() }
                    catch (e: Exception) { onFail("No se pudo verificar con el chip seguro.") }
                } else onOk()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail(errString.toString()) }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Abrir \"${lock.nombre}\"")
            .setSubtitle(kind.titulo)
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(BioGate.authenticators(kind))
            .setConfirmationRequired(true)
            .build()
        if (cipher != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) else prompt.authenticate(info)
    }
}

@Composable
private fun UnlockScreen(lockId: String) {
    val act = androidx.compose.ui.platform.LocalContext.current as UnlockActivity
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    val now = rememberTicker(5_000)
    val lock = Store.lock(lockId) ?: run { act.finish(); return }
    val done = remember { mutableStateMapOf<Method, Boolean>() }
    var msg by remember { mutableStateOf<String?>(null) }
    var unlocked by remember { mutableStateOf(false) }
    val randomText = remember { Crypto.randomText(lock.randomLen) }

    fun fail(text: String) {
        Store.updateLock(lockId) { it.copy(fails = it.fails + 1, lastFailAt = Guard.now()) }
        msg = text
    }

    // Enfriamiento cumplido = método cumplido.
    val cooldownReady = lock.cooldownRequestedAt > 0 && now >= lock.cooldownRequestedAt + lock.cooldownHours * 3_600_000L
    fun isDone(m: Method) = done[m] == true || (m == Method.COOLDOWN && cooldownReady)

    val satisfied = if (lock.methods.isEmpty()) false
    else if (lock.requireAll) lock.methods.all { isDone(it) } else lock.methods.any { isDone(it) }

    LaunchedEffect(satisfied) {
        if (satisfied && !unlocked && !Guard.isStrict(lock)) {
            Store.updateLock(lockId) {
                it.copy(passUntil = Guard.now() + it.passMinutes * 60_000L, fails = 0, cooldownRequestedAt = 0)
            }
            OwnerPolicies.apply(act)
            unlocked = true
        }
    }

    val scan = rememberLauncherForActivityResult(ScanContract()) { r ->
        val c = r.contents ?: return@rememberLauncherForActivityResult
        if (Crypto.verify(c, lock.salt, lock.qrHash)) done[Method.QR] = true else fail("Ese QR no es de este candado.")
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("🔒 ${lock.nombre}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Hint("Protege: " + lock.targets.joinToString { it.titulo })

        val wait = Guard.failWaitMs(lock)
        when {
        unlocked -> {
            InfoCard {
                Text("🔓 Abierto por ${lock.passMinutes} minutos", fontWeight = FontWeight.Bold, color = Ok)
                Hint("Ya puedes cambiar lo que protege. Después se cierra solo. Puedes eliminarlo desde la pestaña Candados.")
                Button(onClick = { act.finish() }) { Text("Listo") }
            }
        }
        Guard.isStrict(lock, now) -> {
            InfoCard {
                Text("Bloqueo estricto", fontWeight = FontWeight.Bold, color = Danger)
                Text("Faltan ${fmtRemaining(lock.strictUntil - now)}. Termina el ${fmtDate(lock.strictUntil)}.")
                Hint("Durante este tiempo nada puede abrirlo.")
                Button(onClick = { act.finish() }) { Text("Entendido") }
            }
        }
        wait > 0 -> {
            InfoCard {
                Text("Demasiados intentos fallidos", color = Danger, fontWeight = FontWeight.Bold)
                Text("Espera ${fmtRemaining(wait)} para volver a intentar.")
            }
        }
        else -> {

        Hint(if (lock.requireAll && lock.methods.size > 1) "Debes completar TODOS:" else "Completa uno:")
        msg?.let { Text(it, color = Danger, modifier = Modifier.padding(vertical = 6.dp)) }

        lock.methods.forEach { m ->
            InfoCard {
                Row {
                    Text(m.titulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (isDone(m)) Text("✅")
                }
                if (!isDone(m)) when (m) {
                    Method.PIN, Method.PASSWORD -> {
                        var v by remember { mutableStateOf("") }
                        OutlinedTextField(
                            v, { v = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (m == Method.PIN) "NIP" else "Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = if (m == Method.PIN) KeyboardType.NumberPassword else KeyboardType.Password)
                        )
                        Button(onClick = {
                            val ok = Crypto.verify(v, lock.salt, if (m == Method.PIN) lock.pinHash else lock.pwdHash)
                            if (ok) done[m] = true else { fail("Incorrecto."); v = "" }
                        }) { Text("Verificar") }
                    }
                    Method.QR -> Button(onClick = {
                        scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Escanea el QR de \"${lock.nombre}\"").setBeepEnabled(false).setOrientationLocked(false))
                    }) { Text("Escanear QR") }
                    Method.BIOMETRIC -> {
                        Hint(lock.bioKind?.titulo ?: "")
                        Button(onClick = {
                            act.authBiometric(lock, onOk = { done[Method.BIOMETRIC] = true; msg = null }, onFail = { msg = it })
                        }) { Text("Usar biométrico") }
                    }
                    Method.RANDOM_TEXT -> {
                        var v by remember { mutableStateOf("") }
                        Hint("Escríbelo exactamente (no se puede pegar):")
                        Text(randomText, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                        OutlinedTextField(v, { nv ->
                            // Anti-pegar: si entran muchas letras de golpe, se borra.
                            v = if (nv.length - v.length > 2) "" else nv
                        }, Modifier.fillMaxWidth(), label = { Text("Escribe aquí") })
                        Button(onClick = {
                            if (v.replace(" ", "") == randomText.replace(" ", "")) done[m] = true
                            else msg = "El texto no coincide; revisa mayúsculas y minúsculas."
                        }) { Text("Comprobar") }
                    }
                    Method.COOLDOWN -> {
                        if (lock.cooldownRequestedAt == 0L) {
                            Hint("Al pedirlo, tendrás que esperar ${lock.cooldownHours} h y volver aquí.")
                            Button(onClick = { Store.updateLock(lockId) { it.copy(cooldownRequestedAt = Guard.now()) } }) {
                                Text("Pedir apertura")
                            }
                        } else {
                            val left = lock.cooldownRequestedAt + lock.cooldownHours * 3_600_000L - now
                            Text("Faltan ${fmtRemaining(left)} (${fmtDate(lock.cooldownRequestedAt + lock.cooldownHours * 3_600_000L)}).")
                            TextButton(onClick = { Store.updateLock(lockId) { it.copy(cooldownRequestedAt = 0) } }) { Text("Ya no lo necesito (cancelar)") }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { act.finish() }) { Text("Cancelar") }
        }
        }
    }
}
