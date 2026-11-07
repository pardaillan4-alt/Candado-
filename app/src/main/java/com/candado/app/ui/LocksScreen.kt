package com.candado.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.candado.app.core.BioGate
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.data.Lock
import com.candado.app.data.Store

@Composable
fun LocksScreen(onNewLock: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    val now = rememberTicker(15_000)
    var extending by remember { mutableStateOf<Lock?>(null) }
    var deleting by remember { mutableStateOf<Lock?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = onNewLock, modifier = Modifier.fillMaxWidth()) { Text("+ Poner candado nuevo") }
        val locks = Store.locks()
        if (locks.isEmpty()) Hint("Aún no tienes candados. Primero elige apps (pestaña Apps) o enciende el filtro, y luego ponles candado.")

        locks.forEach { l ->
            InfoCard {
                Text(l.nombre, fontWeight = FontWeight.Bold)
                Hint("Protege: " + l.targets.joinToString { it.titulo })
                Hint(
                    if (l.methods.isEmpty()) "Se abre: solo cuando termine el tiempo"
                    else "Se abre con: " + l.methods.joinToString(if (l.requireAll) " + " else " o ") { it.titulo } +
                        (if (l.bioKind != null) " (${l.bioKind.titulo})" else "")
                )
                when {
                    Guard.isStrict(l, now) -> Hint("🔒 ESTRICTO: faltan ${fmtRemaining(l.strictUntil - now)}\nTermina: ${fmtDate(l.strictUntil)}", Danger)
                    Guard.isPassActive(l, now) -> Hint("🔓 Abierto por ${fmtRemaining(l.passUntil - now)} más", Ok)
                    else -> Hint("🔒 Cerrado")
                }
                Row {
                    if (!Guard.isStrict(l, now) && l.methods.isNotEmpty() && !Guard.isPassActive(l, now)) {
                        TextButton(onClick = {
                            ctx.startActivity(Intent(ctx, UnlockActivity::class.java).putExtra("lockId", l.id))
                        }) { Text("Abrir") }
                    }
                    TextButton(onClick = { extending = l }) { Text("Hacer estricto / extender") }
                    if (Guard.isPassActive(l, now)) {
                        TextButton(onClick = { deleting = l }) { Text("Eliminar", color = Danger) }
                        TextButton(onClick = { Store.updateLock(l.id) { it.copy(passUntil = 0) }; OwnerPolicies.apply(ctx) }) { Text("Cerrar ya") }
                    }
                }
            }
        }
    }

    extending?.let { l ->
        StrictPickerDialog(
            title = "Bloqueo estricto para \"${l.nombre}\"",
            baseFrom = maxOf(l.strictUntil, Guard.now()),
            onDismiss = { extending = null },
            onConfirm = { until ->
                Store.updateLock(l.id) { it.copy(strictUntil = maxOf(it.strictUntil, until), passUntil = 0) }
                OwnerPolicies.apply(ctx)
                extending = null
                toast(ctx, "Estricto hasta ${fmtDate(until)}")
            }
        )
    }

    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar \"${l.nombre}\"?") },
            text = { Text("Las partes que protegía quedarán sin candado.") },
            confirmButton = {
                TextButton(onClick = {
                    if (Store.lock(l.id)?.let { Guard.isPassActive(it) } == true) {
                        BioGate.deleteKey(l.bioAlias)
                        Store.removeLock(l.id)
                        OwnerPolicies.apply(ctx)
                    } else toast(ctx, "El candado ya se cerró otra vez.")
                    deleting = null
                }) { Text("Eliminar", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}
