package com.candado.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.candado.app.core.Guard
import com.candado.app.data.Store
import com.candado.app.data.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppItem(val pkg: String, val label: String, val icon: ImageBitmap?)

private val ESSENTIAL = setOf(
    "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
    "com.android.phone", "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts"
)

@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val rev = Store.rev.intValue
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var query by remember { mutableStateOf("") }
    var onlyBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { it.activityInfo.packageName to it }
                .distinctBy { it.first }
                .filter { it.first != ctx.packageName }
                .map { (pkg, ri) ->
                    val icon = try {
                        val d = ri.loadIcon(pm)
                        val b = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
                        d.setBounds(0, 0, 72, 72); d.draw(Canvas(b)); b.asImageBitmap()
                    } catch (e: Exception) { null }
                    AppItem(pkg, ri.loadLabel(pm).toString(), icon)
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val blocked = Store.blockedApps
    val open = Guard.isOpen(Target.APPS)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        if (!open) Hint("🔒 Hay candado en Apps: puedes agregar apps, pero quitar requiere abrirlo.", Danger)
        else Hint("Marca las apps que quieres bloquear. Luego ponles candado en la pestaña Candados.")
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Buscar app") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyBlocked, onCheckedChange = { onlyBlocked = it })
            Text("Ver solo bloqueadas (${blocked.size})")
        }
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val shown = list.filter {
                (query.isBlank() || it.label.contains(query, true)) && (!onlyBlocked || it.pkg in blocked)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.pkg }) { app ->
                    val isBlocked = app.pkg in blocked
                    fun toggle() {
                        if (isBlocked) {
                            if (!Guard.isOpen(Target.APPS)) { toast(ctx, "Candado activo: abre el candado de Apps para quitarla."); return }
                            Store.blockedApps = Store.blockedApps - app.pkg
                        } else {
                            if (app.pkg in ESSENTIAL) toast(ctx, "Ojo: bloquear el teléfono/contactos te impediría llamar a emergencias.")
                            Store.blockedApps = Store.blockedApps + app.pkg
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (app.icon != null) Image(app.icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, Modifier.weight(1f))
                        Checkbox(checked = isBlocked, onCheckedChange = { toggle() })
                    }
                }
            }
        }
    }
}
