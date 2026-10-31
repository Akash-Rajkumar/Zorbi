package com.zorbi.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ZorbiEngineService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "zorbi_engine"
    }
}

