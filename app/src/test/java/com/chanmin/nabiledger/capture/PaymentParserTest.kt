package com.chanmin.nabiledger.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {

    private fun parse(s: String) = PaymentParser.parse(s)

    @Test fun cardSmsMultiline() {
        val p = parse("[Web발신]\n신한카드(1234)승인\n박*민\n12,000원(일시불)\n10/14 13:20\n스타벅스강남\n누적1,234,560원")
        assertNotNull(p); p!!
        assertEquals(12_000L, p.amount)
        assertEquals("스타벅스강남", p.merchant)
        assertEquals("카페·간식", p.category)
        assertFalse(p.isIncome)
    }

    @Test fun cardPushTimeThenMerchant() {
        val p = parse("KB국민카드1234승인\n홍*동님\n35,000원 일시불\n10/14 12:30 배달의민족\n누적 456,000원")!!
        assertEquals(35_000L, p.amount)
        assertEquals("배달의민족", p.merchant)
        assertEquals("식비", p.category)
    }

    @Test fun kakaoPayStyle() {
        val p = parse("카카오페이\nGS25 역삼점에서 3,200원 결제")!!
        assertEquals(3_200L, p.amount)
        assertEquals("GS25 역삼점", p.merchant)
        assertEquals("식비", p.category)
    }

    @Test fun tossStyleMerchantBeforeAmount() {
        val p = parse("토스\n스타벅스 4,500원 결제")!!
        assertEquals(4_500L, p.amount)
        assertEquals("스타벅스", p.merchant)
    }

    @Test fun bankWithdrawalSkipsBalance() {
        val p = parse("잔액 30,000원\n출금 15,000원 편의점")!!
        assertEquals(15_000L, p.amount)
        assertEquals("편의점", p.merchant)
    }

    @Test fun bankOneLine() {
        val p = parse("[KB국민] 홍*동님 10/14 13:05 출금 8,900원 넷플릭스 잔액 120,000원")!!
        assertEquals(8_900L, p.amount)
        assertEquals("넷플릭스", p.merchant)
        assertEquals("문화·취미", p.category)
    }

    @Test fun incomeDeposit() {
        val p = parse("입금 500,000원\n월급 (주)나비상사\n잔액 1,234,000원")!!
        assertTrue(p.isIncome)
        assertEquals(500_000L, p.amount)
        assertEquals("기타 수입", p.category)
    }

    @Test fun cancelIgnored() {
        assertNull(parse("신한카드(1234)승인취소 12,000원 스타벅스"))
    }

    @Test fun adIgnored() {
        assertNull(parse("(광고) 지금 결제하면 5,000원 할인!"))
    }

    @Test fun billNoticeIgnored() {
        assertNull(parse("[신한카드] 이번 달 결제 예정 금액 320,000원"))
    }

    @Test fun nonPaymentIgnored() {
        assertNull(parse("택배가 도착했어요"))
        assertNull(parse("결제 수단을 등록해 주세요"))
    }

    @Test fun unknownMerchantDefaultsToEtc() {
        assertEquals("기타", PaymentParser.guessCategory("나비문구"))
        assertEquals("교통", PaymentParser.guessCategory("카카오T 택시"))
    }

    // ---------- 송금 ----------

    @Test fun tossSendMoney() {
        val p = parse("토스\n홍길동님에게 30,000원 보냈어요")!!
        assertFalse(p.isIncome)
        assertEquals(30_000L, p.amount)
        assertEquals("홍길동", p.merchant)
        assertEquals("송금", p.category)
    }

    @Test fun kakaoSendMoney() {
        val p = parse("카카오페이 송금\n박나비님께 5,000원 송금 완료")!!
        assertFalse(p.isIncome)
        assertEquals(5_000L, p.amount)
        assertEquals("박나비", p.merchant)
        assertEquals("송금", p.category)
    }

    @Test fun receiveMoneyFromFriend() {
        val p = parse("카카오페이\n김*민님이 15,000원을 보냈어요")!!
        assertTrue(p.isIncome)
        assertEquals(15_000L, p.amount)
        assertEquals("김*민", p.merchant)
        assertEquals("받은 돈", p.category)
    }

    @Test fun bankTransferIn() {
        val p = parse("[신한] 이체입금 200,000원 홍길동 잔액 1,500,000원")!!
        assertTrue(p.isIncome)
        assertEquals(200_000L, p.amount)
        assertEquals("홍길동", p.merchant)
    }

    @Test fun bankTransferOut() {
        val p = parse("[국민] 출금 50,000원 이체 홍길동 잔액 300,000원")!!
        assertFalse(p.isIncome)
        assertEquals(50_000L, p.amount)
    }

    @Test fun topUpAndOwnAccountIgnored() {
        assertNull(parse("토스머니 50,000원 충전 완료"))
        assertNull(parse("내 계좌로 100,000원 이체했어요"))
    }
}
