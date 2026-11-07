package com.candado.app.core

import com.candado.app.data.Lock
import com.candado.app.data.Store
import com.candado.app.data.Target

/**
 * Reglas centrales.
 *  - Endurecer siempre está permitido (agregar apps, palabras, candados, extender tiempo).
 *  - Aflojar (quitar, apagar, borrar) solo si TODOS los candados que cubren esa parte están abiertos.
 *  - Un candado estricto no se abre con nada hasta que termine su tiempo.
 */
object Guard {
    fun now(): Long = TrustedClock.now()

    fun locksFor(t: Target): List<Lock> = Store.locks().filter { t in it.targets }

    fun isStrict(l: Lock, n: Long = now()) = l.strictUntil > n
    fun isPassActive(l: Lock, n: Long = now()) = l.passUntil > n && !isStrict(l, n)

    /** ¿Se pueden aflojar los ajustes de esta parte? */
    fun isOpen(t: Target): Boolean {
        val n = now()
        return locksFor(t).all { isPassActive(it, n) }
    }

    fun isProtected(t: Target) = locksFor(t).isNotEmpty()

    /** Protección pausada solo si hay candados y todos se abrieron a propósito. */
    fun paused(t: Target): Boolean {
        val l = locksFor(t)
        val n = now()
        return l.isNotEmpty() && l.all { isPassActive(it, n) }
    }

    fun strictUntil(t: Target): Long = locksFor(t).maxOfOrNull { it.strictUntil } ?: 0L

    fun appBlocked(pkg: String): Boolean = pkg in Store.blockedApps && !paused(Target.APPS)

    fun filterActive(): Boolean = Store.filterEnabled && !paused(Target.FILTRO)
    fun dnsVpnGuardActive(): Boolean = isProtected(Target.DNS_VPN) && !paused(Target.DNS_VPN)
    fun settingsGuardActive(): Boolean = isProtected(Target.AJUSTES) && !paused(Target.AJUSTES)
    fun keywordsActive(): Boolean = Store.keywordsEnabled && Store.keywords.isNotEmpty() && !paused(Target.PALABRAS)

    /** Borra los candados de solo-tiempo que ya terminaron. */
    fun cleanupExpired() {
        val n = now()
        val expired = Store.locks().filter { it.methods.isEmpty() && it.strictUntil in 1..n }
        if (expired.isNotEmpty()) Store.saveLocks(Store.locks() - expired.toSet())
    }

    /** Espera obligatoria tras intentos fallidos (anti-fuerza bruta). */
    fun failWaitMs(l: Lock): Long {
        if (l.fails < 5) return 0
        val wait = (30_000L shl (l.fails - 5).coerceAtMost(7)).coerceAtMost(3_600_000L)
        return (l.lastFailAt + wait - now()).coerceAtLeast(0)
    }
}
