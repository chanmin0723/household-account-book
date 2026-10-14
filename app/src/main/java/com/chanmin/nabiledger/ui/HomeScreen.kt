package com.chanmin.nabiledger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.data.AppSettings
import com.chanmin.nabiledger.data.Entry
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.logic.Mood
import com.chanmin.nabiledger.logic.Stats
import com.chanmin.nabiledger.logic.Verdict
import java.time.LocalDate
import kotlin.math.roundToInt

fun moodBg(mood: Mood) = when (mood) {
    Mood.HAPPY -> Nabi.MintBg
    Mood.CALM -> Nabi.CreamDeep
    Mood.WORRIED -> Nabi.Warn
    Mood.SHOCKED -> Nabi.Alarm
    Mood.CURIOUS -> Nabi.Peach
}

@Composable
fun HomeScreen(
    entries: List<Entry>,
    settings: AppSettings,
    padding: PaddingValues,
    onDelete: (Entry) -> Unit,
) {
    val todayDate = LocalDate.now()
    val today = todayDate.toEpochDay()
    val verdict = Stats.verdict(entries, today, settings.threshold)
    val spent = Stats.expenseOn(entries, today)
    val usual = Stats.usualDaily(entries, today)
    val monthSpent = Stats.expenseBetween(entries, todayDate.withDayOfMonth(1).toEpochDay(), today)
    val todayEntries = entries.filter { it.epochDay == today }.sortedByDescending { it.createdAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CatHero(verdict) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "오늘 지출", Fmt.won(spent), Modifier.weight(1f),
                    accent = if (verdict.mood == Mood.SHOCKED) Nabi.Red else Nabi.Brown
                )
                StatTile("평소 하루", usual?.let { Fmt.won(it) } ?: "모으는 중", Modifier.weight(1f))
                StatTile("이번 달", Fmt.won(monthSpent), Modifier.weight(1f))
            }
        }
        if (usual != null && usual > 0L) {
            item { Gauge(spent, usual, settings.threshold) }
        }
        item { SectionTitle("오늘 기록", Fmt.dayLabel(today)) }
        if (todayEntries.isEmpty()) {
            item { EmptyNote("아직 오늘 기록이 없다냥.\n아래 + 버튼으로 적어줘냥 🐾") }
        } else {
            items(todayEntries, key = { it.id }) { EntryRow(it, onDelete) }
        }
    }
}

@Composable
private fun CatHero(verdict: Verdict) {
    val bg by animateColorAsState(moodBg(verdict.mood), label = "heroBg")
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NabiCat(verdict.mood, Modifier.size(128.dp))
            SpeechBubble(verdict.message, Modifier.weight(1f).padding(end = 4.dp))
        }
    }
}

/** 오늘 지출 막대. 눈금 = 평소(100%), 막대 끝 = 경고선(평소 × 배수) */
@Composable
private fun Gauge(spent: Long, usual: Long, threshold: Float) {
    val ratio = spent.toFloat() / usual
    val limit = usual * threshold
    val fill = (spent / limit).coerceIn(0f, 1f)
    val color = when {
        ratio <= 1f -> Nabi.Mint
        ratio <= threshold -> Nabi.Orange
        else -> Nabi.Red
    }
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "평소의 ${(ratio * 100).roundToInt()}%",
                fontWeight = FontWeight.Bold, color = Nabi.Brown, modifier = Modifier.weight(1f)
            )
            Text("경고선 ${Fmt.won(limit.toLong())}", fontSize = 12.sp, color = Nabi.BrownSoft)
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(Nabi.CreamDeep, cornerRadius = r)
            if (fill > 0f) {
                drawRoundRect(color, size = Size(size.width * fill, size.height), cornerRadius = r)
            }
            val tickX = size.width / threshold
            drawLine(
                Nabi.Brown, Offset(tickX, -2f), Offset(tickX, size.height + 2f),
                strokeWidth = 3f
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("│ 표시가 평소 하루 평균이에요", fontSize = 11.sp, color = Nabi.BrownSoft)
    }
}
