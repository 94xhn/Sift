package app.sift.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
}

@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    vm: NoteDetailViewModel = hiltViewModel(),
) {
    val note = vm.note

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (note == null) {
            Text("笔记不存在或已删除", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(note.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${note.category}${if (note.sourceApp != null) " · 来自 ${note.sourceApp}" else ""}",
                style = MaterialTheme.typography.labelMedium,
            )
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
                Text("标签：${note.tags.joinToString("、")}", style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}
