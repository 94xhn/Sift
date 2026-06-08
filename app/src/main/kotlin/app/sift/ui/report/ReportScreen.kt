package app.sift.ui.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sift.domain.agent.WeeklyReportAgent
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.repository.NoteRepository
import app.sift.domain.repository.SettingsRepository
import app.sift.domain.util.Clock
import app.sift.ui.components.SiftScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
    private val provider: LLMProvider,
    private val agent: WeeklyReportAgent,
    private val clock: Clock,
) : ViewModel() {

    var loading by mutableStateOf(true)
        private set
    var report by mutableStateOf("")
        private set
    var noteCount by mutableStateOf(0)
        private set

    init { generate() }

    fun generate() {
        viewModelScope.launch {
            loading = true
            val key = settings.getApiKey()
            if (key.isNullOrBlank()) {
                report = "未配置 API Key，请先在设置里填写。"
                loading = false
                return@launch
            }
            val since = clock.nowMillis() - SEVEN_DAYS_MS
            val week = notes.notesSince(since)
            noteCount = week.size
            report = agent.generate(week, provider, LlmConfig(settings.getBaseUrl(), key, settings.getModel()))
            loading = false
        }
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    vm: ReportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    SiftScaffold(
        title = "本周周报",
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.generate() }, enabled = !vm.loading) {
                Icon(Icons.Default.Refresh, contentDescription = "重新生成")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.loading) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text("正在生成本周周报…", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            Text(
                "基于本周 ${vm.noteCount} 条笔记",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(vm.report, style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { copyToClipboard(context, vm.report) },
                    modifier = Modifier.weight(1f),
                ) { Text("复制") }
                OutlinedButton(
                    onClick = { shareText(context, vm.report) },
                    modifier = Modifier.weight(1f),
                ) { Text("分享") }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Sift 周报", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "分享周报"))
}
