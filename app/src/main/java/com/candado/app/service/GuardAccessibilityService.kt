package com.candado.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.core.TrustedClock
import com.candado.app.data.Store
import com.candado.app.ui.BlockedActivity
import com.candado.app.ui.VpnConsentActivity
import java.text.Normalizer

/** El "vigilante": ve qué app/pantalla se abre y cierra lo que esté bloqueado. */
class GuardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var running = false
            private set

        private val SETTINGS_PKGS = setOf(
            "com.android.settings", "com.samsung.android.settings", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.samsung.android.packageinstaller",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.vpndialogs", "com.miui.securitycenter", "com.miui.securitycore",
            "com.coloros.safecenter", "com.oplus.safecenter", "com.huawei.systemmanager",
            "com.samsung.android.lool", "com.samsung.android.sm.devicesecurity",
            "com.motorola.settings", "com.oneplus.security", "com.transsion.phonemaster",
            "com.android.vending", "com.google.android.gms", "com.samsung.android.app.galaxyfinder"
        )

        // Frases sin acentos y en minúsculas (el texto de pantalla se normaliza igual).
        private val DNS_VPN_RULES = listOf(
            "dns privado", "dns privada", "private dns", "proveedor de dns", "dns provider",
            "vpn siempre activa", "always-on vpn", "always on vpn", "bloquear conexiones sin vpn",
            "block connections without vpn", "agregar vpn", "anadir vpn", "add vpn", "perfil de vpn", "vpn profile"
        )
        private val VPN_DIALOG_RULES = listOf(
            "solicitud de conexion", "connection request", "configurar una conexion vpn",
            "set up a vpn connection", "desconectar", "disconnect"
        )
        private val CRITICAL_RULES = listOf(
            "fecha y hora", "date & time", "date and time", "zona horaria", "time zone",
            "restablecer datos de fabrica", "restablecer valores de fabrica", "factory data reset", "factory reset",
            "borrar todos los datos", "erase all data", "opciones de desarrollador", "opciones para desarrolladores",
            "developer options", "depuracion usb", "usb debugging", "depuracion inalambrica", "wireless debugging",
            "agregar usuario", "add user", "espacio privado", "private space", "second space", "segundo espacio",
            "carpeta segura", "secure folder", "app clonada", "dual app", "aplicaciones duales"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastScan = 0L
    private var lastKick = 0L
    private var lastPkg = ""

    private val tick = object : Runnable {
        override fun run() {
            try {
                Guard.cleanupExpired()
                TrustedClock.syncAsync()
                ensureVpn()
                OwnerPolicies.apply(this@GuardAccessibilityService)
            } catch (_: Exception) {}
            handler.postDelayed(this, 20_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        running = true
        handler.post(tick)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private var lastVpnAsk = 0L
    private fun ensureVpn() {
        if (!Guard.filterActive() || FilterVpnService.running) return
        if (VpnService.prepare(this) == null) {
            FilterVpnService.start(this)
        } else if (SystemClock.elapsedRealtime() - lastVpnAsk > 60_000) {
            lastVpnAsk = SystemClock.elapsedRealtime()
            startActivity(Intent(this, VpnConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && Guard.appBlocked(pkg)) {
            kick("app", pkg, goBack = false)
            return
        }

        val isSettings = pkg in SETTINGS_PKGS
        val needSettings = isSettings && (Guard.dnsVpnGuardActive() || Guard.settingsGuardActive())
        val needWords = !isSettings && Guard.keywordsActive() && !isKeyboard(pkg)
        if (!needSettings && !needWords) return

        val t = SystemClock.uptimeMillis()
        val minGap = if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) 0 else if (needSettings) 250 else 700
        if (pkg == lastPkg && t - lastScan < minGap) return
        lastScan = t; lastPkg = pkg

        val root = rootInActiveWindow ?: return
        val text = norm(collect(root))

        if (needSettings) {
            val appName = "candado"
            val hit = when {
                Guard.dnsVpnGuardActive() && pkg == "com.android.vpndialogs" && VPN_DIALOG_RULES.any { it in text } -> "VPN"
                Guard.dnsVpnGuardActive() && DNS_VPN_RULES.any { it in text } -> "DNS / VPN"
                Guard.settingsGuardActive() && (appName in text || CRITICAL_RULES.any { it in text }) -> "Ajustes críticos"
                Guard.dnsVpnGuardActive() && pkg == "com.android.vpndialogs" && appName in text -> "VPN"
                else -> null
            }
            if (hit != null) kick("ajustes", hit, goBack = true)
        } else if (needWords) {
            val kw = Store.keywords.map { norm(it) }.firstOrNull { it.isNotBlank() && containsWord(text, it) }
            if (kw != null) kick("palabra", kw, goBack = true)
        }
    }

    private fun isKeyboard(pkg: String) = pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains("honeyboard")

    private fun containsWord(text: String, w: String): Boolean {
        var idx = text.indexOf(w)
        while (idx >= 0) {
            val before = if (idx == 0) ' ' else text[idx - 1]
            if (!before.isLetterOrDigit()) return true
            idx = text.indexOf(w, idx + 1)
        }
        return false
    }

    private fun kick(tipo: String, detalle: String, goBack: Boolean) {
        val t = SystemClock.uptimeMillis()
        if (t - lastKick < 600) return
        lastKick = t
        if (goBack) performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tipo", tipo).putExtra("detalle", detalle)
        )
    }

    private fun collect(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 30 || sb.length > 8000) return
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString()
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
