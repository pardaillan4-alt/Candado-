package com.candado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.data.Store
import com.candado.app.data.Target
import com.candado.app.service.FilterVpnService
import kotlinx.coroutines.delay

@Composable
fun rememberTicker(periodMs: Long = 30_000): Long {
    var t by remember { mutableLongStateOf(Guard.now()) }
    LaunchedEffect(Unit) { while (true) { delay(periodMs); t = Guard.now() } }
    return t
}

@Composable
fun HomeScreen(onGo: (Int) -> Unit, onNewLock: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    val now = rememberTicker()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val missing = Perms.missing(ctx)
        if (missing.isNotEmpty()) {
            InfoCard {
                Text("⚠️ Faltan permisos", fontWeight = FontWeight.Bold, color = Danger)
                missing.forEach { Hint("• $it") }
                Button(onClick = { onGo(5) }) { Text("Configurar permisos") }
            }
        }

        SectionTitle("Protecciones")
        Target.entries.forEach { t ->
            val locks = Guard.locksFor(t)
            val strict = locks.maxOfOrNull { it.strictUntil } ?: 0L
            val activo = when (t) {
                Target.APPS -> Store.blockedApps.isNotEmpty()
                Target.FILTRO -> Store.filterEnabled
                Target.PALABRAS -> Store.keywordsEnabled
                Target.RUTINA -> com.candado.app.core.Routine.actividades().isNotEmpty()
                else -> locks.isNotEmpty()
            }
            InfoCard {
                Row {
                    Text(t.titulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            !activo -> "Apagado"
                            Guard.paused(t) -> "Pausado"
                            else -> "Activo"
                        },
                        color = if (activo && !Guard.paused(t)) Ok else Danger, fontSize = 14.sp
                    )
                }
                when {
                    strict > now -> Hint("🔒 Estricto: faltan ${fmtRemaining(strict - now)} (hasta ${fmtDate(strict)})", Danger)
                    locks.isNotEmpty() -> Hint("🔒 Con ${locks.size} candado(s)")
                    else -> Hint("Sin candado: cualquiera puede cambiarlo.")
                }
                if (t == Target.FILTRO && Store.filterEnabled) {
                    Hint(if (FilterVpnService.running) "Filtro funcionando" else "El filtro no está corriendo; abre Permisos.")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onNewLock, modifier = Modifier.fillMaxWidth()) { Text("Poner un candado nuevo") }
        Spacer(Modifier.height(8.dp))
        if (OwnerPolicies.isOwner(ctx)) Hint("🛡️ Modo blindado activo (propietario del dispositivo).", Ok)
        else Hint("Consejo: activa el Modo blindado en Permisos para que ni el modo seguro ni restablecer de fábrica puedan quitar Candado.")
    }
}
