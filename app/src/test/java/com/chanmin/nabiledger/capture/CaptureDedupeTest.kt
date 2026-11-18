package com.chanmin.nabiledger.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureDedupeTest {
    private val t0 = 1_700_000_000_000L
    private fun seen(pkg: String, text: String, amount: Long, income: Boolean = false, at: Long = t0) =
        Seen(pkg, CaptureDedupe.normalize(text), amount, income, at)

    @Test fun repostedSameNotificationIsDuplicate() {
        val a = seen("kakaopay", "GS25에서 3,200원 결제", 3200)
        val b = seen("kakaopay", "GS25에서  3,200원 결제", 3200, at = t0 + 60 * 60 * 1000L)
        assertNotNull(CaptureDedupe.duplicateReason(listOf(a), b))
    }

    @Test fun twoSamePricePurchasesInSameAppAreBothKept() {
        val a = seen("shinhan", "신한카드 승인 4,500원 10/14 13:20 스타벅스", 4500)
        val b = seen("shinhan", "신한카드 승인 4,500원 10/14 13:21 스타벅스", 4500, at = t0 + 60_000)
        assertNull(CaptureDedupe.duplicateReason(listOf(a), b))
    }

    @Test fun smsAndCardAppForSamePaymentIsDuplicate() {
        val sms = seen("messages", "[Web발신] 신한카드 승인 12,000원", 12000)
        val push = seen("shinhancard", "12,000원 결제되었습니다", 12000, at = t0 + 30_000)
        assertNotNull(CaptureDedupe.duplicateReason(listOf(sms), push))
    }

    @Test fun incomeAndExpenseOfSameAmountAreBothKept() {
        val out = seen("toss", "홍길동님에게 10,000원 보냈어요", 10000, income = false)
        val inc = seen("kakaopay", "홍길동님이 10,000원을 보냈어요", 10000, income = true, at = t0 + 30_000)
        assertNull(CaptureDedupe.duplicateReason(listOf(out), inc))
    }

    @Test fun crossAppAfterWindowIsKept() {
        val a = seen("messages", "출금 5,000원", 5000)
        val b = seen("bank", "5,000원 출금", 5000, at = t0 + CaptureDedupe.CROSS_APP_WINDOW + 1)
        assertNull(CaptureDedupe.duplicateReason(listOf(a), b))
    }

    @Test fun shortTextContainedInBigTextIsDropped() {
        val pieces = CaptureDedupe.piecesOf(
            listOf("신한카드 승인 12,000원", "신한카드 승인 12,000원 일시불 10/14 13:20 스타벅스", "신한카드 승인 12,000원…")
        )
        assertEquals(listOf("신한카드 승인 12,000원 일시불 10/14 13:20 스타벅스"), pieces)
    }

    @Test fun inboxLinesStaySeparate() {
        val pieces = CaptureDedupe.piecesOf(listOf("출금 3,000원 GS25", "출금 8,000원 김밥천국"))
        assertEquals(2, pieces.size)
    }
}
