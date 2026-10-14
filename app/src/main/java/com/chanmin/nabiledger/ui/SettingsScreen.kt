package com.chanmin.nabiledger.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.data.AppSettings
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.notify.Notifier
import com.chanmin.nabiledger.notify.ReminderScheduler

@Composable
fun SettingsScreen(settings: AppSettings, resumeTick: Int, padding: PaddingValues) {
    val ctx = LocalContext.current
    val canExact = remember(resumeTick) { ReminderScheduler.canExact(ctx) }
    val canNotify = remember(resumeTick) { Notifier.canPost(ctx) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun update(f: (AppSettings) -> AppSettings) {
        LedgerRepository.updateSettings(f)
        ReminderScheduler.schedule(ctx)
    }

    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = Nabi.Orange, checkedThumbColor = Nabi.Fur,
        uncheckedTrackColor = Nabi.CreamDeep, uncheckedThumbColor = Nabi.BrownSoft,
        uncheckedBorderColor = Nabi.CreamDeep,
    )

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
                SettingRow("⏰ 매일 알림", "정해진 시간에 오늘 쓴 돈을 알려줘요") {
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { on -> update { it.copy(reminderEnabled = on) } },
                        colors = switchColors,
                    )
                }
                if (settings.reminderEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                TimePickerDialog(
                                    ctx,
                                    { _, h, m -> update { it.copy(reminderHour = h, reminderMinute = m) } },
                                    settings.reminderHour, settings.reminderMinute, false
                                ).show()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("알림 시간", color = Nabi.BrownSoft, modifier = Modifier.weight(1f))
                        Text(
                            Fmt.timeLabel(settings.reminderHour, settings.reminderMinute),
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Nabi.OrangeDeep
                        )
                    }
                    if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                        Warning("알림 권한이 꺼져 있어서 나비가 말을 못 건다냥.") {
                            TextButton(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("알림 허용하기")
                            }
                        }
                    }
                    if (!canExact && Build.VERSION.SDK_INT >= 31) {
                        Warning("'알람 및 리마인더' 권한이 없으면 알림이 몇 분 늦을 수 있어요.") {
                            TextButton(onClick = {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:" + ctx.packageName)
                                    )
                                )
                            }) { Text("정확한 시간으로 받기") }
                        }
                    }
                }
            }
        }
        item {
            WhiteCard {
                SettingRow("😾 과소비 경고", "평소보다 많이 쓰면 나비가 바로 알려줘요") {
                    Switch(
                        checked = settings.overspendAlert,
                        onCheckedChange = { on -> update { it.copy(overspendAlert = on) } },
                        colors = switchColors,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "평소(최근 30일 하루 평균)의 몇 배를 넘으면 경고할까요?",
                    fontSize = 13.sp, color = Nabi.BrownSoft
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.2f to "깐깐 1.2배", 1.5f to "보통 1.5배", 2.0f to "느긋 2배").forEach { (v, label) ->
                        FilterChip(
                            selected = settings.threshold == v,
                            onClick = { update { it.copy(threshold = v) } },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Nabi.Orange.copy(alpha = 0.3f),
                                selectedLabelColor = Nabi.Brown,
                            ),
                        )
                    }
                }
            }
        }
        item { CaptureSettingsCard(settings, resumeTick) { f -> update(f) } }
        item {
            WhiteCard {
                Text("🧪 알림 미리보기", fontWeight = FontWeight.Bold, color = Nabi.Brown)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { Notifier.postDailyReminder(ctx) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("지금 알림 보내보기") }
            }
        }
        item {
            Text(
                "나비 가계부 v1.0 · 기록은 이 휴대폰 안에만 저장돼요 🐾",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center, fontSize = 12.sp, color = Nabi.BrownSoft
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Nabi.Brown)
            Text(desc, fontSize = 12.sp, color = Nabi.BrownSoft)
        }
        trailing()
    }
}

@Composable
private fun Warning(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("⚠️ $text", fontSize = 12.sp, color = Nabi.OrangeDeep)
        action()
    }
}
