package com.coinsprout.app

import android.app.Application
import com.coinsprout.app.sync.SyncWorker
import com.coinsprout.app.util.NotificationHelper

class CoinSproutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        SyncWorker.schedulePeriodic(this)
    }
}
