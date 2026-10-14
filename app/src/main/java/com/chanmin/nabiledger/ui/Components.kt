package com.chanmin.nabiledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.data.Categories
import com.chanmin.nabiledger.data.Entry
import com.chanmin.nabiledger.logic.Fmt

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = Nabi.Brown, modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = Nabi.BrownSoft)
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        textAlign = TextAlign.Center,
        color = Nabi.BrownSoft,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Nabi.Brown) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 12.sp, color = Nabi.BrownSoft)
            Text(
                value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EntryRow(e: Entry, onDelete: (Entry) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (e.isIncome) Nabi.MintBg else Nabi.CreamDeep),
                contentAlignment = Alignment.Center
            ) {
                Text(Categories.emojiOf(e.category), fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.category, fontWeight = FontWeight.SemiBold, color = Nabi.Ink)
                if (e.memo.isNotBlank()) {
                    Text(
                        e.memo, style = MaterialTheme.typography.bodySmall, color = Nabi.BrownSoft,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                (if (e.isIncome) "+" else "-") + Fmt.won(e.amount),
                color = if (e.isIncome) Nabi.MintDeep else Nabi.Brown,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { onDelete(e) }) {
                Icon(Icons.Filled.Delete, contentDescription = "삭제", tint = Nabi.BrownSoft.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text, color = Nabi.Ink, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
    }
}

@Composable
fun Spaced(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
