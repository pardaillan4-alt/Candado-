package com.candado.app.ui

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.candado.app.data.Store
import com.candado.app.service.FilterVpnService

/** Si alguien quitó el permiso del filtro, Candado lo vuelve a pedir. */
class VpnConsentActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) FilterVpnService.start(this)
        else toast(this, "El filtro de Candado está activo y necesita este permiso.")
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val i = VpnService.prepare(this)
        if (i == null) { FilterVpnService.start(this); finish() } else launcher.launch(i)
    }
}
