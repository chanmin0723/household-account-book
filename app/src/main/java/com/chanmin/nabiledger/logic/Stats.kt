package com.chanmin.nabiledger.logic

import com.chanmin.nabiledger.data.Entry
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Mood { CURIOUS, HAPPY, CALM, WORRIED, SHOCKED }

data class Verdict(val mood: Mood, val message: String)

/**
 * 한 주(월~일) 요약.
 * - daily: 월요일부터 7칸, 오늘 이후 날짜는 0
 * - compare: 지난주 '같은 기간'(이번 주면 월~오늘 요일까지, 지난 주들은 7일 전체) 지출
 */
data class WeekSummary(
    val start: Long,
    val daily: List<Long>,
    val total: Long,
    val income: Long,
    val compare: Long,
    val daysCounted: Int,
    val isCurrent: Boolean,
    val byCategory: List<Pair<String, Long>>,
)

/**
 * "평소" = 최근 30일(오늘 제외) 하루 평균 지출.
 * 기록을 시작한 지 3일이 안 됐으면 아직 평소를 모르는 상태로 둔다.
 */
object Stats {
    const val WINDOW_DAYS = 30L
    const val MIN_HISTORY_DAYS = 3L

    fun expenseOn(entries: List<Entry>, day: Long): Long =
        entries.filter { !it.isIncome && it.epochDay == day }.sumOf { it.amount }

    fun expenseBetween(entries: List<Entry>, from: Long, toInclusive: Long): Long =
        entries.filter { !it.isIncome && it.epochDay in from..toInclusive }.sumOf { it.amount }

    fun incomeBetween(entries: List<Entry>, from: Long, toInclusive: Long): Long =
        entries.filter { it.isIncome && it.epochDay in from..toInclusive }.sumOf { it.amount }

    /** (시작일, 일수) — 비교 기간. 기록이 부족하면 null */
    private fun window(entries: List<Entry>, today: Long): Pair<Long, Long>? {
        val first = entries.minOfOrNull { it.epochDay } ?: return null
        val start = maxOf(first, today - WINDOW_DAYS)
        val days = today - start
        return if (days < MIN_HISTORY_DAYS) null else start to days
    }

    fun usualDaily(entries: List<Entry>, today: Long): Long? {
        val (start, days) = window(entries, today) ?: return null
        return expenseBetween(entries, start, today - 1) / days
    }

    fun isOverspent(entries: List<Entry>, today: Long, threshold: Float): Boolean {
        val usual = usualDaily(entries, today) ?: return false
        if (usual <= 0L) return false
        return expenseOn(entries, today).toFloat() > usual.toFloat() * threshold
    }

    /** 오늘 가장 많이 쓴 카테고리가 평소보다 크게 튀었으면 한마디 */
    private fun categoryNote(entries: List<Entry>, today: Long): String? {
        val (start, days) = window(entries, today) ?: return null
        val top = entries.filter { !it.isIncome && it.epochDay == today }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value } ?: return null
        val catUsual = entries
            .filter { !it.isIncome && it.category == top.key && it.epochDay in start until today }
            .sumOf { it.amount }.toFloat() / days
        if (catUsual <= 0f) return "특히 ${top.key}에 평소엔 잘 안 쓰던 돈을 썼다냥."
        val r = top.value / catUsual
        return if (r >= 1.5f) "특히 ${Fmt.eunNeun(top.key)} 평소의 ${Fmt.one(r.toDouble())}배다냥." else null
    }

    fun verdict(entries: List<Entry>, today: Long, threshold: Float): Verdict {
        val spent = expenseOn(entries, today)
        val usual = usualDaily(entries, today)

        if (usual == null) {
            return if (spent == 0L)
                Verdict(Mood.CURIOUS, "기록을 며칠 모아야 평소가 어떤지 알 수 있다냥. 오늘 쓴 걸 알려줘냥!")
            else
                Verdict(Mood.CURIOUS, "오늘 ${Fmt.won(spent)} 썼다냥. 사흘만 더 모으면 평소랑 비교해줄게냥!")
        }
        if (usual == 0L) {
            return if (spent == 0L) Verdict(Mood.HAPPY, "오늘도 무지출이다냥! 최고다냥 ✨")
            else Verdict(Mood.WORRIED, "평소엔 거의 안 쓰는데 오늘은 ${Fmt.won(spent)} 썼다냥…")
        }
        if (spent == 0L) return Verdict(Mood.HAPPY, "오늘은 아직 0원! 이대로 가자냥 🐾")

        val r = spent.toFloat() / usual
        val note = categoryNote(entries, today)?.let { " $it" } ?: ""
        return when {
            r <= 0.7f -> Verdict(Mood.HAPPY, "평소보다 적게 썼다냥! 칭찬해줄게냥 ♡")
            r <= threshold -> Verdict(Mood.CALM, "평소랑 비슷하게 쓰고 있다냥.")
            r <= threshold * 1.6f -> Verdict(
                Mood.WORRIED,
                "평소보다 ${((r - 1f) * 100).roundToInt()}% 더 썼다냥… 조금만 참자냥.$note"
            )
            else -> Verdict(Mood.SHOCKED, "냥?! 평소의 ${Fmt.one(r.toDouble())}배나 썼다냥!!$note")
        }
    }

    /** 매일 알림 문구 (제목, 본문) */
    fun reminder(entries: List<Entry>, today: Long, threshold: Float): Pair<String, String> {
        val hasToday = entries.any { it.epochDay == today }
        if (!hasToday) return "오늘 가계부 썼냥? 🐾" to "아직 오늘 기록이 없다냥. 잊기 전에 적어두자냥!"
        val spent = expenseOn(entries, today)
        return "오늘의 나비 정산 🐾" to "오늘 ${Fmt.won(spent)} — ${verdict(entries, today, threshold).message}"
    }

    // ---------- 주간 ----------

    /** 그 날이 속한 주의 월요일 */
    fun weekStart(day: Long): Long {
        val d = LocalDate.ofEpochDay(day)
        return day - (d.dayOfWeek.value - 1)
    }

    fun week(entries: List<Entry>, anyDay: Long, today: Long): WeekSummary {
        val start = weekStart(anyDay)
        val isCurrent = start == weekStart(today)
        val lastDay = if (isCurrent) today else start + 6
        val daysCounted = (lastDay - start + 1).toInt()
        val daily = (0 until 7).map { i ->
            val d = start + i
            if (d > lastDay) 0L else expenseOn(entries, d)
        }
        val inWeek = entries.filter { it.epochDay in start..lastDay }
        val byCategory = inWeek.filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
        val prevStart = start - 7
        return WeekSummary(
            start = start,
            daily = daily,
            total = daily.sum(),
            income = inWeek.filter { it.isIncome }.sumOf { it.amount },
            compare = expenseBetween(entries, prevStart, prevStart + daysCounted - 1),
            daysCounted = daysCounted,
            isCurrent = isCurrent,
            byCategory = byCategory,
        )
    }

    /** "지난주보다 12,000원 더 썼다냥 (↑15%)" 같은 한 줄 */
    fun weekCompareText(w: WeekSummary): String {
        val label = if (w.isCurrent && w.daysCounted < 7) "지난주 같은 기간" else "지난주"
        if (w.compare == 0L) {
            return if (w.total == 0L) "이번 주도 지난주도 아직 0원이다냥" else "$label 기록이 없어서 비교는 다음 주부터다냥"
        }
        val diff = w.total - w.compare
        val pct = (kotlin.math.abs(diff) * 100.0 / w.compare).roundToInt()
        return when {
            diff > 0 -> "${label}보다 ${Fmt.won(diff)} 더 썼다냥 (↑$pct%)"
            diff < 0 -> "${label}보다 ${Fmt.won(-diff)} 덜 썼다냥 (↓$pct%) ♡"
            else -> "${label}랑 똑같이 썼다냥"
        }
    }

    /** 일요일 밤 한 주 정산 알림 (제목, 본문) */
    fun weeklyReport(entries: List<Entry>, today: Long): Pair<String, String> {
        val w = week(entries, today, today)
        val top = w.byCategory.take(3).joinToString(", ") { "${it.first} ${Fmt.won(it.second)}" }
        val maxIdx = w.daily.indices.maxByOrNull { w.daily[it] } ?: 0
        val days = listOf("월", "화", "수", "목", "금", "토", "일")
        val body = buildString {
            append(weekCompareText(w))
            if (top.isNotEmpty()) append("\n많이 쓴 곳: ").append(top)
            if (w.daily[maxIdx] > 0) append("\n가장 많이 쓴 날: ${days[maxIdx]}요일 ${Fmt.won(w.daily[maxIdx])}")
        }
        return "🐾 이번 주 ${Fmt.won(w.total)} 썼다냥" to body
    }
}
