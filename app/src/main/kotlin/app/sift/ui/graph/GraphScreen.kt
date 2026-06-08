package app.sift.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sift.domain.repository.NoteRepository
import app.sift.ui.components.SiftScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class GraphNode(val id: String, val title: String)

@HiltViewModel
class GraphViewModel @Inject constructor(
    private val repo: NoteRepository,
) : ViewModel() {
    var nodes by mutableStateOf<List<GraphNode>>(emptyList())
        private set
    var edges by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            val rels = repo.allRelations()
            val ids = rels.flatMap { listOf(it.fromNoteId, it.toNoteId) }.toSet()
            val titleById = ids.mapNotNull { id -> repo.getNote(id)?.let { id to it.title } }.toMap()
            nodes = titleById.map { GraphNode(it.key, it.value) }
            edges = rels
                .filter { it.fromNoteId in titleById && it.toNoteId in titleById }
                .map { it.fromNoteId to it.toNoteId }
            loading = false
        }
    }
}

@Composable
fun GraphScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    vm: GraphViewModel = hiltViewModel(),
) {
    val nodeColor = MaterialTheme.colorScheme.primary
    val edgeColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()

    SiftScaffold(title = "知识图谱", onBack = onBack) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                vm.nodes.isEmpty() -> Text(
                    "还没有关联。多沉淀些相关主题的笔记，agent 会自动把它们连起来。",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                else -> {
                    var size by remember { mutableStateOf(IntSize.Zero) }
                    val positions = remember(vm.nodes, size) { circularPositions(vm.nodes, size) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size = it }
                            .pointerInput(positions) {
                                detectTapGestures { tap ->
                                    positions.entries
                                        .minByOrNull { (it.value - tap).getDistanceSquared() }
                                        ?.takeIf { (it.value - tap).getDistance() < 70f }
                                        ?.let { onOpenNote(it.key) }
                                }
                            },
                    ) {
                        // 先画边
                        vm.edges.forEach { (a, b) ->
                            val pa = positions[a]
                            val pb = positions[b]
                            if (pa != null && pb != null) {
                                drawLine(edgeColor, pa, pb, strokeWidth = 2.5f)
                            }
                        }
                        // 再画节点 + 标签
                        vm.nodes.forEach { node ->
                            val p = positions[node.id] ?: return@forEach
                            drawCircle(nodeColor, radius = 20f, center = p)
                            val layout = measurer.measure(
                                node.title.take(6),
                                style = TextStyle(fontSize = 11.sp, color = labelColor),
                            )
                            drawText(
                                layout,
                                topLeft = Offset(p.x - layout.size.width / 2f, p.y + 24f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun circularPositions(nodes: List<GraphNode>, size: IntSize): Map<String, Offset> {
    if (size == IntSize.Zero || nodes.isEmpty()) return emptyMap()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = (minOf(size.width, size.height) / 2f) * 0.72f
    return nodes.mapIndexed { i, node ->
        val angle = 2.0 * PI * i / nodes.size
        node.id to Offset(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
    }.toMap()
}
