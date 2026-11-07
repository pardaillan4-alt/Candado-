package com.candado.app.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.candado.app.core.BioGate
import com.candado.app.core.Crypto
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.core.Qr
import com.candado.app.data.BioKind
import com.candado.app.data.Lock
import com.candado.app.data.Method
import com.candado.app.data.Store
import com.candado.app.data.Target
import java.util.UUID

@Composable
fun CreateLockScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var nombre by remember { mutableStateOf("Mi candado") }
    var targets by remember { mutableStateOf(setOf(Target.APPS)) }
    var methods by remember { mutableStateOf(setOf(Method.PIN)) }
    var requireAll by remember { mutableStateOf(false) }
    var pin1 by remember { mutableStateOf("") }; var pin2 by remember { mutableStateOf("") }
    var pw1 by remember { mutableStateOf("") }; var pw2 by remember { mutableStateOf("") }
    var qrSecret by remember { mutableStateOf<String?>(null) }
    var qrBmp by remember { mutableStateOf<Bitmap?>(null) }
    var qrSaved by remember { mutableStateOf(false) }
    var bioKind by remember { mutableStateOf(BioKind.FUERTE) }
    var randomLen by remember { mutableIntStateOf(60) }
    var cooldownH by remember { mutableIntStateOf(24) }
    var passMin by remember { mutableIntStateOf(15) }
    val strict = remember { StrictState() }
    var confirmUntil by remember { mutableStateOf<Long?>(null) }

    fun toggle(set: Set<Method>, m: Method) = if (m in set) set - m else set + m

    fun validate(): String? {
        if (targets.isEmpty()) return "Elige al menos una cosa que proteger."
        if (methods.isEmpty() && !strict.enabled) return "Elige al menos un método o activa el bloqueo estricto."
        if (strict.enabled && strict.untilFrom(Guard.now()) == null) return "Revisa el tiempo del bloqueo estricto."
        if (Method.PIN in methods && (pin1.length !in 4..12 || pin1 != pin2)) return "El NIP debe tener 4 a 12 dígitos y coincidir."
        if (Method.PASSWORD in methods && (pw1.length < 6 || pw1 != pw2)) return "La contraseña debe tener 6+ caracteres y coincidir."
        if (Method.QR in methods && (qrSecret == null || !qrSaved)) return "Genera el QR y guárdalo/compártelo antes de continuar."
        if (Method.BIOMETRIC in methods) BioGate.status(ctx, bioKind)?.let { return it }
        return null
    }

    fun save(strictUntil: Long) {
        val salt = Crypto.newSalt()
        val id = UUID.randomUUID().toString()
        var alias: String? = null
        if (Method.BIOMETRIC in methods && bioKind == BioKind.HUELLAS_ACTUALES) {
            alias = "candado_bio_$id"
            try { BioGate.createKey(alias) } catch (e: Exception) {
                toast(ctx, "No se pudo ligar a los biométricos actuales: ${e.message}"); return
            }
        }
        val lock = Lock(
            id = id, nombre = nombre.ifBlank { "Candado" }, targets = targets, methods = methods,
            requireAll = requireAll, createdAt = Guard.now(), strictUntil = strictUntil, passMinutes = passMin,
            salt = salt,
            pinHash = if (Method.PIN in methods) Crypto.hash(pin1, salt) else null,
            pwdHash = if (Method.PASSWORD in methods) Crypto.hash(pw1, salt) else null,
            qrHash = if (Method.QR in methods) qrSecret?.let { Crypto.hash(it, salt) } else null,
            bioKind = if (Method.BIOMETRIC in methods) bioKind else null,
            bioAlias = alias, randomLen = randomLen, cooldownHours = cooldownH
        )
        Store.addLock(lock)
        if (Target.FILTRO in targets && !Store.filterEnabled) toast(ctx, "Recuerda encender el filtro en la pestaña Filtro.")
        if (Target.APPS in targets && Store.blockedApps.isEmpty()) toast(ctx, "Recuerda elegir apps en la pestaña Apps.")
        OwnerPolicies.apply(ctx)
        onDone()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), label = { Text("Nombre del candado") }, singleLine = true)

        SectionTitle("1. ¿Qué va a proteger?")
        Target.entries.forEach { t ->
            CheckRow(t.titulo, t.desc, t in targets) { targets = if (t in targets) targets - t else targets + t }
        }

        SectionTitle("2. ¿Con qué se abre?")
        Hint("Puedes elegir varios. Sin ninguno, solo se abre cuando termine el bloqueo estricto.")
        Method.entries.forEach { m ->
            CheckRow(m.titulo, m.desc, m in methods) { methods = toggle(methods, m) }
            if (m in methods) {
                Column(Modifier.padding(start = 36.dp, bottom = 8.dp)) {
                    when (m) {
                        Method.PIN -> {
                            SecretField(pin1, { pin1 = it.filter(Char::isDigit).take(12) }, "NIP", true)
                            SecretField(pin2, { pin2 = it.filter(Char::isDigit).take(12) }, "Repite el NIP", true)
                        }
                        Method.PASSWORD -> {
                            SecretField(pw1, { pw1 = it }, "Contraseña", false)
                            SecretField(pw2, { pw2 = it }, "Repite la contraseña", false)
                            Hint("Tip: pídele a alguien de confianza que la escriba él y no te la diga.")
                        }
                        Method.QR -> {
                            if (qrBmp == null) {
                                Button(onClick = {
                                    val s = Crypto.newQrSecret(); qrSecret = s
                                    qrBmp = Qr.bitmap(s, nombre); qrSaved = false
                                }) { Text("Generar QR") }
                            } else {
                                Image(qrBmp!!.asImageBitmap(), null, Modifier.size(220.dp))
                                Row {
                                    TextButton(onClick = {
                                        val uri = Qr.saveToGallery(ctx, qrBmp!!)
                                        if (uri != null) {
                                            qrSaved = true
                                            ctx.startActivity(Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Enviar QR a…"))
                                        } else toast(ctx, "Toma captura de pantalla del QR y envíala.")
                                    }) { Text("Guardar y enviar") }
                                    TextButton(onClick = { qrSaved = true }) { Text("Ya le tomé foto") }
                                }
                                Hint("Imprímelo o envíalo a alguien de confianza y BÓRRALO de este celular (Galería > Candado). Si queda aquí, no sirve como candado. Si lo pierdes, no hay forma de recuperarlo.", Danger)
                            }
                        }
                        Method.BIOMETRIC -> {
                            BioKind.entries.forEach { k ->
                                Row(
                                    Modifier.fillMaxWidth().selectable(bioKind == k, onClick = { bioKind = k }).padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    RadioButton(bioKind == k, null)
                                    Column(Modifier.padding(start = 6.dp)) {
                                        Text(k.titulo, fontWeight = FontWeight.Medium)
                                        Hint(k.desc)
                                    }
                                }
                            }
                            BioGate.status(ctx, bioKind)?.let { Hint(it, Danger) }
                            Hint("Android no permite elegir un dedo en particular; para eso usa \"registrados HOY\" y registra solo el dedo que quieras antes de crear el candado.")
                        }
                        Method.RANDOM_TEXT -> {
                            Options(listOf(30, 60, 120, 250), randomLen, { "$it letras" }) { randomLen = it }
                        }
                        Method.COOLDOWN -> {
                            Options(listOf(1, 6, 24, 72, 168), cooldownH, { if (it < 24) "$it h" else "${it / 24} d" }) { cooldownH = it }
                            Hint("Pides abrirlo y tienes que volver después de ese tiempo.")
                        }
                    }
                }
            }
        }
        if (methods.size > 1) {
            SectionTitle("3. ¿Pedir todos o basta uno?")
            RadioRow("Pedir TODOS los métodos elegidos (más seguro)", requireAll) { requireAll = true }
            RadioRow("Basta con UNO de ellos", !requireAll) { requireAll = false }
        }

        SectionTitle("Al abrirlo, ¿cuánto tiempo queda abierto?")
        Options(listOf(5, 15, 30, 60, 120), passMin, { "$it min" }) { passMin = it }
        Hint("Después se vuelve a cerrar solo.")

        SectionTitle("Bloqueo estricto")
        StrictPicker(strict, Guard.now())

        Spacer(Modifier.height(16.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val err = validate()
            if (err != null) toast(ctx, err)
            else {
                val until = strict.untilFrom(Guard.now())
                if (until != null) confirmUntil = until else save(0L)
            }
        }) { Text("Poner candado") }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        Spacer(Modifier.height(40.dp))
    }

    confirmUntil?.let { u ->
        StrictConfirmDialog(u, onDismiss = { confirmUntil = null }, onConfirm = { confirmUntil = null; save(u) })
    }
}

@Composable
private fun SecretField(v: String, on: (String) -> Unit, label: String, numeric: Boolean) {
    OutlinedTextField(
        v, on, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password)
    )
}

@Composable
fun CheckRow(title: String, desc: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(checked, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked, null)
        Column(Modifier.padding(start = 6.dp, top = 2.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Hint(desc)
        }
    }
}

@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null); Spacer(Modifier.width(6.dp)); Text(title)
    }
}

@Composable
fun <T> Options(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { v ->
            if (v == selected) Button(onClick = { onSelect(v) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label(v)) }
            else OutlinedButton(onClick = { onSelect(v) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label(v)) }
        }
    }
}
