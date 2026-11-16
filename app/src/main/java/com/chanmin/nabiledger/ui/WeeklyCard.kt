package com.chanmin.nabiledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.data.Categories
import com.chanmin.nabiledger.data.Entry
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.logic.Stats
import java.time.LocalDate

/** 홈 화면의 '이번 주' 카드. ‹ › 로 지난 주들을 볼 수 있다 */
@Composable
fun WeeklyCard(all: List<Entry>, today: Long, usual: Long?, threshold: Float) {
    var offset by remember { mutableIntStateOf(0) } // 0 = 이번 주, 1 = 지난주 ...
    val w = Stats.week(all, today - 7L * offset, today)
    val start = LocalDate.ofEpochDay(w.start)
    val end = start.plusDays(6)
    val maxBar = maxOf(w.daily.maxOrNull() ?: 0L, 1L)
    val days = listOf("월", "화", "수", "목", "금", "토", "일")
    val firstRecord = all.minOfOrNull { it.epochDay }

    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { offset++ },
                enabled = firstRecord != null && w.start > firstRecord,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "지난주") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (offset) {
                        0 -> "이번 주"
                        1 -> "지난주"
                        else -> "${offset}주 전"
                    },
                    fontWeight = FontWeight.Bold, color = Nabi.Brown
                )
                Text(
                    "${start.monthValue}/${start.dayOfMonth} ~ ${end.monthValue}/${end.dayOfMonth}",
                    fontSize = 11.sp, color = Nabi.BrownSoft
                )
            }
            IconButton(onClick = { offset-- }, enabled = offset > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 주")
            }
        }

        Text(
            Fmt.won(w.total),
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Nabi.Brown,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Text(
            Stats.weekCompareText(w),
            fontSize = 12.sp, color = Nabi.BrownSoft,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))

        // 요일별 막대
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            w.daily.forEachIndexed { i, amt ->
                val d = w.start + i
                val future = d > today
                val color = when {
                    usual == null || usual <= 0L -> Nabi.Orange
                    amt <= usual -> Nabi.Mint
                    amt <= usual * threshold -> Nabi.Orange
                    else -> Nabi.Red
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (amt > 0) Fmt.shortWon(amt) else if (future) "" else "0",
                        fontSize = 9.sp, color = Nabi.BrownSoft, maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier.fillMaxWidth().height(72.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val h = if (amt <= 0) 3f else 6f + 66f * amt / maxBar
                        Box(
                            Modifier.width(18.dp).height(h.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (amt <= 0) Nabi.CreamDeep else color)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (d == today) "🐾" else days[i],
                        fontSize = 11.sp,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            future -> Nabi.BrownSoft.copy(alpha = 0.4f)
                            i == 6 -> Nabi.Red
                            i == 5 -> Nabi.OrangeDeep
                            else -> Nabi.Brown
                        }
                    )
                }
            }
        }

        if (w.byCategory.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("많이 쓴 곳", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Nabi.Brown)
            Spacer(Modifier.height(4.dp))
            w.byCategory.take(3).forEach { (cat, amt) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${Categories.emojiOf(cat)} $cat", fontSize = 13.sp, color = Nabi.Ink, modifier = Modifier.weight(1f))
                    Text(
                        Fmt.won(amt) + if (w.total > 0) "  ${amt * 100 / w.total}%" else "",
                        fontSize = 12.sp, color = Nabi.BrownSoft
                    )
                }
            }
        }
        if (w.income > 0) {
            Spacer(Modifier.height(6.dp))
            Text("들어온 돈 +${Fmt.won(w.income)}", fontSize = 12.sp, color = Nabi.MintDeep)
        }
    }
}
