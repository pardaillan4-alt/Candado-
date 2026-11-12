package com.candado.app.ui

import android.content.Intent
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
import com.candado.app.data.Store
import com.candado.app.data.Target

/** Pantalla que aparece cuando se intenta abrir algo bloqueado. */
class BlockedActivity : ComponentActivity() {
    private var tipo by mutableStateOf("app")
    private var detalle by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
        setContent { CandadoTheme { Blocked() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(i: Intent?) {
        tipo = i?.getStringExtra("tipo") ?: "app"
        detalle = i?.getStringExtra("detalle") ?: ""
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Composable
    private fun Blocked() {
        val target = when (tipo) {
            "app" -> Target.APPS
            "palabra" -> Target.PALABRAS
            "limite" -> Target.LIMITES
            else -> if (detalle.contains("VPN") || detalle.contains("DNS")) Target.DNS_VPN else Target.AJUSTES
        }
        val label = when (tipo) {
            "app", "limite" -> try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(detalle, 0)).toString()
            } catch (e: Exception) { detalle }
            else -> detalle
        }
        val now = Guard.now()
        val strict = Guard.strictUntil(target)
        val unlockable = Guard.locksFor(target).firstOrNull { !Guard.isStrict(it, now) && it.methods.isNotEmpty() }

        Column(
            Modifier.fillMaxSize().background(Teal).systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔒", fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                when (tipo) {
                    "app" -> "$label está bloqueada"
                    "limite" -> "Se acabó el tiempo de $label"
                    "palabra" -> "Contenido bloqueado"
                    else -> "Esta pantalla está protegida"
                },
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (tipo) {
                    "palabra" -> "Se detectó una palabra de tu lista."
                    "limite" -> com.candado.app.core.Limits.lista().firstOrNull { detalle in it.apps }?.let {
                        "Límite \"${it.nombre}\": " + (if (it.minutos <= 0) "bloqueada" else "${it.minutos} min") +
                            " de ${com.candado.app.core.Limits.hhmm(it.desde)} a ${com.candado.app.core.Limits.hhmm(it.hasta)}. Se libera a las ${com.candado.app.core.Limits.hhmm(it.hasta)}."
                    } ?: "Ya usaste el tiempo permitido en este horario."
                    "app" -> "Decidiste bloquearla. Tú puedes más que el impulso."
                    else -> "Protección: $label"
                },
                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center
            )
            if (strict > now) {
                Spacer(Modifier.height(16.dp))
                Text("Bloqueo estricto: faltan ${fmtRemaining(strict - now)}", color = Gold, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Hasta ${fmtDate(strict)}", color = Gold, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = { goHome() }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Teal)) {
                Text("Volver al inicio")
            }
            if (strict <= now && unlockable != null) {
                TextButton(onClick = {
                    startActivity(Intent(this@BlockedActivity, UnlockActivity::class.java).putExtra("lockId", unlockable.id))
                    finish()
                }) { Text("Abrir candado", color = Color.White) }
            }
        }
    }
}
