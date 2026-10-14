package com.chanmin.nabiledger.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chanmin.nabiledger.MainActivity
import com.chanmin.nabiledger.R
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.logic.Mood
import com.chanmin.nabiledger.logic.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 알림창에 항상 떠 있는 '오늘 / 이번 달 지출' 알림.
 * 기록이 추가·수정·삭제될 때마다(알림 자동 기록 포함) 바로 갱신되고, 자정에 새 날로 바뀐다.
 */
object LiveStatus {
    const val CHANNEL = "live_status"
    private const val ID = 3001

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            combine(LedgerRepository.entries, LedgerRepository.settings) { _, _ -> Unit }
                .collect { update(app) }
        }
    }

    private fun emoji(mood: Mood) = when (mood) {
        Mood.CURIOUS -> "🐱"
        Mood.HAPPY -> "😸"
        Mood.CALM -> "🐈"
        Mood.WORRIED -> "😿"
        Mood.SHOCKED -> "🙀"
    }

    @SuppressLint("MissingPermission")
    fun update(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        val s = LedgerRepository.settings.value
        if (!s.liveStatus || !Notifier.canPost(ctx)) {
            nm.cancel(ID)
            return
        }
        val entries = LedgerRepository.entries.value
        val todayDate = LocalDate.now()
        val today = todayDate.toEpochDay()
        val spent = Stats.expenseOn(entries, today)
        val month = Stats.expenseBetween(entries, todayDate.withDayOfMonth(1).toEpochDay(), today)
        val usual = Stats.usualDaily(entries, today)
        val verdict = Stats.verdict(entries, today, s.threshold)

        val ratio = if (usual != null && usual > 0) " (평소의 ${spent * 100 / usual}%)" else ""
        val title = "${emoji(verdict.mood)} 오늘 ${Fmt.won(spent)}$ratio · 이번 달 ${Fmt.won(month)}"

        val open = PendingIntent.getActivity(
            ctx, ID,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_paw)
            .setColor(0xFFD9813A.toInt())
            .setContentTitle(title)
            .setContentText(verdict.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(verdict.message))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        nm.notify(ID, n)
    }
}
