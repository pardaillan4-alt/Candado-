package com.candado.app.core

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager
import com.candado.app.data.Store
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Una actividad de la rutina (ej. Santo Rosario, 20 min, todos los días a las 22:00). */
data class Actividad(
    val id: String,
    val nombre: String,
    val minutos: Int,
    val auto: Boolean,
    /** Minuto del día en que empieza sola (ej. 22:00 = 1320). */
    val hora: Int,
    /** Días de la semana (Calendar.SUNDAY=1 … SATURDAY=7). */
    val dias: Set<Int>,
    /** Apps extra que se pueden usar durante la actividad (ej. una app del Rosario). */
    val appsExtra: Set<String>
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("nombre", nombre); put("minutos", minutos); put("auto", auto); put("hora", hora)
        put("dias", JSONArray(dias.toList())); put("apps", JSONArray(appsExtra.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject) = Actividad(
            o.getString("id"), o.optString("nombre", "Actividad"), o.optInt("minutos", 20),
            o.optBoolean("auto"), o.optInt("hora", 1320),
            o.optJSONArray("dias")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: (1..7).toSet(),
            o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        )
    }
}

/** Sesión en curso: no se puede salir hasta que pasen los minutos y se marque "Ya terminé". */
data class Sesion(val actividadId: String, val nombre: String, val inicio: Long, val minutos: Int, val apps: Set<String>, val diaAuto: String?) {
    val fin get() = inicio + minutos * 60_000L
}

object Routine {
    /** Siempre permitidas durante una actividad: urgencias. */
    private val SIEMPRE = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.phone", "com.android.server.telecom", "com.android.incallui", "com.samsung.android.incallui",
        "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts",
        "com.android.mms", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.android.deskclock", "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        "com.android.systemui", "android", "com.android.emergency", "com.google.android.apps.safetyhub"
    )

    @Volatile private var cacheAllowed: Pair<String, Set<String>>? = null

    fun actividades(): List<Actividad> {
        val arr = try { JSONArray(Store.prefs.getString("rutina", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { try { Actividad.fromJson(arr.getJSONObject(it)) } catch (e: Exception) { null } }
    }

    fun guardar(list: List<Actividad>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        Store.prefs.edit().putString("rutina", arr.toString()).apply()
        Store.rev.intValue = Store.rev.intValue + 1
    }

    fun sesion(): Sesion? {
        val o = try { JSONObject(Store.prefs.getString("sesion", null) ?: return null) } catch (e: Exception) { return null }
        return Sesion(
            o.getString("id"), o.optString("nombre"), o.getLong("inicio"), o.optInt("minutos", 20),
            o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            o.optString("diaAuto").ifEmpty { null }
        )
    }

    private fun guardarSesion(s: Sesion?) {
        val e = Store.prefs.edit()
        if (s == null) e.remove("sesion") else e.putString("sesion", JSONObject().apply {
            put("id", s.actividadId); put("nombre", s.nombre); put("inicio", s.inicio); put("minutos", s.minutos)
            put("apps", JSONArray(s.apps.toList())); s.diaAuto?.let { put("diaAuto", it) }
        }.toString())
        e.commit()
        cacheAllowed = null
        Store.rev.intValue = Store.rev.intValue + 1
    }

    fun iniciar(a: Actividad, diaAuto: String? = null): Boolean {
        if (sesion() != null) return false
        guardarSesion(Sesion(a.id, a.nombre, Guard.now(), a.minutos, a.appsExtra, diaAuto))
        return true
    }

    fun puedeTerminar(s: Sesion?): Boolean = s != null && Guard.now() >= s.fin

    /** Solo funciona cuando ya pasó el tiempo mínimo. */
    fun terminar(): Boolean {
        val s = sesion() ?: return false
        if (!puedeTerminar(s)) return false
        // Hecha hoy (aunque la hayas iniciado tú): así no vuelve a arrancar sola el mismo día.
        marcarHecha(s.actividadId, s.diaAuto ?: claveDia(Calendar.getInstance().apply { timeInMillis = Guard.now() }))
        guardarSesion(null)
        return true
    }

    private fun claveDia(c: Calendar) = "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    private fun hecha(id: String, dia: String) = Store.prefs.getString("hecha_$id", "") == dia
    private fun marcarHecha(id: String, dia: String) { Store.prefs.edit().putString("hecha_$id", dia).apply() }

    /** Arranca sola la actividad programada si ya es su hora y hoy no se ha hecho. */
    fun revisarAutomaticas() {
        if (sesion() != null) return
        val c = Calendar.getInstance().apply { timeInMillis = Guard.now() }
        val minDia = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val dia = claveDia(c)
        val dow = c.get(Calendar.DAY_OF_WEEK)
        for (a in actividades()) {
            if (!a.auto || dow !in a.dias || hecha(a.id, dia)) continue
            // Ventana: desde su hora hasta 2 h después de lo que dura (por si el teléfono estaba apagado).
            if (minDia >= a.hora && minDia < a.hora + a.minutos + 120) {
                iniciar(a, dia); return
            }
        }
    }

    fun proxima(a: Actividad): String {
        val h = a.hora / 60; val m = a.hora % 60
        val d = if (a.dias.size == 7) "todos los días" else listOf(2 to "L", 3 to "M", 4 to "Mi", 5 to "J", 6 to "V", 7 to "S", 1 to "D")
            .filter { it.first in a.dias }.joinToString(" ") { it.second }
        return "%02d:%02d · %s".format(h, m, d)
    }

    /** Paquetes que se pueden usar durante la sesión. */
    fun permitidas(ctx: Context, s: Sesion): Set<String> {
        cacheAllowed?.let { if (it.first == s.actividadId + s.inicio) return it.second }
        val set = HashSet(SIEMPRE)
        set += s.apps
        set += ctx.packageName
        try {
            ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName?.let { set += it }
        } catch (_: Exception) {}
        try { (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage?.let { set += it } } catch (_: Exception) {}
        try { Telephony.Sms.getDefaultSmsPackage(ctx)?.let { set += it } } catch (_: Exception) {}
        cacheAllowed = (s.actividadId + s.inicio) to set
        return set
    }
}
