package com.chanmin.nabiledger

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chanmin.nabiledger.cloud.Cloud
import com.chanmin.nabiledger.data.Entry
import com.chanmin.nabiledger.data.LedgerRepository
import com.chanmin.nabiledger.logic.Fmt
import com.chanmin.nabiledger.logic.Stats
import com.chanmin.nabiledger.notify.LiveStatus
import com.chanmin.nabiledger.notify.Notifier
import com.chanmin.nabiledger.notify.ReminderScheduler
import com.chanmin.nabiledger.ui.AddEntryDialog
import com.chanmin.nabiledger.ui.FriendsScreen
import com.chanmin.nabiledger.ui.HistoryScreen
import com.chanmin.nabiledger.ui.HomeScreen
import com.chanmin.nabiledger.ui.Nabi
import com.chanmin.nabiledger.ui.NabiTheme
import com.chanmin.nabiledger.ui.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LedgerRepository.init(this)
        Cloud.init(this)
        enableEdgeToEdge()
        setContent {
            NabiTheme { NabiRoot(resumeTick.intValue) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        ReminderScheduler.schedule(this)
        LiveStatus.update(this)
    }
}

@Composable
private fun NabiRoot(resumeTick: Int) {
    val ctx = LocalContext.current
    val entries by LedgerRepository.entries.collectAsState()
    val settings by LedgerRepository.settings.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var selectedDay by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Cloud.syncAll(LedgerRepository.entries.value)
    }

    val toggleHidden: (Entry) -> Unit = { e ->
        LedgerRepository.setHidden(e.id, !e.hidden)?.let { changed ->
            Cloud.push(changed)
            scope.launch {
                snackbar.showSnackbar(
                    if (changed.hidden) "🙈 친구에게 숨겼다냥" else "👀 친구에게 보이게 했다냥"
                )
            }
        }
    }

    val titles = listOf("오늘", "달력", "친구", "설정")
    Scaffold(
        containerColor = Nabi.Cream,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🐾 나비 가계부", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Nabi.Brown)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Nabi.Peach) {
                listOf(Icons.Filled.Home, Icons.Filled.DateRange, Icons.Filled.Face, Icons.Filled.Settings)
                    .forEachIndexed { i, icon ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, contentDescription = titles[i]) },
                            label = { Text(titles[i]) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Nabi.Brown,
                                selectedTextColor = Nabi.Brown,
                                indicatorColor = Nabi.Orange.copy(alpha = 0.35f),
                                unselectedIconColor = Nabi.BrownSoft,
                                unselectedTextColor = Nabi.BrownSoft,
                            ),
                        )
                    }
            }
        },
        floatingActionButton = {
            if (tab <= 1) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = Nabi.Orange,
                    contentColor = Color.White,
                ) { Icon(Icons.Filled.Add, contentDescription = "기록 추가") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(
                entries, settings, padding,
                onDelete = { pendingDelete = it },
                onToggleHidden = toggleHidden,
                onEdit = { editing = it },
            )
            1 -> HistoryScreen(
                entries, settings, padding,
                selectedDay = selectedDay,
                onSelectDay = { selectedDay = it },
                onDelete = { pendingDelete = it },
                onToggleHidden = toggleHidden,
                onEdit = { editing = it },
            )
            2 -> FriendsScreen(padding)
            else -> SettingsScreen(settings, resumeTick, padding)
        }
    }

    if (showAdd) {
        val today = LocalDate.now().toEpochDay()
        AddEntryDialog(
            initialDay = if (tab == 1) selectedDay else today,
            onDismiss = { showAdd = false },
            onSave = { day, amount, category, memo, isIncome, hidden ->
                val before = Stats.isOverspent(LedgerRepository.entries.value, today, settings.threshold)
                val added = LedgerRepository.add(day, amount, category, memo, isIncome, hidden)
                Cloud.push(added)
                showAdd = false
                if (!isIncome && day == today) {
                    val after = Stats.isOverspent(LedgerRepository.entries.value, today, settings.threshold)
                    if (!before && after) {
                        Notifier.maybeAlertOverspend(ctx)
                        scope.launch { snackbar.showSnackbar("냥?! 오늘 평소보다 많이 썼다냥! 😿") }
                    }
                }
            },
        )
    }

    editing?.let { old ->
        AddEntryDialog(
            initialDay = old.epochDay,
            editing = old,
            onDismiss = { editing = null },
            onSave = { day, amount, category, memo, isIncome, hidden ->
                val updated = LedgerRepository.update(
                    old.copy(
                        epochDay = day, amount = amount, category = category,
                        memo = memo.trim(), isIncome = isIncome, hidden = hidden,
                    )
                )
                Cloud.push(updated)
                editing = null
                Notifier.maybeAlertOverspend(ctx)
                scope.launch { snackbar.showSnackbar("✏️ 고쳤다냥") }
            },
        )
    }

    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Nabi.Cream,
            title = { Text("기록을 지울까냥?") },
            text = { Text("${e.category} ${Fmt.won(e.amount)} · ${Fmt.dayLabel(e.epochDay)}") },
            confirmButton = {
                TextButton(onClick = {
                    LedgerRepository.delete(e.id)
                    Cloud.remove(e.id)
                    pendingDelete = null
                }) { Text("지우기", color = Nabi.Red) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}
