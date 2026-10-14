package com.chanmin.nabiledger

import android.app.Application
import com.chanmin.nabiledger.cloud.Cloud
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.notify.LiveStatus
import com.chanmin.nabiledger.notify.Notifier
import com.chanmin.nabiledger.notify.ReminderScheduler

class NabiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LedgerRepository.init(this)
        Cloud.init(this)
        Notifier.createChannels(this)
        ReminderScheduler.schedule(this)
        LiveStatus.start(this)
    }
}
