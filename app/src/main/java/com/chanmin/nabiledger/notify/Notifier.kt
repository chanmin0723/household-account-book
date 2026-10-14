package com.chanmin.nabiledger.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chanmin.nabiledger.MainActivity
import com.chanmin.nabiledger.R
import com.chanmin.nabiledger.capture.Capture
import com.chanmin.nabiledger.capture.CaptureActionReceiver
import com.chanmin.nabiledger.data.PendingCapture
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.logic.Stats
import java.time.LocalDate

object Notifier {
    const val CH_REMINDER = "daily_reminder"
    const val CH_OVERSPEND = "overspend"
    const val CH_CAPTURE = "capture"
    const val ID_REMINDER = 2001
    const val ID_OVERSPEND = 2002

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "매일 기록 알림", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "정해진 시간에 오늘 가계부를 알려줘요" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_OVERSPEND, "과소비 경고", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "평소보다 많이 쓰면 나비가 알려줘요" }
        )
        nm.createNotificationChannel(
            NotificationChannel(LiveStatus.CHANNEL, "실시간 지출", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "오늘/이번 달 지출을 알림창에 계속 보여줘요"
                    setShowBadge(false)
                }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CAPTURE, "결제 알림 감지", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "결제 알림을 읽고 기록할지 물어봐요" }
        )
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_paw)
            .setColor(0xFFD9813A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun postDailyReminder(ctx: Context) {
        val today = LocalDate.now().toEpochDay()
        val (title, text) = Stats.reminder(
            LedgerRepository.entries.value, today, LedgerRepository.settings.value.threshold
        )
        post(ctx, CH_REMINDER, ID_REMINDER, title, text)
    }

    /** 오늘 처음으로 경고선을 넘었을 때 하루 한 번 알림 */
    fun maybeAlertOverspend(ctx: Context) {
        val s = LedgerRepository.settings.value
        if (!s.overspendAlert) return
        val today = LocalDate.now().toEpochDay()
        if (LedgerRepository.overspendNotifiedDay() == today) return
        val entries = LedgerRepository.entries.value
        if (!Stats.isOverspent(entries, today, s.threshold)) return
        val v = Stats.verdict(entries, today, s.threshold)
        post(ctx, CH_OVERSPEND, ID_OVERSPEND, "평소보다 많이 썼다냥! 😿", v.message)
        LedgerRepository.markOverspendNotified(today)
    }

    private fun actionIntent(ctx: Context, action: String, id: Long, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, CaptureActionReceiver::class.java)
                .setAction(action)
                .putExtra(CaptureActionReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission")
    fun postPendingCapture(ctx: Context, p: PendingCapture) {
        if (!canPost(ctx)) return
        val nid = Capture.notificationId(p)
        val open = PendingIntent.getActivity(
            ctx, nid,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val what = (if (p.isIncome) "+" else "") + Fmt.won(p.amount) +
            (if (p.merchant.isNotEmpty()) " · ${p.merchant}" else "")
        val n = NotificationCompat.Builder(ctx, CH_CAPTURE)
            .setSmallIcon(R.drawable.ic_paw)
            .setColor(0xFFD9813A.toInt())
            .setContentTitle("🐾 이거 기록할까냥?")
            .setContentText("$what (${p.category})")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "기록", actionIntent(ctx, CaptureActionReceiver.ACTION_ACCEPT, p.id, nid * 2))
            .addAction(0, "무시", actionIntent(ctx, CaptureActionReceiver.ACTION_DISMISS, p.id, nid * 2 + 1))
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, n)
    }

    fun postCaptureSaved(ctx: Context, entryId: Long, label: String) {
        post(ctx, CH_CAPTURE, Capture.notificationId(entryId), "🐾 자동으로 기록했다냥", label)
    }

    fun cancelPending(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(Capture.notificationId(id))
    }
}
