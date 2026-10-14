package com.chanmin.nabiledger.capture

data class ParsedPayment(
    val amount: Long,
    val merchant: String,
    val isIncome: Boolean,
    val category: String,
)

/**
 * 카드사·은행·간편결제 알림 문구에서 금액/가게/수입·지출을 뽑아낸다.
 * 순수 Kotlin이라 단위 테스트로 검증 (PaymentParserTest).
 */
object PaymentParser {
    private val amountRe = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")
    private val timeRe = Regex("""\d{1,2}:\d{2}""")
    private val ws = Regex("""\s+""")

    private val spendKw = listOf("승인", "결제", "출금", "사용", "지불", "구매", "송금", "보냈", "이체")
    private val incomeKw = listOf("입금", "받았", "받은 돈")
    private val transferKw = listOf("송금", "보냈", "이체", "받았", "받은 돈")
    /** 다른 사람이 나에게 보낸 것: "김*민님이 15,000원을 보냈어요" */
    private val sentByOther = Regex("""님이\s*[\d,]+\s*원""")
    /** 송금 상대 이름: "홍길동님에게", "박나비님께", "김*민님이" */
    private val counterpartRe = Regex("""([^\s,\[\]()]{1,12}?)님(?:에게|께|한테|이|으로부터)""")
    /**
     * 이런 말이 있으면 실제 지출/수입이 아니라고 본다.
     * 충전·내 계좌 이체는 돈이 내 지갑 안에서 옮겨 다닌 것이라 이중 기록을 막기 위해 제외.
     */
    private val rejectKw = listOf(
        "취소", "거절", "실패", "광고", "예정", "명세서", "청구", "이벤트",
        "충전", "내 계좌", "내계좌", "본인 계좌", "본인계좌",
    )
    /** 금액 바로 앞에 이런 말이 있으면 그 금액은 건너뜀 (잔액 등) */
    private val notTheAmount = listOf("잔액", "누적", "잔고", "한도", "합계", "포인트", "적립", "할인")
    /** 가게 이름이 아니라 결제 수단/알림 머리말인 단어 */
    private val notMerchant = listOf(
        "카드", "페이", "Pay", "pay", "PAY", "은행", "뱅크", "Bank", "발신", "알림", "토스",
        "입출금", "일시불", "할부", "체크", "님",
    )
    private val allKw = spendKw + incomeKw + listOf("완료", "내역", "이용")

    fun parse(raw: String): ParsedPayment? {
        val text = raw.replace(' ', ' ').trim()
        if (text.isEmpty()) return null
        if (rejectKw.any { it in text }) return null

        val incoming = incomeKw.any { it in text } || (sentByOther.containsMatchIn(text) && "보냈" in text)
        val isIncome = incoming && "출금" !in text
        val isSpend = !isIncome && spendKw.any { it in text }
        if (!isSpend && !isIncome) return null
        val isTransfer = transferKw.any { it in text }

        val m = amountRe.findAll(text).firstOrNull { mr ->
            val before = text.substring(maxOf(0, mr.range.first - 6), mr.range.first)
            notTheAmount.none { it in before }
        } ?: return null
        val amount = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        if (amount <= 0L || amount > 100_000_000L) return null

        val counterpart = if (isTransfer) counterpartOf(text) else null
        val merchant = (counterpart ?: merchantOf(text, m)).take(30)
        val category = when {
            isIncome && counterpart != null -> "받은 돈"
            isIncome -> "기타 수입"
            counterpart != null -> "송금"
            else -> guessCategory(merchant, text)
        }
        return ParsedPayment(amount, merchant, isIncome, category)
    }

    private fun counterpartOf(text: String): String? =
        counterpartRe.findAll(text)
            .map { clean(it.groupValues[1]) }
            .firstOrNull { it.isNotEmpty() && it !in listOf("고객", "회원", "사용자") && it.any { c -> c.isLetter() } }

    private val numberish = Regex("""[\d,./:\-()원\s]+""")

    /** 가게 이름 후보로 쓸 만한가: 글자가 있고, 숫자뭉치/마스킹된 이름(박*민)/결제수단/키워드가 아님 */
    private fun good(w: String): Boolean =
        w.length >= 2 &&
            w.any { it.isLetter() } &&
            !w.matches(numberish) &&
            '*' !in w &&
            !amountRe.containsMatchIn(w) &&
            allKw.none { it in w } &&
            notMerchant.none { it in w }

    private fun clean(s: String): String =
        s.trim().trim('[', ']', '(', ')', '.', ',', ':', '-', '·', '|').replace(ws, " ").trim()

    private fun merchantOf(text: String, amount: MatchResult): String {
        // 1) "스타벅스 강남점에서 4,500원 결제"
        Regex("""([^\s,]+(?: [^\s,]+)?)에서""").findAll(text)
            .map { clean(it.groupValues[1]) }
            .firstOrNull { good(it) }
            ?.let { return it }

        // 2) 카드 문자: "... 12,000원 일시불 10/14 13:20 스타벅스강남 누적 ..."
        timeRe.find(text, amount.range.last)?.let { tm ->
            val after = text.substring(tm.range.last + 1).trim()
            val name = clean(
                after.lineSequence().firstOrNull().orEmpty()
                    .split("누적", "잔액", "잔고").first()
            )
            if (name.isNotEmpty() && name.any { it.isLetter() } && allKw.none { it in name }) return name
        }

        // 3) 숫자·키워드가 없는 줄 (예: 가게 이름만 있는 줄)
        text.lines().map { clean(it) }
            .firstOrNull { it.length in 2..25 && good(it) }
            ?.let { return it }

        // 4) 금액 바로 앞 단어, 5) 바로 뒤 단어
        text.substring(0, amount.range.first).trim().split(ws).lastOrNull()
            ?.let { clean(it) }?.takeIf { good(it) }?.let { return it }
        text.substring(amount.range.last + 1).trim().split(ws)
            .map { clean(it) }.firstOrNull { good(it) }
            ?.let { return it }

        return ""
    }

    private val categoryHints: List<Pair<String, List<String>>> = listOf(
        "카페·간식" to listOf(
            "스타벅스", "커피", "카페", "이디야", "투썸", "메가MGC", "메가커피", "컴포즈", "빽다방",
            "할리스", "폴바셋", "베이커리", "파리바게뜨", "뚜레쥬르", "배스킨", "던킨", "공차", "설빙",
        ),
        "식비" to listOf(
            "배달의민족", "배민", "요기요", "쿠팡이츠", "식당", "김밥", "치킨", "맥도날드", "버거",
            "롯데리아", "맘스터치", "서브웨이", "피자", "국밥", "분식", "GS25", "CU", "씨유",
            "세븐일레븐", "이마트24", "편의점", "푸드", "학생식당", "도시락",
        ),
        "교통" to listOf(
            "택시", "카카오T", "카카오모빌리티", "버스", "지하철", "코레일", "SRT", "티머니",
            "캐시비", "주유", "GS칼텍스", "SK에너지", "S-OIL", "현대오일", "고속", "타다", "쏘카",
        ),
        "쇼핑" to listOf(
            "쿠팡", "11번가", "G마켓", "지마켓", "옥션", "무신사", "올리브영", "다이소", "에이블리",
            "지그재그", "29CM", "SSG", "알리", "테무", "백화점", "아울렛",
        ),
        "생활" to listOf(
            "이마트", "홈플러스", "롯데마트", "코스트코", "관리비", "통신", "KT", "SKT", "LGU",
            "LG U", "전기", "가스", "수도", "세탁", "마트",
        ),
        "문화·취미" to listOf(
            "CGV", "메가박스", "롯데시네마", "넷플릭스", "NETFLIX", "유튜브", "YouTube", "스팀",
            "Steam", "멜론", "지니", "교보", "영풍", "알라딘", "예스24", "PC방", "노래", "닌텐도",
        ),
        "의료" to listOf("약국", "병원", "의원", "치과", "한의원", "의료"),
    )

    fun guessCategory(merchant: String, fullText: String = ""): String {
        for (target in listOf(merchant, fullText)) {
            if (target.isBlank()) continue
            for ((cat, hints) in categoryHints) {
                if (hints.any { target.contains(it, ignoreCase = true) }) return cat
            }
        }
        return "기타"
    }
}
