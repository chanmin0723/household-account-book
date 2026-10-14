package com.chanmin.nabiledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.data.Categories
import com.chanmin.nabiledger.data.Category
import com.chanmin.nabiledger.logic.Fmt
import java.time.LocalDate

@Composable
fun AddEntryDialog(
    initialDay: Long,
    onDismiss: () -> Unit,
    onSave: (day: Long, amount: Long, category: String, memo: String, isIncome: Boolean) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    var isIncome by remember { mutableStateOf(false) }
    var day by remember { mutableLongStateOf(minOf(initialDay, today)) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.expense.first().name) }
    var memo by remember { mutableStateOf("") }
    val amount = amountText.toLongOrNull() ?: 0L
    val cats = if (isIncome) Categories.income else Categories.expense
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Nabi.Orange.copy(alpha = 0.3f),
        selectedLabelColor = Nabi.Brown,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nabi.Cream,
        title = {
            Text(
                if (isIncome) "💰 들어온 돈" else "🐾 나간 돈",
                fontWeight = FontWeight.Bold, color = Nabi.Brown
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isIncome,
                        onClick = {
                            if (isIncome) {
                                isIncome = false
                                category = Categories.expense.first().name
                            }
                        },
                        label = { Text("지출") },
                        colors = chipColors,
                    )
                    FilterChip(
                        selected = isIncome,
                        onClick = {
                            if (!isIncome) {
                                isIncome = true
                                category = Categories.income.first().name
                            }
                        },
                        label = { Text("수입") },
                        colors = chipColors,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { day -= 1 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "전날")
                    }
                    Text(
                        Fmt.dayLabel(day) + if (day == today) " · 오늘" else "",
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = Nabi.Brown, fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { day += 1 }, enabled = day < today) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음날")
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() }.take(12).trimStart('0') },
                    label = { Text("금액") },
                    suffix = { Text("원") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { if (amount > 0) Text(Fmt.won(amount)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1_000L, 5_000L, 10_000L, 50_000L).forEach { inc ->
                        SuggestionChip(
                            onClick = { amountText = (amount + inc).toString() },
                            label = {
                                Text(
                                    "+" + Fmt.shortWon(inc), fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                cats.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            CategoryPill(c, c.name == category, Modifier.weight(1f)) { category = c.name }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(40) },
                    label = { Text("메모 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(day, amount, category, memo, isIncome) },
                enabled = amount > 0
            ) { Text("저장할래냥", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun CategoryPill(c: Category, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Nabi.Orange.copy(alpha = 0.28f) else Nabi.CreamDeep)
            .then(if (selected) Modifier.border(2.dp, Nabi.OrangeDeep, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(c.emoji, fontSize = 20.sp)
        Text(c.name, fontSize = 10.sp, color = Nabi.Brown, maxLines = 1)
    }
}
