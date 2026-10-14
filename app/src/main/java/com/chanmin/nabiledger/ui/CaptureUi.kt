package com.chanmin.nabiledger.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.capture.Capture
import com.chanmin.nabiledger.data.AppSettings
import com.chanmin.nabiledger.data.Categories
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.data.PendingCapture
import com.chanmin.nabiledger.logic.Fmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 홈 화면의 '기록할까냥?' 카드 */
@Composable
fun PendingCard(p: PendingCapture) {
    val ctx = LocalContext.current
    var category by remember(p.id) { mutableStateOf(p.category) }
    var menu by remember { mutableStateOf(false) }
    val cats = if (p.isIncome) Categories.income else Categories.expense

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Nabi.Warn),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White)
                            .clickable { menu = true },
                        contentAlignment = Alignment.Center
                    ) { Text(Categories.emojiOf(category), fontSize = 20.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        cats.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.emoji} ${c.name}") },
                                onClick = { category = c.name; menu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.merchant.ifEmpty { "(가게 이름 없음)" },
                        fontWeight = FontWeight.SemiBold, color = Nabi.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$category · ${Fmt.dayLabel(p.epochDay)} · 아이콘을 눌러 분류 변경",
                        fontSize = 11.sp, color = Nabi.BrownSoft
                    )
                }
                Text(
                    (if (p.isIncome) "+" else "-") + Fmt.won(p.amount),
                    fontWeight = FontWeight.Bold,
                    color = if (p.isIncome) Nabi.MintDeep else Nabi.Brown
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { Capture.dismiss(ctx, p.id) },
                    modifier = Modifier.weight(1f)
                ) { Text("무시") }
                Button(
                    onClick = { Capture.accept(ctx, p.id, category) },
                    colors = ButtonDefaults.buttonColors(containerColor = Nabi.OrangeDeep),
                    modifier = Modifier.weight(1f)
                ) { Text("기록할래냥") }
            }
        }
    }
}

/** 설정 화면의 결제 알림 자동 기록 카드 */
@Composable
fun CaptureSettingsCard(settings: AppSettings, resumeTick: Int, update: ((AppSettings) -> AppSettings) -> Unit) {
    val ctx = LocalContext.current
    val enabled = remember(resumeTick) { Capture.listenerEnabled(ctx) }
    var showLog by remember { mutableStateOf(false) }
    var logTick by remember { mutableStateOf(0) }
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = Nabi.Orange, checkedThumbColor = Nabi.Fur,
        uncheckedTrackColor = Nabi.CreamDeep, uncheckedThumbColor = Nabi.BrownSoft,
        uncheckedBorderColor = Nabi.CreamDeep,
    )

    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📱 결제 알림 자동 기록", fontWeight = FontWeight.Bold, color = Nabi.Brown)
                Text("카드·은행·페이 앱 알림을 읽어 기록해요", fontSize = 12.sp, color = Nabi.BrownSoft)
            }
            Switch(
                checked = settings.captureEnabled,
                onCheckedChange = { on -> update { it.copy(captureEnabled = on) } },
                colors = switchColors,
            )
        }

        if (settings.captureEnabled) {
            Spacer(Modifier.height(8.dp))
            if (enabled) {
                Text("✅ 알림 접근 허용됨", fontSize = 13.sp, color = Nabi.MintDeep)
            } else {
                Text(
                    "⚠️ 아직 알림 접근 권한이 없어요. 아래 버튼 → '나비 가계부 결제 감지'를 켜주세요.\n" +
                        "켜지지 않으면: 설정 > 애플리케이션 > 나비 가계부 > ⋮ > '제한된 설정 허용' 후 다시 시도",
                    fontSize = 12.sp, color = Nabi.OrangeDeep
                )
            }
            TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text(if (enabled) "알림 접근 설정 열기" else "알림 접근 허용하러 가기") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("확인 없이 바로 기록", color = Nabi.Brown)
                    Text(
                        "끄면 '기록할까냥?' 하고 먼저 물어봐요 (추천)",
                        fontSize = 11.sp, color = Nabi.BrownSoft
                    )
                }
                Switch(
                    checked = settings.captureAutoSave,
                    onCheckedChange = { on -> update { it.copy(captureAutoSave = on) } },
                    colors = switchColors,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { Capture.runSample(ctx); logTick++ },
                    modifier = Modifier.weight(1f)
                ) { Text("예시로 테스트", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { showLog = !showLog; logTick++ },
                    modifier = Modifier.weight(1f)
                ) { Text(if (showLog) "기록 닫기" else "최근 읽은 알림", fontSize = 13.sp) }
            }
            if (showLog) {
                val log = remember(logTick) { LedgerRepository.captureLog() }
                val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.KOREA) }
                Spacer(Modifier.height(8.dp))
                if (log.isEmpty()) {
                    Text("아직 읽은 결제 알림이 없다냥", fontSize = 12.sp, color = Nabi.BrownSoft)
                }
                log.forEach { l ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Nabi.Cream).padding(8.dp)
                    ) {
                        Text("${fmt.format(Date(l.at))} · ${l.result}", fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, color = Nabi.Brown)
                        Text(l.text, fontSize = 11.sp, color = Nabi.BrownSoft)
                    }
                }
                Text(
                    "잘못 읽힌 알림이 있으면 이 화면을 캡처해서 알려주세요 (기기 안에만 저장돼요)",
                    fontSize = 11.sp, color = Nabi.BrownSoft
                )
            }
        }
    }
}
