package com.candado.app.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.candado.app.data.Store
import com.candado.app.data.Target
import com.candado.app.service.CandadoAdminReceiver
import kotlin.concurrent.thread

/**
 * "Modo blindado": cuando Candado es propietario del dispositivo (Device Owner) el propio
 * Android impide desinstalarlo, entrar en modo seguro, restablecer de fábrica, cambiar la
 * hora, el DNS privado o la VPN. Se activa una sola vez con la app LADB (ver pestaña Permisos).
 */
object OwnerPolicies {
    fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    fun admin(ctx: Context) = ComponentName(ctx, CandadoAdminReceiver::class.java)
    fun isOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)
    fun isAdmin(ctx: Context) = dpm(ctx).isAdminActive(admin(ctx))

    const val ADB_COMMAND = "dpm set-device-owner com.candado.app/.service.CandadoAdminReceiver"

    private fun restrict(ctx: Context, key: String, on: Boolean) {
        try {
            if (on) dpm(ctx).addUserRestriction(admin(ctx), key) else dpm(ctx).clearUserRestriction(admin(ctx), key)
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    fun apply(ctx: Context) {
        if (!isOwner(ctx)) return
        val d = dpm(ctx); val a = admin(ctx)
        val ajustes = Guard.settingsGuardActive()
        val dns = Guard.dnsVpnGuardActive()

        restrict(ctx, UserManager.DISALLOW_SAFE_BOOT, ajustes)
        restrict(ctx, UserManager.DISALLOW_FACTORY_RESET, ajustes)
        restrict(ctx, UserManager.DISALLOW_DEBUGGING_FEATURES, ajustes)
        restrict(ctx, UserManager.DISALLOW_ADD_USER, ajustes)
        restrict(ctx, UserManager.DISALLOW_CONFIG_DATE_TIME, ajustes)
        try { d.setUninstallBlocked(a, ctx.packageName, ajustes) } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= 30) d.setAutoTimeEnabled(a, true)
            if (Build.VERSION.SDK_INT < 30) d.setAutoTimeRequired(a, ajustes)
        } catch (_: Exception) {}

        restrict(ctx, UserManager.DISALLOW_CONFIG_VPN, dns)
        if (Build.VERSION.SDK_INT >= 29) restrict(ctx, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, dns)
        try {
            if (Store.filterEnabled && dns) d.setAlwaysOnVpnPackage(a, ctx.packageName, false)
            else if (!Store.filterEnabled) d.setAlwaysOnVpnPackage(a, null, false)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= 29 && Store.filterEnabled && dns) {
            thread(isDaemon = true) {
                try { d.setGlobalPrivateDnsModeSpecifiedHost(a, "family.cloudflare-dns.com") } catch (_: Exception) {}
            }
        }
    }

    /** Solo cuando "Ajustes críticos" está abierto. Devuelve el teléfono a la normalidad. */
    @Suppress("DEPRECATION")
    fun removeOwner(ctx: Context) {
        if (!isOwner(ctx)) return
        val d = dpm(ctx); val a = admin(ctx)
        listOf(
            UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_FACTORY_RESET, UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_CONFIG_DATE_TIME, UserManager.DISALLOW_CONFIG_VPN
        ).forEach { restrict(ctx, it, false) }
        if (Build.VERSION.SDK_INT >= 29) restrict(ctx, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false)
        try { d.setUninstallBlocked(a, ctx.packageName, false) } catch (_: Exception) {}
        try { d.setAlwaysOnVpnPackage(a, null, false) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 29) try { d.setGlobalPrivateDnsModeOpportunistic(a) } catch (_: Exception) {}
        try { d.clearDeviceOwnerApp(ctx.packageName) } catch (_: Exception) {}
    }
}
