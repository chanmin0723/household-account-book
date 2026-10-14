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

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        file = File(app.filesDir, "entries.json")
        prefs = app.getSharedPreferences("nabi_settings", Context.MODE_PRIVATE)
        _entries.value = loadEntries()
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
    fun add(day: Long, amount: Long, category: String, memo: String, isIncome: Boolean): Entry {
        val now = System.currentTimeMillis()
        val id = maxOf(now, (_entries.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val entry = Entry(id, day, amount, category, memo.trim(), isIncome, now)
        val list = _entries.value + entry
        saveEntries(list)
        _entries.value = list
        return entry
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
            .apply()
        _settings.value = s
    }

    /** 과소비 알림은 하루 한 번만 */
    fun overspendNotifiedDay(): Long = prefs.getLong("overspendNotifiedDay", Long.MIN_VALUE)

    fun markOverspendNotified(day: Long) {
        prefs.edit().putLong("overspendNotifiedDay", day).apply()
    }
}
