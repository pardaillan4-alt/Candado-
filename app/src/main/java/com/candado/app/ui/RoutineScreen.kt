package com.candado.app.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.candado.app.core.Actividad
import com.candado.app.core.Guard
import com.candado.app.core.Routine
import com.candado.app.data.Store
import com.candado.app.data.Target
import java.util.UUID

private val DIAS = listOf(2 to "L", 3 to "M", 4 to "Mi", 5 to "J", 6 to "V", 7 to "S", 1 to "D")

@Composable
fun RoutineScreen() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    val now = rememberTicker(5_000)
    var editing by remember { mutableStateOf<Actividad?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val abierto = Guard.isOpen(Target.RUTINA)
    val ses = Routine.sesion()

    editing?.let { a ->
        ActividadEditor(a, isNew, abierto, onDone = { editing = null })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (ses != null) {
            InfoCard {
                Text("En curso: ${ses.nombre}", fontWeight = FontWeight.Bold, color = Danger)
                Hint(if (now < ses.fin) "Faltan ${fmtRemaining(ses.fin - now)}" else "Ya puedes marcarla como terminada.")
                Button(onClick = { ctx.startActivity(Intent(ctx, FocusActivity::class.java)) }) { Text("Ver actividad") }
            }
        }
        Hint("Durante una actividad el celular solo deja usar WhatsApp, llamadas, mensajes, reloj y las apps que elijas para esa actividad. Se libera cuando pasa el tiempo mínimo y tocas \"Ya terminé\". Requiere el Vigilante (pestaña Permisos).")
        if (!abierto) Hint("🔒 Rutina con candado: puedes agregar actividades, pero no borrarlas ni acortarlas.", Danger)

        Button(onClick = {
            isNew = true
            editing = Actividad(UUID.randomUUID().toString(), "", 20, false, 22 * 60, (1..7).toSet(), emptySet())
        }, modifier = Modifier.fillMaxWidth()) { Text("+ Nueva actividad") }

        val lista = Routine.actividades()
        if (lista.none { it.nombre.contains("Rosario", true) }) {
            OutlinedButton(onClick = {
                Routine.guardar(lista + Actividad(UUID.randomUUID().toString(), "Santo Rosario", 20, false, 22 * 60, (1..7).toSet(), emptySet()))
            }, modifier = Modifier.fillMaxWidth()) { Text("Agregar ejemplo: Santo Rosario (20 min)") }
        }

        lista.forEach { a ->
            InfoCard {
                Text(a.nombre, fontWeight = FontWeight.Bold)
                Hint("Duración mínima: ${a.minutos} min")
                Hint(if (a.auto) "Empieza sola: ${Routine.proxima(a)}" else "La inicias tú cuando quieras")
                if (a.appsExtra.isNotEmpty()) Hint("Apps permitidas extra: ${a.appsExtra.size}")
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    if (ses == null) TextButton(onClick = {
                        if (Routine.iniciar(a)) ctx.startActivity(Intent(ctx, FocusActivity::class.java))
                    }) { Text("Iniciar ahora") }
                    TextButton(onClick = { isNew = false; editing = a }) { Text("Editar") }
                    TextButton(onClick = {
                        if (!abierto) toast(ctx, "Candado activo en la Rutina: ábrelo para borrar actividades.")
                        else Routine.guardar(Routine.actividades().filterNot { it.id == a.id })
                    }) { Text("Borrar", color = Danger) }
                }
            }
        }
    }
}

@Composable
private fun ActividadEditor(orig: Actividad, isNew: Boolean, abierto: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var nombre by remember { mutableStateOf(orig.nombre) }
    var minutos by remember { mutableStateOf(orig.minutos.toString()) }
    var auto by remember { mutableStateOf(orig.auto) }
    var hh by remember { mutableStateOf("%02d".format(orig.hora / 60)) }
    var mm by remember { mutableStateOf("%02d".format(orig.hora % 60)) }
    var dias by remember { mutableStateOf(orig.dias) }
    var apps by remember { mutableStateOf(orig.appsExtra) }
    var query by remember { mutableStateOf("") }
    val pm = ctx.packageManager
    val installed = remember {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.filter { it.first != ctx.packageName }.sortedBy { it.second.lowercase() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle(if (isNew) "Nueva actividad" else "Editar actividad")
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), label = { Text("Nombre (ej. Santo Rosario)") }, singleLine = true)
        OutlinedTextField(
            minutos, { v -> minutos = v.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(),
            label = { Text("Duración mínima en minutos") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Hint("No podrás marcarla como terminada antes de este tiempo.")

        SectionTitle("¿Cómo empieza?")
        RadioRow("La inicio yo cuando quiera", !auto) { auto = false }
        RadioRow("Empieza sola a una hora", auto) { auto = true }
        if (auto) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(hh, { v -> hh = v.filter(Char::isDigit).take(2) }, Modifier.width(80.dp), label = { Text("Hora") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("  :  ")
                OutlinedTextField(mm, { v -> mm = v.filter(Char::isDigit).take(2) }, Modifier.width(80.dp), label = { Text("Min") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Hint("Formato 24 h: 22:00 son las 10 de la noche.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DIAS.forEach { (d, l) ->
                    if (d in dias) Button(onClick = { dias = dias - d }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(l) }
                    else OutlinedButton(onClick = { dias = dias + d }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(l) }
                }
            }
        }

        SectionTitle("Apps permitidas durante la actividad")
        Hint("Siempre se permiten: WhatsApp, Teléfono, Mensajes, Contactos y Reloj. Agrega aquí las que necesites para la actividad (ej. una app del Rosario o la Biblia).")
        apps.forEach { pkg ->
            val label = installed.firstOrNull { it.first == pkg }?.second ?: pkg
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• $label", Modifier.weight(1f))
                TextButton(onClick = { apps = apps - pkg }) { Text("Quitar") }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar app para permitir") })
        if (query.length >= 2) {
            installed.filter { it.second.contains(query, true) && it.first !in apps }.take(6).forEach { (pkg, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    TextButton(onClick = { apps = apps + pkg; query = "" }) { Text("Permitir") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val min = minutos.toIntOrNull()
            val h = hh.toIntOrNull(); val m = mm.toIntOrNull()
            val nueva = orig.copy(
                nombre = nombre.trim(), minutos = min ?: 0, auto = auto,
                hora = ((h ?: 0) * 60 + (m ?: 0)), dias = dias, appsExtra = apps
            )
            // Con candado: no se puede aflojar (acortar, quitar horario/días, permitir más apps).
            val afloja = !isNew && (nueva.minutos < orig.minutos || (orig.auto && !nueva.auto) ||
                !nueva.dias.containsAll(orig.dias) || !orig.appsExtra.containsAll(nueva.appsExtra) || nueva.hora != orig.hora)
            when {
                nueva.nombre.isEmpty() -> toast(ctx, "Escribe un nombre.")
                min == null || min !in 1..600 -> toast(ctx, "La duración debe ser de 1 a 600 minutos.")
                auto && (h == null || h !in 0..23 || m == null || m !in 0..59) -> toast(ctx, "Revisa la hora (0-23) y minutos (0-59).")
                auto && dias.isEmpty() -> toast(ctx, "Elige al menos un día.")
                afloja && !abierto -> toast(ctx, "Candado activo en la Rutina: no se puede acortar ni aflojar esta actividad.")
                else -> {
                    val l = Routine.actividades()
                    Routine.guardar(if (isNew) l + nueva else l.map { if (it.id == nueva.id) nueva else it })
                    onDone()
                }
            }
        }) { Text("Guardar") }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        Spacer(Modifier.height(40.dp))
    }
}
