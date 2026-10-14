package com.chanmin.nabiledger.logic

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    private val nf: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    fun won(v: Long): String = nf.format(v) + "원"

    /** 달력 칸처럼 좁은 곳용: 8,500 → 8.5천, 23,000 → 2.3만 */
    fun shortWon(v: Long): String = when {
        v >= 100_000_000L -> one(v / 1e8) + "억"
        v >= 10_000L -> one(v / 1e4) + "만"
        v >= 1_000L -> one(v / 1e3) + "천"
        else -> v.toString()
    }

    fun one(d: Double): String {
        val s = String.format(Locale.US, "%.1f", d)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    fun dayLabel(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        return "${d.monthValue}월 ${d.dayOfMonth}일 ($dow)"
    }

    fun timeLabel(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "오전" else "오후"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$ampm $h:" + minute.toString().padStart(2, '0')
    }

    /** 받침 유무에 따라 은/는 */
    fun eunNeun(word: String): String {
        val c = word.lastOrNull { it in '가'..'힣' } ?: return word + "은(는)"
        return word + if ((c - '가') % 28 != 0) "은" else "는"
    }
}
