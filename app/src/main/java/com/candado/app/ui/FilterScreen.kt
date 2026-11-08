package com.candado.app.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.data.Store
import com.candado.app.data.Target
import com.candado.app.data.Upstream
import com.candado.app.service.FilterVpnService

@Composable
fun FilterScreen() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    val filtroOpen = Guard.isOpen(Target.FILTRO)
    val palabrasOpen = Guard.isOpen(Target.PALABRAS)

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) { Store.filterEnabled = true; FilterVpnService.start(ctx); OwnerPolicies.apply(ctx) }
        else toast(ctx, "Sin ese permiso el filtro no puede funcionar.")
    }

    fun loosen(open: Boolean, action: () -> Unit) {
        if (open) action() else toast(ctx, "Candado activo: ábrelo primero para aflojar este ajuste.")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle("Filtro de internet")
        if (!filtroOpen) Hint("🔒 Con candado: solo puedes endurecerlo.", Danger)
        SwitchRow("Bloquear contenido para adultos en todo el celular", Store.filterEnabled) { on ->
            if (on) {
                val i = VpnService.prepare(ctx)
                if (i != null) vpnLauncher.launch(i) else { Store.filterEnabled = true; FilterVpnService.start(ctx); OwnerPolicies.apply(ctx) }
            } else loosen(filtroOpen) { Store.filterEnabled = false; FilterVpnService.stop(ctx); OwnerPolicies.apply(ctx) }
        }
        SwitchRow("Forzar búsqueda segura (Google, YouTube restringido, Bing)", Store.safeSearch) { on ->
            if (on) Store.safeSearch = true else loosen(filtroOpen) { Store.safeSearch = false }
        }
        SwitchRow("Bloquear \"DNS seguro\" de navegadores (evita que se salten el filtro)", Store.blockDoh) { on ->
            if (on) Store.blockDoh = true else loosen(filtroOpen) { Store.blockDoh = false }
        }

        SectionTitle("Servidor de filtrado")
        Upstream.entries.forEach { u ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = Store.upstream == u, onClick = {
                    // Cambiar de servidor se permite solo con el filtro abierto (algunos son menos estrictos).
                    loosen(filtroOpen) { Store.upstream = u }
                }).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = Store.upstream == u, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(u.titulo)
            }
        }

        if (Store.upstream == Upstream.NEXTDNS) {
            var idInput by remember { mutableStateOf(Store.nextDnsId) }
            InfoCard {
                Text("Tu NextDNS")
                Hint("Escribe tu ID (6 letras/números) o pega tu nombre de DNS privado, ej. abc123.dns.nextdns.io. Lo ves en my.nextdns.io > Configuración.")
                OutlinedTextField(idInput, { idInput = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("ID de NextDNS") })
                Button(onClick = {
                    val id = Store.parseNextDnsId(idInput)
                    when {
                        id == null -> toast(ctx, "No reconozco ese ID. Debe tener 6 caracteres, ej. abc123.")
                        id != Store.nextDnsId && !filtroOpen -> toast(ctx, "Candado activo: ábrelo para cambiar el ID.")
                        else -> { Store.nextDnsId = id; idInput = id; toast(ctx, "NextDNS $id guardado") }
                    }
                }) { Text("Guardar") }
                if (Store.nextDnsId.isEmpty()) Hint("Mientras no guardes el ID se usa Cloudflare Familia.", Danger)
                else Hint("Activo: ${Store.nextDnsId}. Si NextDNS no responde, se usa Cloudflare Familia de respaldo. Con candado en el filtro, el panel de NextDNS queda bloqueado.", Ok)
            }
        }

        SectionTitle("Sitios bloqueados extra")
        Hint("Escribe dominios como: tiktok.com, instagram.com. Bloquea también sus subdominios.")
        EditableList(
            items = Store.blockedDomains.sorted(),
            placeholder = "ejemplo.com",
            onAdd = { d ->
                val clean = d.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
                if (clean.contains('.')) Store.blockedDomains = Store.blockedDomains + clean
                else toast(ctx, "Escribe un dominio válido, ej. sitio.com")
            },
            onRemove = { d -> loosen(filtroOpen) { Store.blockedDomains = Store.blockedDomains - d } }
        )

        SectionTitle("Apps de trabajo sin filtro")
        Hint("Si una app de trabajo (punto de venta, banco, terminal) no se conecta con el filtro encendido, agrégala aquí: se conectará directo. No agregues navegadores. Agregar requiere el candado del filtro abierto; quitar siempre se puede.")
        WorkAppsPicker(filtroOpen)

        SectionTitle("Palabras prohibidas en pantalla")
        if (!palabrasOpen) Hint("🔒 Con candado: puedes agregar palabras, no quitarlas.", Danger)
        SwitchRow("Cerrar cualquier pantalla que muestre estas palabras", Store.keywordsEnabled) { on ->
            if (on) Store.keywordsEnabled = true else loosen(palabrasOpen) { Store.keywordsEnabled = false }
        }
        Hint("Funciona en navegadores, redes sociales y cualquier app. Requiere el Vigilante (Permisos).")
        EditableList(
            items = Store.keywords.sorted(),
            placeholder = "palabra o frase",
            onAdd = { w -> if (w.trim().length >= 3) Store.keywords = Store.keywords + w.trim().lowercase() else toast(ctx, "Mínimo 3 letras") },
            onRemove = { w -> loosen(palabrasOpen) { Store.keywords = Store.keywords - w } }
        )
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun EditableList(items: List<String>, placeholder: String, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(placeholder) }, singleLine = true)
        Spacer(Modifier.width(8.dp))
        Button(onClick = { if (input.isNotBlank()) { onAdd(input); input = "" } }) { Text("Agregar") }
    }
    items.forEach { w ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("• $w", Modifier.weight(1f))
            TextButton(onClick = { onRemove(w) }) { Text("Quitar") }
        }
    }
}


private val BROWSER_HINTS = listOf("chrome", "firefox", "browser", "opera", "brave", "edge", "duckduckgo", "torproject", "samsung.android.sbrowser", "UCMobile", "kiwi", "vivaldi")

@Composable
private fun WorkAppsPicker(filtroOpen: Boolean) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val pm = ctx.packageManager
    val installed = remember {
        val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.filter { it.first != ctx.packageName }.sortedBy { it.second.lowercase() }
    }
    val excluded = Store.vpnExcluded
    excluded.forEach { pkg ->
        val label = installed.firstOrNull { it.first == pkg }?.second ?: pkg
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("• $label", Modifier.weight(1f))
            TextButton(onClick = { Store.vpnExcluded = Store.vpnExcluded - pkg; FilterVpnService.restart(ctx) }) { Text("Quitar") }
        }
    }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar app para excluir") })
    if (query.length >= 2) {
        installed.filter { it.second.contains(query, true) && it.first !in excluded }.take(6).forEach { (pkg, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                TextButton(onClick = {
                    when {
                        BROWSER_HINTS.any { pkg.contains(it, true) } -> toast(ctx, "Los navegadores no se pueden excluir: eso abriría un hueco en el filtro.")
                        !filtroOpen -> toast(ctx, "Candado activo: abre el candado del filtro para excluir una app.")
                        else -> { Store.vpnExcluded = Store.vpnExcluded + pkg; FilterVpnService.restart(ctx); query = ""; toast(ctx, "$label ya no pasa por el filtro") }
                    }
                }) { Text("Excluir") }
            }
        }
    }
}
