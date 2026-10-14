package com.chanmin.nabiledger.logic

import com.chanmin.nabiledger.data.Entry
import kotlin.math.roundToInt

enum class Mood { CURIOUS, HAPPY, CALM, WORRIED, SHOCKED }

data class Verdict(val mood: Mood, val message: String)

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
}
