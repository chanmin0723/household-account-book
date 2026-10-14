package com.chanmin.nabiledger.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chanmin.nabiledger.data.LedgerRepository
import java.time.ZonedDateTime

/** 매일 정해진 시각에 ReminderReceiver를 깨운다. 울릴 때마다 다음 날 것을 다시 예약. */
object ReminderScheduler {
    private const val REQUEST_CODE = 1001
    const val ACTION = "com.chanmin.nabiledger.DAILY_REMINDER"

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            REQUEST_CODE,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun schedule(ctx: Context) {
        LedgerRepository.init(ctx)
        val s = LedgerRepository.settings.value
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx)
        am.cancel(pi)
        if (!s.reminderEnabled) return

        val trigger = nextTrigger(s.reminderHour, s.reminderMinute)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var t = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }
}
