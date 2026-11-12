package com.candado.app.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.candado.app.data.Store
import com.candado.app.data.Target
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Límite por horario: en la ventana [desde, hasta) de los días elegidos, las apps del grupo
 * pueden usarse en total `minutos` (0 = bloqueadas todo ese horario).
 * Ej.: YouTube, desde 21:00 hasta 06:00, 30 min.
 */
data class Limite(
    val id: String,
    val nombre: String,
    val apps: Set<String>,
    val desde: Int,
    val hasta: Int,
    val minutos: Int,
    val dias: Set<Int>,
    /** Páginas web que cuentan para el mismo límite (ej. youtube.com). */
    val sitios: Set<String> = emptySet()
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("nombre", nombre); put("apps", JSONArray(apps.toList()))
        put("desde", desde); put("hasta", hasta); put("minutos", minutos); put("dias", JSONArray(dias.toList()))
        put("sitios", JSONArray(sitios.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject) = Limite(
            o.getString("id"), o.optString("nombre", "Límite"),
            o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            o.optInt("desde", 21 * 60), o.optInt("hasta", 0), o.optInt("minutos", 30),
            o.optJSONArray("dias")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: (1..7).toSet(),
            o.optJSONArray("sitios")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                // Límites creados antes: se agregan solos los sitios de las apps conocidas.
                ?: (o.optJSONArray("apps")?.let { a -> (0 until a.length()).flatMap { Limits.SITIOS_DE_APP[a.getString(it)] ?: emptyList() }.toSet() } ?: emptySet())
        )
    }
}

object Limits {
    /** Sitios web de apps conocidas: se agregan solos al elegir la app. */
    val SITIOS_DE_APP = mapOf(
        "com.google.android.youtube" to listOf("youtube.com", "youtu.be", "youtube-nocookie.com"),
        "com.instagram.android" to listOf("instagram.com"), "com.instagram.lite" to listOf("instagram.com"),
        "com.instagram.barcelona" to listOf("threads.net", "threads.com"),
        "com.zhiliaoapp.musically" to listOf("tiktok.com"), "com.zhiliaoapp.musically.go" to listOf("tiktok.com"),
        "com.facebook.katana" to listOf("facebook.com", "fb.com", "fb.watch"), "com.facebook.lite" to listOf("facebook.com", "fb.com"),
        "com.twitter.android" to listOf("x.com", "twitter.com"), "com.reddit.frontpage" to listOf("reddit.com"),
        "com.pinterest" to listOf("pinterest.com"), "tv.twitch.android.app" to listOf("twitch.tv"),
        "com.netflix.mediaclient" to listOf("netflix.com"), "com.snapchat.android" to listOf("snapchat.com"),
        "com.kwai.video" to listOf("kwai.com"), "com.discord" to listOf("discord.com")
    )

    val NAVEGADORES = setOf(
        "com.android.chrome", "com.chrome.beta", "org.mozilla.firefox", "org.mozilla.firefox_beta", "com.opera.browser",
        "com.opera.mini.native", "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
        "com.mi.globalbrowser", "com.android.browser", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser",
        "com.vivaldi.browser", "com.UCMobile.intl", "org.torproject.torbrowser", "com.yandex.browser", "com.huawei.browser",
        "com.heytap.browser", "com.google.android.googlequicksearchbox"
    )

    private fun hostCoincide(host: String, sitio: String) = host == sitio || host.endsWith(".$sitio")

    /** Límites en horario cuyo sitio coincide con la dirección abierta en el navegador. */
    fun limitesDeSitio(host: String, now: Long = Guard.now()): List<Pair<Limite, Long>> =
        lista().mapNotNull { l -> if (l.sitios.any { hostCoincide(host, it) }) inicioVentana(l, now)?.let { l to it } else null }

    private fun claveWeb(l: Limite, inicio: Long) = "web_${l.id}_$inicio"

    /** Suma tiempo de uso en el navegador a los límites de ese sitio. */
    fun sumarWeb(host: String, ms: Long) {
        if (ms <= 0) return
        val e = Store.prefs.edit()
        limitesDeSitio(host).forEach { (l, ini) ->
            val k = claveWeb(l, ini); e.putLong(k, Store.prefs.getLong(k, 0) + ms)
        }
        e.apply(); cache.clear()
    }

    /** ¿El sitio ya está agotado/bloqueado ahora? */
    fun sitioExcedido(ctx: Context, host: String): Limite? {
        if (Guard.paused(Target.LIMITES)) return null
        for ((l, ini) in limitesDeSitio(host)) {
            if (l.minutos <= 0) return l
            if (usado(ctx, l, ini) >= l.minutos * 60_000L) return l
        }
        return null
    }

    @Volatile private var dnsCache: Pair<Long, Set<String>> = 0L to emptySet()

    /** Sitios a bloquear por DNS (límites agotados o en 0), recalculado cada 15 s. */
    fun sitiosBloqueadosDns(ctx: Context): Set<String> {
        val (t, v) = dnsCache
        if (SystemClock.elapsedRealtime() - t < 15_000) return v
        val set = HashSet<String>()
        if (!Guard.paused(Target.LIMITES)) {
            val now = Guard.now()
            for (l in lista()) {
                if (l.sitios.isEmpty()) continue
                val ini = inicioVentana(l, now) ?: continue
                if (l.minutos <= 0 || usado(ctx, l, ini) >= l.minutos * 60_000L) set += l.sitios
            }
        }
        dnsCache = SystemClock.elapsedRealtime() to set
        return set
    }

    @Volatile private var listaCache: Pair<String, List<Limite>> = "" to emptyList()

    fun lista(): List<Limite> {
        val raw = Store.prefs.getString("limites", "[]") ?: "[]"
        listaCache.let { if (it.first == raw) return it.second }
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val l = (0 until arr.length()).mapNotNull { try { Limite.fromJson(arr.getJSONObject(it)) } catch (e: Exception) { null } }
        listaCache = raw to l
        return l
    }

    fun guardar(l: List<Limite>) {
        val arr = JSONArray(); l.forEach { arr.put(it.toJson()) }
        Store.prefs.edit().putString("limites", arr.toString()).apply()
        cache.clear(); dnsCache = 0L to emptySet()
        Store.rev.intValue = Store.rev.intValue + 1
    }

    fun hhmm(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)

    /** Inicio (hora confiable) de la ventana que está corriendo ahora, o null si no está en horario. */
    fun inicioVentana(l: Limite, now: Long = Guard.now()): Long? {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val cruza = l.hasta <= l.desde // ej. 21:00 → 06:00, o "hasta" = "desde" (24 h)
        val empiezaHoy = nowMin >= l.desde && (cruza || nowMin < l.hasta)
        val empezoAyer = cruza && nowMin < l.hasta
        val inicio = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, l.desde / 60); set(Calendar.MINUTE, l.desde % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when {
            empiezaHoy -> {}
            empezoAyer -> inicio.add(Calendar.DAY_OF_YEAR, -1)
            else -> return null
        }
        return if (inicio.get(Calendar.DAY_OF_WEEK) in l.dias) inicio.timeInMillis else null
    }

    /** Cuándo termina la ventana que empezó en `inicio`. */
    fun finVentana(l: Limite, inicio: Long): Long {
        var dur = (l.hasta - l.desde) * 60_000L
        if (dur <= 0) dur += 24 * 60 * 60_000L
        return inicio + dur
    }

    @Suppress("DEPRECATION")
    fun permisoUso(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    private val cache = HashMap<String, Pair<Long, Long>>() // id → (cuándo se midió, ms usados)

    /** Milisegundos que se usaron las apps del límite desde `inicio` (hora confiable). */
    fun usado(ctx: Context, l: Limite, inicio: Long): Long {
        val key = l.id + inicio
        cache[key]?.let { (t, v) -> if (SystemClock.elapsedRealtime() - t < 8_000) return v }
        val offset = System.currentTimeMillis() - Guard.now() // por si el reloj del teléfono no es exacto
        val desde = inicio + offset
        val hasta = System.currentTimeMillis()
        var total = 0L
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = usm.queryEvents(desde - 3 * 60 * 60_000L, hasta) // margen para saber qué estaba abierto al inicio
            val abierto = HashMap<String, Long>()
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val p = e.packageName ?: continue
                if (p !in l.apps) continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> abierto[p] = e.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        abierto.remove(p)?.let { s -> total += (minOf(e.timeStamp, hasta) - maxOf(s, desde)).coerceAtLeast(0) }
                    }
                }
            }
            abierto.values.forEach { s -> total += (hasta - maxOf(s, desde)).coerceAtLeast(0) }
        } catch (_: Exception) {}
        total += Store.prefs.getLong(claveWeb(l, inicio), 0)
        cache[key] = SystemClock.elapsedRealtime() to total
        return total
    }

    /** Límite que bloquea esta app ahora mismo, o null si se puede usar. */
    fun excedido(ctx: Context, pkg: String): Limite? {
        if (Guard.paused(Target.LIMITES)) return null
        val now = Guard.now()
        for (l in lista()) {
            if (pkg !in l.apps) continue
            val ini = inicioVentana(l, now) ?: continue
            if (l.minutos <= 0) return l
            // Sin permiso de uso no se puede medir: se bloquea todo el horario (más seguro).
            if (!permisoUso(ctx)) return l
            if (usado(ctx, l, ini) >= l.minutos * 60_000L) return l
        }
        return null
    }

    /** Texto con lo que queda para mostrar en la lista. */
    fun estado(ctx: Context, l: Limite): String {
        val ini = inicioVentana(l) ?: return "Fuera de horario: libre"
        if (l.minutos <= 0) return "En horario: bloqueadas hasta las ${hhmm(l.hasta)}"
        if (!permisoUso(ctx)) return "En horario: bloqueadas (falta el permiso de datos de uso)"
        val queda = l.minutos * 60_000L - usado(ctx, l, ini)
        return if (queda > 0) "En horario: quedan ${(queda + 59_999) / 60_000} min" else "Tiempo agotado hasta las ${hhmm(l.hasta)}"
    }
}
