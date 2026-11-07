package com.candado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

enum class TimeUnitEs(val titulo: String, val field: Int) {
    HORAS("horas", Calendar.HOUR_OF_DAY),
    DIAS("días", Calendar.DAY_OF_YEAR),
    SEMANAS("semanas", Calendar.WEEK_OF_YEAR),
    MESES("meses", Calendar.MONTH),
    ANIOS("años", Calendar.YEAR)
}

fun addDuration(from: Long, amount: Int, unit: TimeUnitEs): Long =
    Calendar.getInstance().apply { timeInMillis = from; add(unit.field, amount) }.timeInMillis

private val PRESETS = listOf(
    "1 hora" to (1 to TimeUnitEs.HORAS), "1 día" to (1 to TimeUnitEs.DIAS), "1 semana" to (1 to TimeUnitEs.SEMANAS),
    "1 mes" to (1 to TimeUnitEs.MESES), "3 meses" to (3 to TimeUnitEs.MESES), "6 meses" to (6 to TimeUnitEs.MESES),
    "1 año" to (1 to TimeUnitEs.ANIOS), "2 años" to (2 to TimeUnitEs.ANIOS), "5 años" to (5 to TimeUnitEs.ANIOS)
)

/** Estado del selector: null = sin estricto. */
class StrictState {
    var enabled by mutableStateOf(false)
    var preset by mutableIntStateOf(6)          // índice en PRESETS, -1 = personalizado
    var customAmount by mutableStateOf("18")
    var customUnit by mutableStateOf(TimeUnitEs.MESES)

    fun untilFrom(base: Long): Long? {
        if (!enabled) return null
        val (amount, unit) = if (preset >= 0) PRESETS[preset].second
        else (customAmount.toIntOrNull()?.takeIf { it in 1..100_000 } ?: return null) to customUnit
        return addDuration(base, amount, unit)
    }
}

@Composable
fun StrictPicker(state: StrictState, base: Long, allowDisable: Boolean = true) {
    if (allowDisable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bloqueo estricto (nada lo abre hasta que acabe el tiempo)", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = state.enabled, onCheckedChange = { state.enabled = it })
        }
    }
    if (!state.enabled) return
    PRESETS.forEachIndexed { i, (label, _) ->
        Row(
            Modifier.fillMaxWidth().selectable(state.preset == i, onClick = { state.preset = i }).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { RadioButton(state.preset == i, null); Spacer(Modifier.width(6.dp)); Text(label) }
    }
    Row(
        Modifier.fillMaxWidth().selectable(state.preset == -1, onClick = { state.preset = -1 }).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { RadioButton(state.preset == -1, null); Spacer(Modifier.width(6.dp)); Text("Personalizado (cualquier tiempo)") }
    if (state.preset == -1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                state.customAmount, { v -> state.customAmount = v.filter { it.isDigit() }.take(6) },
                Modifier.width(110.dp), label = { Text("Cantidad") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                TimeUnitEs.entries.forEach { u ->
                    Row(
                        Modifier.selectable(state.customUnit == u, onClick = { state.customUnit = u }),
                        verticalAlignment = Alignment.CenterVertically
                    ) { RadioButton(state.customUnit == u, null); Text(u.titulo) }
                }
            }
        }
    }
    val until = state.untilFrom(base)
    if (until != null) Hint("Terminará: ${fmtDate(until)}  (${fmtRemaining(until - base)})", Danger)
    else Hint("Escribe una cantidad válida.", Danger)
}

/** Confirmación fuerte: hay que escribir ACEPTO. */
@Composable
fun StrictConfirmDialog(until: Long, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ Bloqueo estricto") },
        text = {
            Column {
                Text("Hasta el ${fmtDate(until)} NINGÚN método (NIP, QR, huella, nada) podrá abrir este candado, ni siquiera tú. Cambiar la hora del teléfono no lo acorta.")
                Spacer(Modifier.height(6.dp))
                Text("Las llamadas, mensajes y apps no bloqueadas siguen funcionando normal.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(typed, { typed = it }, label = { Text("Escribe ACEPTO") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = typed.trim().equals("ACEPTO", true), onClick = onConfirm) { Text("Bloquear", color = Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Hacer estricto o extender un candado existente (siempre permitido: es endurecer). */
@Composable
fun StrictPickerDialog(title: String, baseFrom: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val st = remember { StrictState().apply { enabled = true } }
    var confirm by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Hint("Se suma desde ${fmtDate(baseFrom)}.")
                StrictPicker(st, baseFrom, allowDisable = false)
            }
        },
        confirmButton = { TextButton(onClick = { st.untilFrom(baseFrom)?.let { confirm = it } }) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    confirm?.let { u -> StrictConfirmDialog(u, onDismiss = { confirm = null }, onConfirm = { onConfirm(u) }) }
}
