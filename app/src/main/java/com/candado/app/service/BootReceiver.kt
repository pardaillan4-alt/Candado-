package com.candado.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.core.TrustedClock
import com.candado.app.data.Store

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Store.init(context)
            TrustedClock.syncAsync()
            if (Guard.filterActive()) FilterVpnService.start(context)
            OwnerPolicies.apply(context)
        } catch (_: Exception) {
            // En LOCKED_BOOT_COMPLETED el almacenamiento aún puede estar cifrado; se reintenta en BOOT_COMPLETED.
        }
    }
}
