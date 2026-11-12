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
    val dias: Set<Int>
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("nombre", nombre); put("apps", JSONArray(apps.toList()))
        put("desde", desde); put("hasta", hasta); put("minutos", minutos); put("dias", JSONArray(dias.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject) = Limite(
            o.getString("id"), o.optString("nombre", "Límite"),
            o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            o.optInt("desde", 21 * 60), o.optInt("hasta", 0), o.optInt("minutos", 30),
            o.optJSONArray("dias")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: (1..7).toSet()
        )
    }
}

object Limits {
    fun lista(): List<Limite> {
        val arr = try { JSONArray(Store.prefs.getString("limites", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { try { Limite.fromJson(arr.getJSONObject(it)) } catch (e: Exception) { null } }
    }

    fun guardar(l: List<Limite>) {
        val arr = JSONArray(); l.forEach { arr.put(it.toJson()) }
        Store.prefs.edit().putString("limites", arr.toString()).apply()
        cache.clear()
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
