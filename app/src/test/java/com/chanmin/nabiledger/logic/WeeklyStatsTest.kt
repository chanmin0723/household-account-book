package com.chanmin.nabiledger.logic

import com.chanmin.nabiledger.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyStatsTest {
    private var nextId = 1L
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun spend(day: Long, amount: Long, cat: String = "식비") =
        Entry(nextId++, day, amount, cat, "", false, 0L)
    private fun income(day: Long, amount: Long) =
        Entry(nextId++, day, amount, "월급", "", true, 0L)

    private val mon = day(2026, 11, 16)   // 월요일
    private val tue = day(2026, 11, 17)   // 화요일 (오늘)

    @Test fun weekStartsOnMonday() {
        assertEquals(mon, Stats.weekStart(mon))
        assertEquals(mon, Stats.weekStart(tue))
        assertEquals(mon, Stats.weekStart(day(2026, 11, 22))) // 일요일
        assertEquals(mon + 7, Stats.weekStart(day(2026, 11, 23)))
    }

    @Test fun currentWeekTotalsAndSamePeriodCompare() {
        val entries = listOf(
            spend(mon, 10_000), spend(tue, 5_000, "카페·간식"), spend(tue, 3_000, "카페·간식"),
            income(tue, 100_000),
            // 지난주: 월·화 = 같은 기간, 수요일은 비교에서 빠져야 함
            spend(mon - 7, 4_000), spend(tue - 7, 2_000), spend(mon - 5, 50_000),
            // 다른 주는 무시
            spend(mon - 14, 99_000),
        )
        val w = Stats.week(entries, tue, tue)
        assertTrue(w.isCurrent)
        assertEquals(2, w.daysCounted)
        assertEquals(listOf(10_000L, 8_000L, 0L, 0L, 0L, 0L, 0L), w.daily)
        assertEquals(18_000L, w.total)
        assertEquals(100_000L, w.income)
        assertEquals(6_000L, w.compare)
        assertEquals("식비" to 10_000L, w.byCategory[0])
        assertEquals("카페·간식" to 8_000L, w.byCategory[1])
        assertTrue(Stats.weekCompareText(w).contains("12,000원 더"))
    }

    @Test fun pastWeekComparesFullWeek() {
        val entries = listOf(spend(mon - 7, 7_000), spend(mon - 1, 3_000), spend(mon - 10, 20_000))
        val w = Stats.week(entries, mon - 3, tue)
        assertFalse(w.isCurrent)
        assertEquals(7, w.daysCounted)
        assertEquals(10_000L, w.total)
        assertEquals(20_000L, w.compare)
        assertTrue(Stats.weekCompareText(w).contains("덜 썼다냥"))
    }

    @Test fun reportMentionsTopDay() {
        val entries = listOf(spend(mon, 1_000), spend(tue, 9_000))
        val (title, body) = Stats.weeklyReport(entries, tue)
        assertTrue(title.contains("10,000원"))
        assertTrue(body.contains("화요일"))
    }
}
