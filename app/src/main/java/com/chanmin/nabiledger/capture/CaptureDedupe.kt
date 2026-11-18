package com.chanmin.nabiledger.capture

import kotlin.math.abs

/** 최근에 처리한 결제 알림 하나 */
data class Seen(
    val pkg: String,
    val hash: Int,
    val amount: Long,
    val income: Boolean,
    val at: Long,
)

/**
 * 중복 판정 (순수 함수라 단위 테스트로 검증).
 * - 같은 앱이 같은 내용 알림을 다시 띄움(갱신·재게시) → 중복
 * - 다른 앱에서 같은 금액·같은 방향이 3분 안에 옴(문자 + 카드앱 푸시) → 중복
 * - 같은 앱에서 같은 금액이라도 내용이 다르면(커피 두 잔 연속 결제) → 각각 기록
 * - 입금과 출금은 금액이 같아도 항상 따로 기록
 */
object CaptureDedupe {
    /** 여러 줄·대화형 알림은 예전 줄을 계속 다시 보여주므로 넉넉하게 */
    const val SAME_TEXT_WINDOW = 12 * 60 * 60 * 1000L
    const val CROSS_APP_WINDOW = 3 * 60 * 1000L

    fun normalize(raw: String): Int =
        raw.replace(Regex("""\s+"""), " ").trim().hashCode()

    /**
     * 알림 하나에서 나온 조각 정리: 다른 조각에 통째로 들어 있는 조각(짧은 본문 vs 긴 본문)은 버림.
     * 그 뒤 같은 금액·방향이 또 나오면 같은 결제로 봄.
     */
    fun piecesOf(pieces: List<String>): List<String> {
        val cleaned = pieces.map { it.trim().trimEnd('…', '.').trim() }.filter { it.isNotEmpty() }.distinct()
        return cleaned.filter { p -> cleaned.none { o -> o != p && o.contains(p) } }
    }

    /** 중복이면 이유, 아니면 null */
    fun duplicateReason(recent: List<Seen>, s: Seen): String? {
        for (o in recent) {
            val dt = abs(o.at - s.at)
            if (o.pkg == s.pkg && o.hash == s.hash && dt < SAME_TEXT_WINDOW) {
                return "같은 알림이 다시 떠서 건너뜀"
            }
            if (o.pkg != s.pkg && o.amount == s.amount && o.income == s.income && dt < CROSS_APP_WINDOW) {
                return "다른 앱에서 같은 결제 알림이 와서 건너뜀"
            }
        }
        return null
    }
}
