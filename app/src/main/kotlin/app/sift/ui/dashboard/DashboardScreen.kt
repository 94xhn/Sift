package app.sift.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sift.capture.UsageStatsReader
import app.sift.domain.dashboard.HonestNudge
import app.sift.domain.model.DailyUsage
import app.sift.domain.repository.NoteRepository
import app.sift.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DashboardState(
    val todayScrollMinutes: Long = 0,
    val todayKept: Int = 0,
    val totalNotes: Int = 0,
    val reviewedNotes: Int = 0,
    val hasUsagePermission: Boolean = true,
    val nudge: String = "",
) {
    /** 收藏利用率 = 回看过的笔记 / 总笔记。难看也照实显示——这是与自欺 App 的分水岭。 */
    val utilizationPercent: Int
        get() = if (totalNotes == 0) 0 else (reviewedNotes * 100 / totalNotes)
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    noteRepo: NoteRepository,
    private val usageRepo: UsageRepository,
    private val usageStats: UsageStatsReader,
) : ViewModel() {

    private val today = LocalDate.now().toString()

    init {
        // 把今天的刷视频时长刷进库（有"使用情况访问"权限才有值）
        viewModelScope.launch {
            usageRepo.setScrollMillis(today, usageStats.shortVideoMillisToday())
        }
    }

    val state = combine(
        noteRepo.observeNotes(),
        usageRepo.observeRecent(7),
    ) { notes, days ->
        val todayUsage = days.firstOrNull { it.date == today } ?: DailyUsage(today, 0L, 0, 0)
        DashboardState(
            todayScrollMinutes = todayUsage.scrollMillis / 60_000L,
            todayKept = todayUsage.keptCount,
            totalNotes = notes.size,
            reviewedNotes = notes.count { it.reviewCount > 0 },
            hasUsagePermission = usageStats.hasPermission(),
            nudge = HonestNudge.forToday(todayUsage),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val s by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("今天", style = MaterialTheme.typography.headlineSmall)

        // 诚实提示——这一行是整个 App 的灵魂
        Card(Modifier.fillMaxWidth()) {
            Text(
                s.nudge,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // 三个并排的诚实数字
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(Modifier.weight(1f), "刷视频", "${s.todayScrollMinutes}", "分钟")
            StatCard(Modifier.weight(1f), "今日沉淀", "${s.todayKept}", "条")
            StatCard(Modifier.weight(1f), "收藏利用率", "${s.utilizationPercent}", "%")
        }

        if (!s.hasUsagePermission) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "未授予「使用情况访问」权限，刷视频时长显示为 0。授予后才能照出真实数据。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    ) {
                        Text("去授予")
                    }
                }
            }
        }

        Text(
            "利用率 = 回看过的笔记 / 总笔记（共 ${s.totalNotes} 条，回看 ${s.reviewedNotes} 条）。" +
                "存了不看等于没存——这个数字故意做得诚实。",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, unit: String) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(unit, style = MaterialTheme.typography.labelSmall)
        }
    }
}
