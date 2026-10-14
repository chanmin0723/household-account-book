package com.chanmin.nabiledger.capture

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.chanmin.nabiledger.cloud.Cloud
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.data.PendingCapture
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.notify.Notifier
import java.time.Instant
import java.time.ZoneId

object Capture {
    /** 사용자가 '알림 접근'을 허용했는지 */
    fun listenerEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun handle(ctx: Context, title: String, text: String, postedAt: Long) {
        LedgerRepository.init(ctx)
        val s = LedgerRepository.settings.value
        if (!s.captureEnabled) return
        val raw = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
        if (!raw.contains("원")) return // 금액이 없는 알림은 아예 보지 않음

        val parsed = PaymentParser.parse(raw)
        if (parsed == null) {
            // 결제 관련처럼 보이는데 못 읽은 것만 기록 (파서 개선용)
            if (listOf("승인", "결제", "출금", "입금").any { it in raw }) {
                LedgerRepository.logCapture(raw, "건너뜀")
            }
            return
        }
        if (LedgerRepository.isDuplicateCapture(parsed.amount, postedAt)) {
            LedgerRepository.logCapture(raw, "중복이라 건너뜀")
            return
        }
        val day = Instant.ofEpochMilli(postedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val label = (if (parsed.isIncome) "+" else "") + Fmt.won(parsed.amount) +
            (if (parsed.merchant.isNotEmpty()) " · ${parsed.merchant}" else "") + " · ${parsed.category}"

        if (s.captureAutoSave) {
            val e = LedgerRepository.add(day, parsed.amount, parsed.category, parsed.merchant, parsed.isIncome)
            Cloud.push(e)
            LedgerRepository.logCapture(raw, "자동 기록: $label")
            Notifier.postCaptureSaved(ctx, e.id, label)
            if (!e.isIncome) Notifier.maybeAlertOverspend(ctx)
        } else {
            val p = LedgerRepository.addPending(
                day, parsed.amount, parsed.merchant, parsed.category, parsed.isIncome, postedAt
            )
            LedgerRepository.logCapture(raw, "확인 대기: $label")
            Notifier.postPendingCapture(ctx, p)
        }
    }

    /** 확인 대기 → 기록 (앱 화면·알림 버튼 공통) */
    fun accept(ctx: Context, id: Long, category: String? = null) {
        val e = LedgerRepository.acceptPending(id, category) ?: return
        Cloud.push(e)
        Notifier.cancelPending(ctx, id)
        if (!e.isIncome) Notifier.maybeAlertOverspend(ctx)
    }

    fun dismiss(ctx: Context, id: Long) {
        LedgerRepository.dismissPending(id)
        Notifier.cancelPending(ctx, id)
    }

    fun notificationId(p: PendingCapture): Int = notificationId(p.id)
    fun notificationId(id: Long): Int = 10_000 + (id % 1_000_000).toInt()

    /** 설정 화면 '예시로 테스트' */
    fun runSample(ctx: Context) {
        handle(ctx, "카카오페이", "나비분식에서 6,500원 결제했어요", System.currentTimeMillis())
    }

    fun requestRebind(ctx: Context) {
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(ctx, PaymentListener::class.java))
        }
    }
}

/** 다른 앱의 결제 알림을 읽는 서비스. 설정 > 알림 접근에서 허용해야 동작 */
class PaymentListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        runCatching { Capture.handle(applicationContext, title, text, sbn.postTime) }
    }
}

/** 알림의 [기록] / [무시] 버튼 */
class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LedgerRepository.init(context)
        Cloud.init(context)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            ACTION_ACCEPT -> Capture.accept(context, id)
            ACTION_DISMISS -> Capture.dismiss(context, id)
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.chanmin.nabiledger.CAPTURE_ACCEPT"
        const val ACTION_DISMISS = "com.chanmin.nabiledger.CAPTURE_DISMISS"
        const val EXTRA_ID = "id"
    }
}
