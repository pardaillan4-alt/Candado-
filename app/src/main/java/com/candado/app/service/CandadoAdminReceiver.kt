package com.candado.app.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.candado.app.core.OwnerPolicies
import com.candado.app.data.Store

class CandadoAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Store.init(context)
        OwnerPolicies.apply(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Desactivar Candado quitará tus protecciones. Si tienes candados activos, ábrelos primero desde la app."
}
