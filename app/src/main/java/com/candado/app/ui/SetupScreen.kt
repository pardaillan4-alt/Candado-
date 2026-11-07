package com.candado.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.data.Store
import com.candado.app.data.Target
import com.candado.app.service.FilterVpnService
import com.candado.app.service.GuardAccessibilityService

object Perms {
    fun accessibilityOn(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(ctx.packageName + "/") || GuardAccessibilityService.running
    }

    fun batteryOk(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun missing(ctx: Context): List<String> = buildList {
        if (!accessibilityOn(ctx)) add("Vigilante (accesibilidad) apagado")
        if (!OwnerPolicies.isAdmin(ctx)) add("Protección contra desinstalación (administrador) apagada")
        if (Store.filterEnabled && VpnService.prepare(ctx) != null) add("Permiso del filtro (VPN) sin dar")
        if (!batteryOk(ctx)) add("Ahorro de batería puede detener Candado")
    }
}

@SuppressLint("BatteryLife")
@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    var refresh by remember { mutableIntStateOf(0) }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) FilterVpnService.start(ctx)
        refresh++
    }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    key(refresh) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Hint("Hazlo en este orden ANTES de poner candados en \"Ajustes críticos\" (después Candado cerrará estas pantallas).")

            StepCard(
                n = 1, title = "Vigilante (Accesibilidad)",
                done = Perms.accessibilityOn(ctx),
                desc = "Permite cerrar apps bloqueadas y pantallas de ajustes. En la lista busca \"Candado\" (a veces dentro de \"Apps instaladas\" o \"Servicios descargados\") y actívalo. En Android 13+ si sale \"Ajuste restringido\": ve a Ajustes > Apps > Candado > ⋮ > Permitir ajustes restringidos.",
                button = "Abrir Accesibilidad"
            ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

            StepCard(
                n = 2, title = "Protección contra desinstalación",
                done = OwnerPolicies.isAdmin(ctx),
                desc = "Activa a Candado como administrador del dispositivo para que no se pueda desinstalar sin abrir el candado.",
                button = "Activar"
            ) {
                adminLauncher.launch(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, OwnerPolicies.admin(ctx))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Evita que Candado se desinstale mientras tenga candados."))
            }

            StepCard(
                n = 3, title = "Permiso del filtro de internet",
                done = VpnService.prepare(ctx) == null,
                desc = "Android mostrará un aviso de \"conexión VPN\". Es local: tus datos no salen a ningún servidor de Candado; solo se revisan los nombres de los sitios.",
                button = "Dar permiso"
            ) {
                val i = VpnService.prepare(ctx)
                if (i != null) vpnLauncher.launch(i) else { FilterVpnService.start(ctx); toast(ctx, "Permiso ya concedido") }
            }

            StepCard(
                n = 4, title = "Que el ahorro de batería no lo apague",
                done = Perms.batteryOk(ctx),
                desc = "Permite que Candado siga funcionando en segundo plano. En Xiaomi/Redmi/POCO: en la pantalla que se abre ve a \"Ahorro de batería\" > \"Sin restricciones\" y activa \"Inicio automático\". Xiaomi a veces no avisa a la app, así que la palomita puede no salir aunque ya esté bien.",
                button = "Abrir ajustes de Candado"
            ) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                }
            }

            StepCard(
                n = 5, title = "Recomendado: VPN siempre activa",
                done = false,
                desc = "Opcional. En Ajustes > VPN > ⚙️ de Candado activa SOLO \"VPN siempre activa\". NO actives \"Bloquear conexiones sin VPN\": Candado solo filtra los nombres de sitios, no todo el tráfico, y esa opción corta el internet.",
                button = "Abrir ajustes de VPN"
            ) { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }

            SectionTitle("🛡️ Modo blindado (opcional, el más fuerte)")
            if (OwnerPolicies.isOwner(ctx)) {
                Hint("Activo. Android impide: modo seguro, restablecer de fábrica, desinstalar Candado, cambiar hora, DNS privado y VPN, mientras tengas candados en esas partes.", Ok)
                OutlinedButton(onClick = {
                    if (!Guard.isOpen(Target.AJUSTES)) toast(ctx, "Primero abre los candados de \"Ajustes críticos\".")
                    else { OwnerPolicies.removeOwner(ctx); refresh++; toast(ctx, "Modo blindado quitado") }
                }) { Text("Quitar modo blindado") }
            } else {
                InfoCard {
                    Text("Se hace UNA vez, desde el mismo celular, sin computadora:", fontWeight = FontWeight.SemiBold)
                    Hint("1. Ajustes > Acerca del teléfono > toca 7 veces \"Número de compilación\".")
                    Hint("2. Ajustes > Cuentas: quita temporalmente TODAS las cuentas (Google, Samsung, etc.). Android lo exige; luego las vuelves a agregar.")
                    Hint("3. Instala \"LADB\" o \"Shizuku + aShell\" (Play Store). Activa \"Depuración inalámbrica\" en Opciones de desarrollador y vincúlala con la app siguiendo sus pasos en pantalla dividida.")
                    Hint("4. Pega y ejecuta este comando:")
                    SelectionContainer { Text(OwnerPolicies.ADB_COMMAND, fontFamily = FontFamily.Monospace) }
                    Row {
                        TextButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("comando", OwnerPolicies.ADB_COMMAND))
                            toast(ctx, "Comando copiado")
                        }) { Text("Copiar comando") }
                        TextButton(onClick = {
                            try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } catch (_: Exception) {}
                        }) { Text("Opciones de desarrollador") }
                    }
                    Hint("5. Vuelve a agregar tus cuentas. Si sale \"already several accounts\", falta quitar alguna cuenta.")
                }
            }
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, done: Boolean, desc: String, button: String, onClick: () -> Unit) {
    InfoCard {
        Row {
            Text("$n. $title", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (done) Text("✅")
        }
        Hint(desc)
        if (!done || n == 5) OutlinedButton(onClick = onClick) { Text(button) }
    }
}
