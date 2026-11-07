package com.candado.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Teal = Color(0xFF0F3D3E)
val Gold = Color(0xFFF2C14E)
val Danger = Color(0xFFB3261E)
val Ok = Color(0xFF2E7D32)

@Composable
fun CandadoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Gold, onPrimary = Teal, secondary = Color(0xFF8FC9C4),
        background = Color(0xFF101414), surface = Color(0xFF172020)
    ) else lightColorScheme(
        primary = Teal, onPrimary = Color.White, secondary = Color(0xFF8A6A12),
        background = Color(0xFFF6F4EF), surface = Color.White
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

fun fmtDate(ms: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "MX")).format(Date(ms))

fun fmtRemaining(ms: Long): String {
    if (ms <= 0) return "0 min"
    var m = ms / 60_000
    val years = m / (365L * 24 * 60); m -= years * 365L * 24 * 60
    val days = m / (24 * 60); m -= days * 24 * 60
    val hours = m / 60; m -= hours * 60
    val parts = mutableListOf<String>()
    if (years > 0) parts += "$years ${if (years == 1L) "año" else "años"}"
    if (days > 0) parts += "$days ${if (days == 1L) "día" else "días"}"
    if (hours > 0 && years == 0L) parts += "$hours h"
    if (m > 0 && years == 0L && days == 0L) parts += "$m min"
    if (parts.isEmpty()) parts += "menos de 1 min"
    return parts.joinToString(" ")
}

@Composable
fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun Hint(t: String, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) {
    Text(t, fontSize = 13.sp, color = color, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.padding(14.dp), content = content) }
}
