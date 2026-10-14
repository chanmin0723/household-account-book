package com.chanmin.nabiledger.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chanmin.nabiledger.data.LedgerRepository

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LedgerRepository.init(context)
        Notifier.createChannels(context)
        if (intent.action == ReminderScheduler.ACTION_MIDNIGHT) {
            LiveStatus.update(context)
            ReminderScheduler.scheduleMidnight(context)
            return
        }
        Notifier.postDailyReminder(context)
        LiveStatus.update(context)
        ReminderScheduler.schedule(context)
    }
}

/** 재부팅·시간 변경·정확한 알람 권한 변경·앱 업데이트 후 알람 재예약 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LedgerRepository.init(context)
        ReminderScheduler.schedule(context)
        LiveStatus.update(context)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Notifier.createChannels(context)
            Notifier.post(
                context, Notifier.CH_UPDATE, Notifier.ID_UPDATE,
                "🐾 나비 가계부가 업데이트됐다냥",
                "v${com.chanmin.nabiledger.BuildConfig.VERSION_NAME} 으로 바뀌었어요"
            )
        }
    }
}
