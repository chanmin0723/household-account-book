package com.chanmin.nabiledger.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.data.AppSettings
import com.chanmin.nabiledger.update.Updater
import kotlinx.coroutines.launch

@Composable
fun UpdateSettingsCard(
    settings: AppSettings,
    resumeTick: Int,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val canInstall = remember(resumeTick) { Updater.canInstall(ctx) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    WhiteCard {
        Text("🔄 자동 업데이트", fontWeight = FontWeight.Bold, color = Nabi.Brown)
        Text(
            "새 버전이 나오면 알아서 받아 설치해요 · 지금 v${Updater.currentName()}",
            fontSize = 12.sp, color = Nabi.BrownSoft
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Updater.MODE_OFF to "끄기",
                Updater.MODE_WIFI to "Wi-Fi에서만",
                Updater.MODE_ANY to "Wi-Fi+데이터",
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = settings.autoUpdate == mode,
                    onClick = {
                        update { it.copy(autoUpdate = mode) }
                        Updater.schedule(ctx)
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Nabi.Orange.copy(alpha = 0.3f),
                        selectedLabelColor = Nabi.Brown,
                    ),
                )
            }
        }
        if (!canInstall) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠️ 앱이 스스로 업데이트하려면 '이 출처 허용'이 필요해요 (한 번만)",
                fontSize = 12.sp, color = Nabi.OrangeDeep
            )
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.packageName)
                    )
                )
            }) { Text("설치 허용하러 가기") }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                busy = true
                status = "확인 중…"
                scope.launch {
                    status = Updater.checkAndInstall(ctx)
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("지금 업데이트 확인") }
        status?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 12.sp, color = Nabi.Brown)
        }
    }
}
