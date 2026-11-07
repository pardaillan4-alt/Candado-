package com.candado.app.core

import android.os.SystemClock
import android.provider.Settings
import com.candado.app.CandadoApp
import com.candado.app.data.Store
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Reloj a prueba de trampas para los bloqueos estrictos:
 *  1. Si hay internet, usa la hora de un servidor (no la del teléfono).
 *  2. Dentro del mismo encendido, avanza con el reloj interno que no se puede cambiar.
 *  3. Nunca retrocede: atrasar o adelantar la hora del teléfono no acorta el bloqueo.
 */
object TrustedClock {
    @Volatile private var netTime = 0L
    @Volatile private var netElapsed = 0L
    @Volatile private var lastSync = 0L

    private fun bootCount(): Int = try {
        Settings.Global.getInt(CandadoApp.ctx.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (e: Exception) { -1 }

    fun now(): Long {
        val p = Store.prefs
        val elapsed = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val lastSeen = p.getLong("clock_last", 0L)
        val lastElapsed = p.getLong("clock_last_elapsed", 0L)
        val lastBoot = p.getInt("clock_boot", -2)
        val boot = bootCount()

        val candidate = when {
            netTime > 0 -> netTime + (elapsed - netElapsed)
            // Mismo encendido: solo se permite avanzar lo que avanzó el reloj interno (+2 min de tolerancia).
            lastSeen > 0 && boot == lastBoot && elapsed >= lastElapsed ->
                minOf(wall, lastSeen + (elapsed - lastElapsed) + 120_000L).coerceAtLeast(lastSeen + (elapsed - lastElapsed))
            else -> wall
        }
        val n = maxOf(candidate, lastSeen)
        if (n - lastSeen > 30_000L || boot != lastBoot) {
            p.edit().putLong("clock_last", n).putLong("clock_last_elapsed", elapsed).putInt("clock_boot", boot).apply()
        }
        return n
    }

    fun syncAsync() {
        if (SystemClock.elapsedRealtime() - lastSync < 10 * 60_000L && netTime > 0) return
        thread(isDaemon = true) {
            for (u in listOf("https://www.google.com", "https://www.cloudflare.com")) {
                try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    c.requestMethod = "HEAD"; c.connectTimeout = 8000; c.readTimeout = 8000
                    c.connect()
                    val d = c.date
                    c.disconnect()
                    if (d > 1_600_000_000_000L) {
                        netElapsed = SystemClock.elapsedRealtime(); netTime = d
                        lastSync = netElapsed
                        now()
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
