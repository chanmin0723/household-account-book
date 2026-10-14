package com.chanmin.nabiledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.data.AppSettings
import com.chanmin.nabiledger.data.Categories
import com.chanmin.nabiledger.data.Entry
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.logic.Stats
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HistoryScreen(
    entries: List<Entry>,
    settings: AppSettings,
    padding: PaddingValues,
    selectedDay: Long,
    onSelectDay: (Long) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(selectedDay))) }
    val first = month.atDay(1).toEpochDay()
    val last = month.atEndOfMonth().toEpochDay()
    val monthEntries = entries.filter { it.epochDay in first..last }
    val expenseByDay: Map<Long, Long> = monthEntries.filter { !it.isIncome }
        .groupBy { it.epochDay }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
    val usual: Long? = Stats.usualDaily(entries, today)
        ?: expenseByDay.values.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
    val monthExpense = expenseByDay.values.sum()
    val monthIncome = monthEntries.filter { it.isIncome }.sumOf { it.amount }
    val byCategory = monthEntries.filter { !it.isIncome }
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
    val dayEntries = entries.filter { it.epochDay == selectedDay }.sortedByDescending { it.createdAt }

    fun changeMonth(m: YearMonth) {
        month = m
        onSelectDay(if (m == YearMonth.now()) today else m.atDay(1).toEpochDay())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { changeMonth(month.minusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
                    }
                    Text(
                        "${month.year}년 ${month.monthValue}월",
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Nabi.Brown
                    )
                    IconButton(
                        onClick = { changeMonth(month.plusMonths(1)) },
                        enabled = month < YearMonth.now()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
                    }
                }
                Spacer(Modifier.height(8.dp))
                MonthCalendar(month, expenseByDay, usual, settings.threshold, selectedDay, today, onSelectDay)
                Spacer(Modifier.height(10.dp))
                Legend()
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("지출", Fmt.won(monthExpense), Modifier.weight(1f))
                StatTile("수입", Fmt.won(monthIncome), Modifier.weight(1f), accent = Nabi.MintDeep)
                val left = monthIncome - monthExpense
                StatTile(
                    "남은 돈", (if (left < 0) "-" else "") + Fmt.won(kotlin.math.abs(left)),
                    Modifier.weight(1f), accent = if (left < 0) Nabi.Red else Nabi.Brown
                )
            }
        }
        if (byCategory.isNotEmpty()) {
            item {
                WhiteCard {
                    Text("어디에 썼냥?", fontWeight = FontWeight.Bold, color = Nabi.Brown)
                    Spacer(Modifier.height(8.dp))
                    byCategory.forEach { (cat, amt) ->
                        val share = if (monthExpense > 0) amt.toFloat() / monthExpense else 0f
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(Categories.emojiOf(cat), fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(cat, fontSize = 13.sp, color = Nabi.Ink, modifier = Modifier.width(72.dp))
                            LinearProgressIndicator(
                                progress = { share },
                                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Nabi.Orange,
                                trackColor = Nabi.CreamDeep,
                            )
                            Text(
                                Fmt.won(amt), fontSize = 12.sp, color = Nabi.BrownSoft,
                                textAlign = TextAlign.End, modifier = Modifier.width(84.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(
                Fmt.dayLabel(selectedDay),
                "지출 " + Fmt.won(Stats.expenseOn(entries, selectedDay))
            )
        }
        if (dayEntries.isEmpty()) {
            item { EmptyNote(if (selectedDay > today) "아직 오지 않은 날이다냥 🌙" else "이 날은 기록이 없다냥") }
        } else {
            items(dayEntries, key = { it.id }) { EntryRow(it, onDelete) }
        }
    }
}

private fun cellColor(amount: Long, usual: Long?, threshold: Float): Color {
    if (amount <= 0L) return Color.Transparent
    if (usual == null || usual <= 0L) return Nabi.Orange.copy(alpha = 0.25f)
    val r = amount.toFloat() / usual
    return when {
        r <= 1f -> Nabi.Mint.copy(alpha = 0.35f)
        r <= threshold -> Nabi.Orange.copy(alpha = 0.35f)
        else -> Nabi.Red.copy(alpha = 0.35f)
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    expenseByDay: Map<Long, Long>,
    usual: Long?,
    threshold: Float,
    selectedDay: Long,
    today: Long,
    onSelect: (Long) -> Unit,
) {
    val week = listOf("일", "월", "화", "수", "목", "금", "토")
    val offset = month.atDay(1).dayOfWeek.value % 7 // 일요일 시작
    val length = month.lengthOfMonth()
    val rows = (offset + length + 6) / 7

    Column {
        Row {
            week.forEachIndexed { i, w ->
                Text(
                    w, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                    color = when (i) { 0 -> Nabi.Red; 6 -> Nabi.OrangeDeep; else -> Nabi.BrownSoft }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum < 1 || dayNum > length) {
                        Spacer(Modifier.weight(1f).aspectRatio(0.82f))
                    } else {
                        val epoch = month.atDay(dayNum).toEpochDay()
                        val amt = expenseByDay[epoch] ?: 0L
                        val selected = epoch == selectedDay
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.weight(1f).aspectRatio(0.82f).padding(2.dp)
                                .clip(shape)
                                .background(cellColor(amt, usual, threshold))
                                .then(if (selected) Modifier.border(2.dp, Nabi.OrangeDeep, shape) else Modifier)
                                .clickable { onSelect(epoch) }
                                .alpha(if (epoch > today) 0.4f else 1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (epoch == today) "🐾" else dayNum.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected || epoch == today) FontWeight.Bold else FontWeight.Normal,
                                    color = Nabi.Ink
                                )
                                Text(
                                    if (amt > 0) Fmt.shortWon(amt) else " ",
                                    fontSize = 9.sp, color = Nabi.Brown, maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        LegendDot(Nabi.Mint, "평소 이하")
        LegendDot(Nabi.Orange, "조금 많이")
        LegendDot(Nabi.Red, "과소비")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.5f)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Nabi.BrownSoft, style = MaterialTheme.typography.bodySmall)
    }
}
