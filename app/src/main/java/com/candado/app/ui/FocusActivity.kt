package com.candado.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.candado.app.core.Guard
import com.candado.app.core.Routine
import com.candado.app.data.Store
import kotlinx.coroutines.delay

/** Pantalla de la actividad en curso. */
class FocusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
        setContent { CandadoTheme { Focus() } }
    }

    private fun open(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(this, "No se encontró la app.") }
    }

    @Composable
    private fun Focus() {
        var now by remember { mutableLongStateOf(Guard.now()) }
        LaunchedEffect(Unit) { while (true) { delay(1000); now = Guard.now() } }
        @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
        val s = Routine.sesion()

        Column(
            Modifier.fillMaxSize().background(Teal).systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (s == null) {
                Text("✅", fontSize = 64.sp)
                Text("Actividad terminada", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { finish() }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Teal)) { Text("Cerrar") }
                return@Column
            }
            val left = s.fin - now
            val total = s.minutos * 60_000L
            Text("🕯️", fontSize = 64.sp)
            Spacer(Modifier.height(8.dp))
            Text("Es momento de:", color = Color.White.copy(alpha = 0.8f))
            Text(s.nombre, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { (1f - left.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = Gold, trackColor = Color.White.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(10.dp))
            if (left > 0) {
                val m = left / 60_000; val sec = (left / 1000) % 60
                Text("Faltan %d:%02d".format(m, sec), color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("El celular se libera cuando pase el tiempo y marques que terminaste.", color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            } else {
                Text("Ya pasó el tiempo mínimo", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                enabled = left <= 0,
                onClick = { if (Routine.terminar()) toast(this@FocusActivity, "¡Bien hecho! Celular liberado.") },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Teal),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (left <= 0) "Ya terminé" else "Ya terminé (disponible al acabar el tiempo)") }
            Spacer(Modifier.height(24.dp))
            Text("Urgencias (siempre disponibles):", color = Color.White.copy(alpha = 0.8f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open(Intent(Intent.ACTION_DIAL)) }) { Text("📞 Llamar", color = Color.White) }
                OutlinedButton(onClick = {
                    packageManager.getLaunchIntentForPackage("com.whatsapp")?.let { open(it) }
                        ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")?.let { open(it) }
                        ?: toast(this@FocusActivity, "WhatsApp no está instalado.")
                }) { Text("💬 WhatsApp", color = Color.White) }
            }
            OutlinedButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))) }) { Text("🚨 911", color = Color.White) }
            if (s.apps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Apps para esta actividad:", color = Color.White.copy(alpha = 0.8f))
                s.apps.forEach { pkg ->
                    val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                    TextButton(onClick = { packageManager.getLaunchIntentForPackage(pkg)?.let { open(it) } }) { Text(label, color = Gold) }
                }
            }
        }
    }
}
