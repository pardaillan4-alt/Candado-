package com.candado.app

import android.app.Application
import android.content.Context
import com.candado.app.core.TrustedClock
import com.candado.app.data.Store

class CandadoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = this
        Store.init(this)
        TrustedClock.syncAsync()
    }

    companion object {
        lateinit var ctx: Context
            private set
    }
}
