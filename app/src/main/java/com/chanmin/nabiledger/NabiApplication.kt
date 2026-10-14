package com.chanmin.nabiledger

import android.app.Application
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.notify.Notifier
import com.chanmin.nabiledger.notify.ReminderScheduler

class NabiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LedgerRepository.init(this)
        Notifier.createChannels(this)
        ReminderScheduler.schedule(this)
    }
}
