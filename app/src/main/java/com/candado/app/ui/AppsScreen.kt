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

/** Apps conocidas que se pueden bloquear aunque no estén instaladas. */
val POPULAR_APPS = listOf(
    "com.instagram.android" to "Instagram", "com.instagram.lite" to "Instagram Lite",
    "com.instagram.barcelona" to "Threads",
    "com.zhiliaoapp.musically" to "TikTok", "com.zhiliaoapp.musically.go" to "TikTok Lite",
    "com.ss.android.ugc.trill" to "TikTok (Asia)",
    "com.facebook.katana" to "Facebook", "com.facebook.lite" to "Facebook Lite",
    "com.snapchat.android" to "Snapchat", "com.twitter.android" to "X (Twitter)",
    "com.reddit.frontpage" to "Reddit", "com.pinterest" to "Pinterest",
    "com.tumblr" to "Tumblr", "com.discord" to "Discord", "org.telegram.messenger" to "Telegram",
    "com.google.android.youtube" to "YouTube", "tv.twitch.android.app" to "Twitch",
    "com.tinder" to "Tinder", "com.bumble.app" to "Bumble", "com.badoo.mobile" to "Badoo",
    "co.hinge.app" to "Hinge", "com.grindrapp.android" to "Grindr",
    "com.kwai.video" to "Kwai",
    "com.android.chrome" to "Chrome", "org.mozilla.firefox" to "Firefox", "com.opera.browser" to "Opera",
    "com.brave.browser" to "Brave", "com.UCMobile.intl" to "UC Browser", "org.torproject.torbrowser" to "Tor Browser"
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
    var manual by remember { mutableStateOf("") }

    // Une instaladas + populares no instaladas + bloqueadas que no estén instaladas.
    val all = apps?.let { installed ->
        val have = installed.map { it.pkg }.toSet()
        val known = POPULAR_APPS.toMap()
        val extra = (POPULAR_APPS.map { it.first } + blocked).distinct()
            .filter { it !in have && it != ctx.packageName }
            .map { AppItem(it, (known[it] ?: it) + " · no instalada", null) }
        installed + extra.sortedBy { it.label.lowercase() }
    }

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
        Hint("Las marcadas \"no instalada\" quedan bloqueadas por si algún día las instalas.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(manual, { manual = it.trim() }, Modifier.weight(1f), singleLine = true,
                label = { Text("Otra app (nombre de paquete)") }, placeholder = { Text("com.ejemplo.app") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (Regex("^[a-zA-Z][\\w]*(\\.[a-zA-Z_][\\w]*)+$").matches(manual)) {
                    Store.blockedApps = Store.blockedApps + manual; manual = ""; toast(ctx, "App agregada al bloqueo")
                } else toast(ctx, "Escribe el nombre de paquete, ej. com.instagram.android (aparece en el enlace de Play Store después de id=)")
            }) { Text("Agregar") }
        }
        val list = all
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
