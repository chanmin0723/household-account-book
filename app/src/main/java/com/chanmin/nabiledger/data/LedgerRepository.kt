package com.chanmin.nabiledger.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/** 기기 내부 저장소(entries.json + SharedPreferences)에 저장하는 단일 저장소 */
object LedgerRepository {
    private lateinit var file: File
    private lateinit var prefs: SharedPreferences
    private var initialized = false

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private lateinit var pendingFile: File

    private val _pending = MutableStateFlow<List<PendingCapture>>(emptyList())
    val pending: StateFlow<List<PendingCapture>> = _pending.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        file = File(app.filesDir, "entries.json")
        prefs = app.getSharedPreferences("nabi_settings", Context.MODE_PRIVATE)
        pendingFile = File(app.filesDir, "pending.json")
        _entries.value = loadEntries()
        _pending.value = loadPending()
        _settings.value = loadSettings()
        initialized = true
    }

    // ---------- entries ----------

    private fun loadEntries(): List<Entry> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Entry.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveEntries(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val text = arr.toString()
        val tmp = File(file.parentFile, "entries.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    @Synchronized
    fun add(day: Long, amount: Long, category: String, memo: String, isIncome: Boolean, hidden: Boolean = false): Entry {
        val now = System.currentTimeMillis()
        val id = maxOf(now, (_entries.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val entry = Entry(id, day, amount, category, memo.trim(), isIncome, now, hidden)
        val list = _entries.value + entry
        saveEntries(list)
        _entries.value = list
        return entry
    }

    /** 기존 기록 수정 (id·createdAt 유지) */
    @Synchronized
    fun update(entry: Entry): Entry {
        val list = _entries.value.map { if (it.id == entry.id) entry else it }
        saveEntries(list)
        _entries.value = list
        return entry
    }

    @Synchronized
    fun setHidden(id: Long, hidden: Boolean): Entry? {
        var changed: Entry? = null
        val list = _entries.value.map {
            if (it.id == id) it.copy(hidden = hidden).also { e -> changed = e } else it
        }
        saveEntries(list)
        _entries.value = list
        return changed
    }

    @Synchronized
    fun delete(id: Long) {
        val list = _entries.value.filterNot { it.id == id }
        saveEntries(list)
        _entries.value = list
    }

    // ---------- settings ----------

    private fun loadSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            reminderEnabled = prefs.getBoolean("reminderEnabled", d.reminderEnabled),
            reminderHour = prefs.getInt("reminderHour", d.reminderHour),
            reminderMinute = prefs.getInt("reminderMinute", d.reminderMinute),
            overspendAlert = prefs.getBoolean("overspendAlert", d.overspendAlert),
            threshold = prefs.getFloat("threshold", d.threshold),
            captureEnabled = prefs.getBoolean("captureEnabled", d.captureEnabled),
            captureAutoSave = prefs.getBoolean("captureAutoSave", d.captureAutoSave),
            liveStatus = prefs.getBoolean("liveStatus", d.liveStatus),
            autoUpdate = prefs.getInt("autoUpdate", d.autoUpdate),
        )
    }

    @Synchronized
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putBoolean("reminderEnabled", s.reminderEnabled)
            .putInt("reminderHour", s.reminderHour)
            .putInt("reminderMinute", s.reminderMinute)
            .putBoolean("overspendAlert", s.overspendAlert)
            .putFloat("threshold", s.threshold)
            .putBoolean("captureEnabled", s.captureEnabled)
            .putBoolean("captureAutoSave", s.captureAutoSave)
            .putBoolean("liveStatus", s.liveStatus)
            .putInt("autoUpdate", s.autoUpdate)
            .apply()
        _settings.value = s
    }

    /** 과소비 알림은 하루 한 번만 */
    fun overspendNotifiedDay(): Long = prefs.getLong("overspendNotifiedDay", Long.MIN_VALUE)

    fun markOverspendNotified(day: Long) {
        prefs.edit().putLong("overspendNotifiedDay", day).apply()
    }

    // ---------- 결제 알림 감지 ----------

    private fun loadPending(): List<PendingCapture> = try {
        if (!pendingFile.exists()) emptyList()
        else {
            val arr = JSONArray(pendingFile.readText())
            (0 until arr.length()).map { PendingCapture.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun savePending(list: List<PendingCapture>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        pendingFile.writeText(arr.toString())
    }

    /** 같은 금액이 3분 안에 이미 감지·기록됐으면 중복(문자+앱푸시 동시 수신 등) */
    @Synchronized
    fun isDuplicateCapture(amount: Long, isIncome: Boolean, postedAt: Long): Boolean {
        val window = 3 * 60 * 1000L
        val inPending = _pending.value.any {
            it.amount == amount && it.isIncome == isIncome && kotlin.math.abs(it.postedAt - postedAt) < window
        }
        val inEntries = _entries.value.any {
            it.amount == amount && it.isIncome == isIncome && kotlin.math.abs(it.createdAt - postedAt) < window
        }
        return inPending || inEntries
    }

    @Synchronized
    fun addPending(day: Long, amount: Long, merchant: String, category: String, isIncome: Boolean, postedAt: Long): PendingCapture {
        val id = maxOf(System.currentTimeMillis(), (_pending.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val p = PendingCapture(id, day, amount, merchant, category, isIncome, postedAt)
        val list = (_pending.value + p).takeLast(50)
        savePending(list)
        _pending.value = list
        return p
    }

    /** 확인 → 실제 기록으로. 이미 처리된 건이면 null */
    @Synchronized
    fun acceptPending(id: Long, category: String? = null): Entry? {
        val p = _pending.value.firstOrNull { it.id == id } ?: return null
        dismissPending(id)
        return add(p.epochDay, p.amount, category ?: p.category, p.merchant, p.isIncome)
    }

    @Synchronized
    fun dismissPending(id: Long) {
        val list = _pending.value.filterNot { it.id == id }
        savePending(list)
        _pending.value = list
    }

    fun captureLog(): List<CaptureLog> = try {
        val arr = JSONArray(prefs.getString("captureLog", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            CaptureLog(o.getLong("at"), o.getString("text"), o.getString("result"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun logCapture(text: String, result: String) {
        val list = (listOf(CaptureLog(System.currentTimeMillis(), text.take(200), result)) + captureLog()).take(15)
        val arr = JSONArray()
        list.forEach {
            arr.put(org.json.JSONObject().apply { put("at", it.at); put("text", it.text); put("result", it.result) })
        }
        prefs.edit().putString("captureLog", arr.toString()).apply()
    }
}
