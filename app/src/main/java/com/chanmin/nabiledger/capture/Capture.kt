package com.chanmin.nabiledger.capture

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chanmin.nabiledger.cloud.Cloud
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.data.PendingCapture
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.notify.Notifier
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

object Capture {
    private const val PREFS = "nabi_capture"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 사용자가 '알림 접근'을 허용했는지 */
    fun listenerEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    // ---------- 리스너 연결 상태 ----------

    fun markConnected(ctx: Context, connected: Boolean) {
        prefs(ctx).edit()
            .putBoolean("connected", connected)
            .putLong(if (connected) "connectedAt" else "disconnectedAt", System.currentTimeMillis())
            .apply()
    }

    /** 권한은 있는데 시스템이 리스너를 붙여두지 않은 상태인지 */
    fun listenerConnected(ctx: Context): Boolean = prefs(ctx).getBoolean("connected", false)

    fun lastConnectedAt(ctx: Context): Long = prefs(ctx).getLong("connectedAt", 0L)

    fun requestRebind(ctx: Context) {
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(ctx, PaymentListener::class.java))
        }
    }

    /** 끊긴 리스너를 확실히 다시 붙이기: 컴포넌트를 껐다 켜서 시스템이 새로 연결하게 함 */
    fun forceRebind(ctx: Context) {
        val cn = ComponentName(ctx, PaymentListener::class.java)
        val pm = ctx.packageManager
        runCatching {
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        requestRebind(ctx)
    }

    /** 앱을 열 때·업데이트 직후: 권한은 있는데 연결이 안 돼 있으면 다시 연결 */
    fun ensureConnected(ctx: Context) {
        if (listenerEnabled(ctx) && !listenerConnected(ctx)) forceRebind(ctx)
    }

    // ---------- 중복 판정용 최근 기록 ----------

    @Synchronized
    private fun recent(ctx: Context): List<Seen> = try {
        val arr = JSONArray(prefs(ctx).getString("recent", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Seen(o.getString("p"), o.getInt("h"), o.getLong("a"), o.getBoolean("i"), o.getLong("t"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun remember(ctx: Context, s: Seen) {
        val cutoff = s.at - CaptureDedupe.SAME_TEXT_WINDOW
        val list = (recent(ctx).filter { it.at >= cutoff } + s).takeLast(200)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("p", it.pkg); put("h", it.hash); put("a", it.amount); put("i", it.income); put("t", it.at)
            })
        }
        prefs(ctx).edit().putString("recent", arr.toString()).apply()
    }

    // ---------- 처리 ----------

    /**
     * 알림 하나에서 나온 글 조각들(본문, 여러 줄 알림의 각 줄, 대화형 알림의 각 메시지)을 처리.
     * 조각마다 따로 읽어서, 문자가 쌓여 '메시지 2개'로 요약된 알림도 놓치지 않는다.
     */
    @Synchronized
    fun handle(ctx: Context, pkg: String, appLabel: String, title: String, pieces: List<String>, postedAt: Long) {
        LedgerRepository.init(ctx)
        val s = LedgerRepository.settings.value
        if (!s.captureEnabled) return
        val candidates = CaptureDedupe.piecesOf(pieces)
            .map { if (title.isNotBlank() && !it.contains(title)) "$title\n$it" else it }
            .filter { PaymentParser.hasAmount(it) }
        val doneInThis = mutableSetOf<Pair<Long, Boolean>>()
        for (raw in candidates) {
            val parsed = PaymentParser.parse(raw)
            if (parsed != null && !doneInThis.add(parsed.amount to parsed.isIncome)) continue
            handleOne(ctx, s, pkg, appLabel, raw, postedAt)
        }
    }

    private fun handleOne(
        ctx: Context, s: com.chanmin.nabiledger.data.AppSettings,
        pkg: String, appLabel: String, raw: String, postedAt: Long,
    ) {
        val tag = "[$appLabel] "
        val parsed = PaymentParser.parse(raw)
        if (parsed == null) {
            LedgerRepository.logCapture(raw, tag + "건너뜀: " + PaymentParser.whyNot(raw))
            return
        }
        val seen = Seen(pkg, CaptureDedupe.normalize(raw), parsed.amount, parsed.isIncome, postedAt)
        CaptureDedupe.duplicateReason(recent(ctx), seen)?.let { why ->
            // 같은 알림 재게시는 조용히 넘기고, 다른 앱 중복만 기록
            if (!why.startsWith("같은 알림")) LedgerRepository.logCapture(raw, tag + why)
            return
        }
        remember(ctx, seen)

        val day = Instant.ofEpochMilli(postedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val label = (if (parsed.isIncome) "+" else "") + Fmt.won(parsed.amount) +
            (if (parsed.merchant.isNotEmpty()) " · ${parsed.merchant}" else "") + " · ${parsed.category}"

        if (s.captureAutoSave) {
            val e = LedgerRepository.add(day, parsed.amount, parsed.category, parsed.merchant, parsed.isIncome)
            Cloud.push(e)
            LedgerRepository.logCapture(raw, tag + "자동 기록: $label")
            Notifier.postCaptureSaved(ctx, e.id, label)
            if (!e.isIncome) Notifier.maybeAlertOverspend(ctx)
        } else {
            val p = LedgerRepository.addPending(
                day, parsed.amount, parsed.merchant, parsed.category, parsed.isIncome, postedAt
            )
            LedgerRepository.logCapture(raw, tag + "확인 대기: $label")
            Notifier.postPendingCapture(ctx, p)
            if (!p.isIncome) Notifier.maybeAlertOverspend(ctx)
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

    /** 설정 화면 '예시로 테스트' — 누를 때마다 다른 알림으로 취급되도록 시각을 넣음 */
    fun runSample(ctx: Context) {
        val now = System.currentTimeMillis()
        val hm = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date(now))
        handle(ctx, "nabi.sample", "테스트", "카카오페이", listOf("나비분식에서 6,500원 결제했어요 ($hm)"), now)
    }
}

/** 다른 앱의 결제 알림을 읽는 서비스. 설정 > 알림 접근에서 허용해야 동작 */
class PaymentListener : NotificationListenerService() {
    override fun onListenerConnected() {
        Capture.markConnected(applicationContext, true)
    }

    override fun onListenerDisconnected() {
        Capture.markConnected(applicationContext, false)
        Capture.requestRebind(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val pieces = mutableListOf<String>()
        ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { pieces += it }
        ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { pieces += it }
        // 여러 줄 알림(InboxStyle): 한 줄에 한 건씩
        ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { pieces += it.toString() }
        // 대화형 알림(문자앱 등 MessagingStyle): 메시지마다 따로
        runCatching {
            val msgs = NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(n)?.messages.orEmpty()
            // 쌓인 예전 메시지가 다시 기록되지 않도록 최근 10분 안의 메시지만
            msgs.filter { it.timestamp <= 0L || it.timestamp >= sbn.postTime - 10 * 60 * 1000L }
                .takeLast(5).forEach { m -> m.text?.toString()?.let { pieces += it } }
        }
        if (pieces.isEmpty()) n.tickerText?.toString()?.let { pieces += it }

        val label = runCatching {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        runCatching { Capture.handle(applicationContext, sbn.packageName, label, title, pieces, sbn.postTime) }
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
