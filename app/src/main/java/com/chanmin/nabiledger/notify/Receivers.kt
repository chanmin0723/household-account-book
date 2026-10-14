package com.chanmin.nabiledger.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chanmin.nabiledger.data.LedgerRepository

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LedgerRepository.init(context)
        Notifier.createChannels(context)
        Notifier.postDailyReminder(context)
        ReminderScheduler.schedule(context)
    }
}

/** 재부팅·시간 변경·정확한 알람 권한 변경 시 알람 재예약 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LedgerRepository.init(context)
        ReminderScheduler.schedule(context)
    }
}
