package com.chanmin.nabiledger.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.cloud.Cloud
import com.chanmin.nabiledger.cloud.Friend
import com.chanmin.nabiledger.cloud.Profile
import com.chanmin.nabiledger.data.Entry
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.logic.Stats
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun FriendsScreen(padding: PaddingValues) {
    if (!Cloud.available()) {
        CenterNote(padding, "🔧 친구 기능을 쓰려면 Firebase 연결이 필요하다냥.\nREADME의 '친구 기능 켜기'를 따라 해줘냥.")
        return
    }
    val profile by Cloud.profile.collectAsState()
    val p = profile
    if (p == null) {
        ProfileSetup(padding)
    } else {
        FriendsHome(padding, p)
    }
}

@Composable
private fun CenterNote(padding: PaddingValues, text: String) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabiCat(com.chanmin.nabiledger.logic.Mood.CURIOUS, Modifier.size(140.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center, color = Nabi.Brown)
    }
}

@Composable
private fun ProfileSetup(padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabiCat(com.chanmin.nabiledger.logic.Mood.HAPPY, Modifier.size(140.dp))
        Spacer(Modifier.height(12.dp))
        Text("친구랑 서로 쓴 걸 볼 수 있다냥!", fontWeight = FontWeight.Bold, color = Nabi.Brown)
        Text(
            "🙈 숨긴 기록은 절대 공유되지 않아요",
            fontSize = 12.sp, color = Nabi.BrownSoft
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(12) },
            label = { Text("친구에게 보일 이름") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        Cloud.saveProfile(name)
                        Cloud.syncAll(LedgerRepository.entries.value)
                    } catch (e: Exception) {
                        error = e.message ?: "시작하지 못했다냥"
                    }
                    busy = false
                }
            },
            enabled = name.isNotBlank() && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Nabi.OrangeDeep),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "준비 중…" else "시작하기") }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("⚠️ $it", color = Nabi.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

private data class FriendData(val friend: Friend, val entries: List<Entry>, val failed: Boolean)

@Composable
private fun FriendsHome(padding: PaddingValues, me: Profile) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val todayDate = LocalDate.now()
    val today = todayDate.toEpochDay()
    val monthStart = todayDate.withDayOfMonth(1).toEpochDay()

    var data by remember { mutableStateOf<List<FriendData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<FriendData?>(null) }
    var code by remember { mutableStateOf("") }
    var addMsg by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(me.uid, refresh) {
        loading = true
        loadError = null
        try {
            val from = minOf(today - Stats.WINDOW_DAYS, monthStart)
            data = Cloud.friends().map { f ->
                val result = runCatching { Cloud.friendEntries(f.uid, from) }
                FriendData(f, result.getOrDefault(emptyList()), result.isFailure)
            }
        } catch (e: Exception) {
            loadError = e.message ?: "친구 목록을 못 불러왔다냥"
        }
        loading = false
    }

    opened?.let { fd ->
        BackHandler { opened = null }
        FriendDetail(
            fd, padding, today, monthStart,
            onBack = { opened = null },
            onRemove = {
                scope.launch {
                    runCatching { Cloud.removeFriend(fd.friend.uid) }
                    opened = null
                    refresh++
                }
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WhiteCard {
                Text("내 친구 코드", fontSize = 12.sp, color = Nabi.BrownSoft)
                Text(
                    me.code, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = Nabi.OrangeDeep, letterSpacing = 3.sp
                )
                Text("${me.nickname} · 이 코드를 아는 친구는 내 기록(숨긴 것 제외)을 볼 수 있어요",
                    fontSize = 12.sp, color = Nabi.BrownSoft)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(me.code)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("복사") }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "🐾 나비 가계부에서 친구하자냥! 내 친구 코드: ${me.code}"
                                )
                            }
                            ctx.startActivity(Intent.createChooser(send, "친구 코드 보내기"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Nabi.OrangeDeep),
                        modifier = Modifier.weight(1f)
                    ) { Text("공유") }
                }
            }
        }
        item {
            WhiteCard {
                Text("친구 추가", fontWeight = FontWeight.Bold, color = Nabi.Brown)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.take(8).uppercase() },
                        label = { Text("친구 코드 8자리") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            adding = true
                            addMsg = null
                            scope.launch {
                                addMsg = try {
                                    val f = Cloud.addFriend(code)
                                    code = ""
                                    refresh++
                                    "${f.nickname}님과 친구가 됐다냥! 🎉"
                                } catch (e: Exception) {
                                    "⚠️ " + (e.message ?: "추가하지 못했다냥")
                                }
                                adding = false
                            }
                        },
                        enabled = code.length == 8 && !adding,
                        colors = ButtonDefaults.buttonColors(containerColor = Nabi.OrangeDeep),
                    ) { Text("추가") }
                }
                addMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = Nabi.Brown)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "친구들 (${data.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = Nabi.Brown, modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = { refresh++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "새로고침", tint = Nabi.BrownSoft)
                }
            }
        }
        when {
            loading -> item {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Nabi.Orange)
                }
            }
            loadError != null -> item { EmptyNote("⚠️ $loadError") }
            data.isEmpty() -> item { EmptyNote("아직 친구가 없다냥.\n내 코드를 보내거나 친구 코드를 입력해줘냥 🐾") }
            else -> items(data, key = { it.friend.uid }) { fd ->
                FriendRow(fd, today, monthStart) { opened = fd }
            }
        }
    }
}

@Composable
private fun FriendRow(fd: FriendData, today: Long, monthStart: Long, onClick: () -> Unit) {
    val verdict = Stats.verdict(fd.entries, today, 1.5f)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = moodBg(verdict.mood)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NabiCat(verdict.mood, Modifier.size(64.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(fd.friend.nickname, fontWeight = FontWeight.Bold, color = Nabi.Ink)
                if (fd.failed) {
                    Text("기록을 볼 수 없다냥 (친구가 끊었을 수도)", fontSize = 12.sp, color = Nabi.BrownSoft)
                } else {
                    Text(
                        "오늘 ${Fmt.won(Stats.expenseOn(fd.entries, today))} · 이번 달 ${
                            Fmt.won(Stats.expenseBetween(fd.entries, monthStart, today))
                        }",
                        fontSize = 12.sp, color = Nabi.BrownSoft
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendDetail(
    fd: FriendData,
    padding: PaddingValues,
    today: Long,
    monthStart: Long,
    onBack: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val verdict = Stats.verdict(fd.entries, today, 1.5f)
    val byDay = fd.entries.filter { it.epochDay in monthStart..today }
        .sortedByDescending { it.createdAt }
        .groupBy { it.epochDay }
        .toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Nabi.Brown)
                }
                Text(
                    "${fd.friend.nickname}의 가계부", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = Nabi.Brown, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { confirmRemove = true }) {
                    Text("친구 끊기", color = Nabi.BrownSoft, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = moodBg(verdict.mood)),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NabiCat(verdict.mood, Modifier.size(112.dp))
                    SpeechBubble(verdict.message, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("오늘", Fmt.won(Stats.expenseOn(fd.entries, today)), Modifier.weight(1f))
                StatTile(
                    "평소 하루",
                    Stats.usualDaily(fd.entries, today)?.let { Fmt.won(it) } ?: "모으는 중",
                    Modifier.weight(1f)
                )
                StatTile("이번 달", Fmt.won(Stats.expenseBetween(fd.entries, monthStart, today)), Modifier.weight(1f))
            }
        }
        item {
            Text(
                "🙈 친구가 숨긴 기록은 여기에 보이지 않아요",
                fontSize = 11.sp, color = Nabi.BrownSoft,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
        if (byDay.isEmpty()) {
            item { EmptyNote("이번 달 공유된 기록이 없다냥") }
        }
        byDay.forEach { (day, list) ->
            item(key = "h$day") {
                SectionTitle(Fmt.dayLabel(day), "지출 " + Fmt.won(list.filter { !it.isIncome }.sumOf { it.amount }))
            }
            items(list, key = { it.id }) { EntryRow(it) }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = Nabi.Cream,
            title = { Text("친구를 끊을까냥?") },
            text = { Text("서로의 기록을 더 이상 볼 수 없게 돼요.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text("끊기", color = Nabi.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("취소") }
            },
        )
    }
}
