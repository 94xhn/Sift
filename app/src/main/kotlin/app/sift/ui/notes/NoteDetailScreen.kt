package app.sift.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sift.domain.model.KnowledgeNote
import app.sift.domain.repository.NoteRepository
import app.sift.ui.components.SiftScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val repo: NoteRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: String = savedState["id"] ?: ""

    var note by mutableStateOf<KnowledgeNote?>(null)
        private set

    init {
        viewModelScope.launch {
            note = repo.getNote(id)
            // 打开即视为"回看过一次"——喂给诚实仪表盘的利用率
            if (note != null) repo.markReviewed(id, System.currentTimeMillis())
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (id.isNotEmpty()) repo.delete(id)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    vm: NoteDetailViewModel = hiltViewModel(),
) {
    val note = vm.note

    SiftScaffold(
        title = "笔记",
        onBack = onBack,
        actions = {
            if (note != null) {
                IconButton(onClick = { vm.delete(onBack) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (note == null) {
                Text("笔记不存在或已删除", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            Text(note.title, style = MaterialTheme.typography.headlineSmall)

            val meta = buildString {
                append(formatTime(note.createdAt))
                append(" · ")
                append(note.category)
                note.sourceApp?.let { append(" · 来自 $it") }
            }
            Text(meta, style = MaterialTheme.typography.labelMedium)

            if (note.summary.isNotBlank()) {
                Text(note.summary, style = MaterialTheme.typography.bodyLarge)
            }

            if (note.keyPoints.isNotEmpty()) {
                Text("知识点", style = MaterialTheme.typography.titleMedium)
                note.keyPoints.forEach { point ->
                    Text("• $point", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (note.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    note.tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
