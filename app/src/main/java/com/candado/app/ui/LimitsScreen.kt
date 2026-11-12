package com.candado.app.ui

import android.content.Intent
import android.provider.Settings
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
import com.candado.app.core.Guard
import com.candado.app.core.Limite
import com.candado.app.core.Limits
import com.candado.app.data.Store
import com.candado.app.data.Target
import java.util.UUID

private val DIAS_L = listOf(2 to "L", 3 to "M", 4 to "Mi", 5 to "J", 6 to "V", 7 to "S", 1 to "D")

@Composable
fun LimitsScreen() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    @Suppress("UNUSED_VARIABLE") val now = rememberTicker(15_000)
    var editing by remember { mutableStateOf<Limite?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val abierto = Guard.isOpen(Target.LIMITES)

    editing?.let { l ->
        LimiteEditor(l, isNew, onDone = { editing = null })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Hint("Limita apps a partir de una hora. Ejemplo: YouTube desde las 21:00 hasta las 06:00, solo 30 minutos en total. Con 0 minutos, la app queda bloqueada todo ese horario.")
        if (!Limits.permisoUso(ctx)) {
            InfoCard {
                Text("Falta el permiso de datos de uso", fontWeight = FontWeight.Bold, color = Danger)
                Hint("Sin él, Candado no puede contar minutos y bloquea la app todo el horario. Busca \"Candado\" en la lista y actívalo.")
                OutlinedButton(onClick = { try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) {} }) { Text("Dar permiso") }
            }
        }
        if (!abierto) Hint("🔒 Con candado: puedes agregar límites nuevos, pero no borrar ni cambiar los que ya existen.", Danger)

        Button(onClick = {
            isNew = true
            editing = Limite(UUID.randomUUID().toString(), "", emptySet(), 21 * 60, 6 * 60, 30, (1..7).toSet())
        }, modifier = Modifier.fillMaxWidth()) { Text("+ Nuevo límite") }

        val pm = ctx.packageManager
        fun label(p: String) = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }
            catch (e: Exception) { POPULAR_APPS.toMap()[p] ?: p }

        Limits.lista().forEach { l ->
            InfoCard {
                Text(l.nombre, fontWeight = FontWeight.Bold)
                Hint("Apps: " + l.apps.joinToString { label(it) })
                Hint("De ${Limits.hhmm(l.desde)} a ${Limits.hhmm(l.hasta)} · " +
                    (if (l.minutos <= 0) "bloqueadas" else "${l.minutos} min en total") + " · " +
                    (if (l.dias.size == 7) "todos los días" else DIAS_L.filter { it.first in l.dias }.joinToString(" ") { it.second }))
                Text(Limits.estado(ctx, l), color = if (Limits.inicioVentana(l) != null) Danger else Ok)
                Row {
                    TextButton(onClick = {
                        if (!abierto) toast(ctx, "Candado activo en Límites: ábrelo para cambiar este límite.")
                        else { isNew = false; editing = l }
                    }) { Text("Editar") }
                    TextButton(onClick = {
                        if (!abierto) toast(ctx, "Candado activo en Límites: ábrelo para borrar.")
                        else Limits.guardar(Limits.lista().filterNot { it.id == l.id })
                    }) { Text("Borrar", color = Danger) }
                }
            }
        }
    }
}

@Composable
private fun LimiteEditor(orig: Limite, isNew: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var nombre by remember { mutableStateOf(orig.nombre) }
    var apps by remember { mutableStateOf(orig.apps) }
    var dh by remember { mutableStateOf("%02d".format(orig.desde / 60)) }
    var dm by remember { mutableStateOf("%02d".format(orig.desde % 60)) }
    var hh by remember { mutableStateOf("%02d".format(orig.hasta / 60)) }
    var hm by remember { mutableStateOf("%02d".format(orig.hasta % 60)) }
    var minutos by remember { mutableStateOf(orig.minutos.toString()) }
    var dias by remember { mutableStateOf(orig.dias) }
    var query by remember { mutableStateOf("") }
    val pm = ctx.packageManager
    val todas = remember {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val inst = pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.filter { it.first != ctx.packageName }
        val have = inst.map { it.first }.toSet()
        (inst + POPULAR_APPS.filter { it.first !in have }.map { it.first to it.second + " · no instalada" })
            .sortedBy { it.second.lowercase() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle(if (isNew) "Nuevo límite" else "Editar límite")
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre (ej. YouTube de noche)") })

        SectionTitle("Apps")
        Hint("Si eliges varias, el tiempo se comparte entre todas.")
        apps.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• " + (todas.firstOrNull { it.first == p }?.second ?: p), Modifier.weight(1f))
                TextButton(onClick = { apps = apps - p }) { Text("Quitar") }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar app (ej. YouTube)") })
        if (query.length >= 2) {
            todas.filter { it.second.contains(query, true) && it.first !in apps }.take(6).forEach { (p, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l, Modifier.weight(1f))
                    TextButton(onClick = { apps = apps + p; query = ""; if (nombre.isBlank()) nombre = l.substringBefore(" ·") }) { Text("Agregar") }
                }
            }
        }

        SectionTitle("Horario")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Desde ", Modifier.width(60.dp))
            NumField(dh, { dh = it }, "Hora"); Text(" : "); NumField(dm, { dm = it }, "Min")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hasta ", Modifier.width(60.dp))
            NumField(hh, { hh = it }, "Hora"); Text(" : "); NumField(hm, { hm = it }, "Min")
        }
        Hint("Formato 24 h. Si \"hasta\" es menor que \"desde\" (ej. 21:00 a 06:00) cruza la medianoche. Si son iguales, aplica las 24 horas.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DIAS_L.forEach { (d, l) ->
                if (d in dias) Button(onClick = { dias = dias - d }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(l) }
                else OutlinedButton(onClick = { dias = dias + d }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(l) }
            }
        }

        SectionTitle("Tiempo permitido en ese horario")
        OutlinedTextField(
            minutos, { v -> minutos = v.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Minutos (0 = bloqueada)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Options(listOf(0, 15, 30, 60, 120), minutos.toIntOrNull() ?: -1, { if (it == 0) "Bloquear" else "$it min" }) { minutos = it.toString() }

        Spacer(Modifier.height(16.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val d = dh.toIntOrNull(); val dmi = dm.toIntOrNull(); val h = hh.toIntOrNull(); val hmi = hm.toIntOrNull()
            val min = minutos.toIntOrNull()
            when {
                apps.isEmpty() -> toast(ctx, "Agrega al menos una app.")
                d == null || d !in 0..23 || dmi == null || dmi !in 0..59 || h == null || h !in 0..23 || hmi == null || hmi !in 0..59 ->
                    toast(ctx, "Revisa las horas (0-23) y minutos (0-59).")
                min == null || min > 1440 -> toast(ctx, "Minutos de 0 a 1440.")
                dias.isEmpty() -> toast(ctx, "Elige al menos un día.")
                else -> {
                    val n = orig.copy(
                        nombre = nombre.trim().ifEmpty { "Límite" }, apps = apps,
                        desde = d * 60 + dmi, hasta = h * 60 + hmi, minutos = min, dias = dias
                    )
                    val l = Limits.lista()
                    Limits.guardar(if (isNew) l + n else l.map { if (it.id == n.id) n else it })
                    onDone()
                }
            }
        }) { Text("Guardar") }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun NumField(v: String, on: (String) -> Unit, label: String) {
    OutlinedTextField(
        v, { x -> on(x.filter(Char::isDigit).take(2)) }, Modifier.width(80.dp), singleLine = true,
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
