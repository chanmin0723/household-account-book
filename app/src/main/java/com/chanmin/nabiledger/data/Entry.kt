package com.chanmin.nabiledger.data

import org.json.JSONObject

/** 가계부 한 줄. epochDay = LocalDate.toEpochDay() */
data class Entry(
    val id: Long,
    val epochDay: Long,
    val amount: Long,
    val category: String,
    val memo: String,
    val isIncome: Boolean,
    val createdAt: Long,
    /** true면 이 기기에만 남고 친구에게 공유되지 않음 */
    val hidden: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("day", epochDay)
        put("amount", amount)
        put("category", category)
        put("memo", memo)
        put("income", isIncome)
        put("createdAt", createdAt)
        put("hidden", hidden)
    }

    companion object {
        fun fromJson(o: JSONObject): Entry = Entry(
            id = o.getLong("id"),
            epochDay = o.getLong("day"),
            amount = o.getLong("amount"),
            category = o.optString("category", "기타"),
            memo = o.optString("memo", ""),
            isIncome = o.optBoolean("income", false),
            createdAt = o.optLong("createdAt", 0L),
            hidden = o.optBoolean("hidden", false),
        )
    }
}

data class Category(val name: String, val emoji: String)

object Categories {
    val expense = listOf(
        Category("식비", "🍚"),
        Category("카페·간식", "☕"),
        Category("교통", "🚌"),
        Category("쇼핑", "🛍️"),
        Category("생활", "🏠"),
        Category("문화·취미", "🎮"),
        Category("의료", "💊"),
        Category("기타", "🐾"),
    )
    val income = listOf(
        Category("월급", "💰"),
        Category("용돈", "🎁"),
        Category("부수입", "✨"),
        Category("기타 수입", "🐾"),
    )

    fun emojiOf(name: String): String =
        (expense + income).firstOrNull { it.name == name }?.emoji ?: "🐾"
}

data class AppSettings(
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val overspendAlert: Boolean = true,
    /** 평소(최근 30일 하루 평균)의 몇 배를 넘으면 경고할지 */
    val threshold: Float = 1.5f,
)
