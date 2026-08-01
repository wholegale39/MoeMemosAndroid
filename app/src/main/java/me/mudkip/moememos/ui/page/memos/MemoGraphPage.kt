package me.mudkip.moememos.ui.page.memos

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.util.extractMemoLinks
import me.mudkip.moememos.viewmodel.LocalMemos

/**
 * A node in the memo graph: a memo with its resolved display name.
 */
private data class GraphNode(
    val memo: MemoEntity,
    val displayName: String,
)

/**
 * Visualizes [[memo]] backlinks / relations as a circular graph. Nodes are
 * memos that participate in at least one link; edges are drawn between memos
 * that reference each other. Tapping a node opens that memo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoGraphPage(navController: NavHostController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val memos = memosViewModel.memos.toList()
    val textMeasurer = rememberTextMeasurer()

    // Resolve every memo's [[target]] to a known memo and build a local id -> memo map.
    val nodes: List<GraphNode> = remember(memos) {
        val byIdentifier = memos.associateBy { it.identifier }
        val linkedIds = LinkedHashSet<String>()

        memos.forEach { memo ->
            val targets = extractMemoLinks(memo.content)
            targets.forEach { target ->
                val resolved = byIdentifier.values.firstOrNull { m ->
                    !m.remoteId.isNullOrBlank() && (
                        target == m.remoteId ||
                        target == "memos/${m.remoteId}" ||
                        target.endsWith("/${m.remoteId}") ||
                        m.remoteId.substringAfterLast('/') == target.substringAfterLast('/')
                        )
                }
                if (resolved != null) {
                    linkedIds.add(memo.identifier)
                    linkedIds.add(resolved.identifier)
                }
            }
        }

        linkedIds.mapNotNull { id ->
            val memo = byIdentifier[id] ?: return@mapNotNull null
            val preview = memo.content.lineSequence().firstOrNull()?.trim()?.take(18) ?: ""
            GraphNode(memo, if (preview.isBlank()) memo.identifier.substringAfterLast('/') else preview)
        }.sortedBy { it.memo.date }
    }

    // Edges: memo A -> memo B when A's content links B.
    val edges: List<Pair<Int, Int>> = remember(memos, nodes) {
        val indexById = nodes.mapIndexed { index, node -> node.memo.identifier to index }.toMap()
        val edges = LinkedHashSet<Pair<Int, Int>>()
        nodes.forEachIndexed { fromIndex, node ->
            val targets = extractMemoLinks(node.memo.content)
            targets.forEach { target ->
                val toIndex = nodes.indexOfFirst { m ->
                    !m.memo.remoteId.isNullOrBlank() && (
                        target == m.memo.remoteId ||
                        target == "memos/${m.memo.remoteId}" ||
                        target.endsWith("/${m.memo.remoteId}") ||
                        m.memo.remoteId.substringAfterLast('/') == target.substringAfterLast('/')
                        )
                }
                if (toIndex != -1 && toIndex != fromIndex) {
                    edges.add(fromIndex to toIndex)
                }
            }
        }
        edges.toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = R.string.memo_graph.string) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = R.string.back.string)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = R.string.memo_graph_empty.string,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text(
                    text = R.string.memo_graph_hint.string,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Text(
                    text = R.string.memo_graph_connected.string.format(nodes.size, edges.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp)
                )
                GraphCanvas(
                    nodes = nodes,
                    edges = edges,
                    textMeasurer = textMeasurer,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    onNodeClick = { node ->
                        navController.navigate(
                            "${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(node.memo.identifier)}"
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    nodes: List<GraphNode>,
    edges: List<Pair<Int, Int>>,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
    onNodeClick: (GraphNode) -> Unit,
) {
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val nodeColor = MaterialTheme.colorScheme.primary
    val nodeLabelColor = MaterialTheme.colorScheme.onSurface
    val nodeRadius = 18.dp

    Canvas(
        modifier = modifier.pointerInput(nodes) {
            detectTapGestures { offset ->
                // Circle layout must match the drawing pass below.
                val size = this.size
                val radius = min(size.width, size.height) / 2f - 60f
                val center = Offset(size.width / 2f, size.height / 2f)
                val hitRadius = nodeRadius.toPx() + 10f

                nodes.forEachIndexed { index, node ->
                    val angle = -Math.PI / 2 + 2 * Math.PI * index / nodes.size
                    val pos = Offset(
                        center.x + radius * cos(angle).toFloat(),
                        center.y + radius * sin(angle).toFloat()
                    )
                    if ((pos - offset).getDistance() <= hitRadius) {
                        onNodeClick(node)
                        return@detectTapGestures
                    }
                }
            }
        }
    ) {
        if (nodes.isEmpty()) return@Canvas

        val radius = min(size.width, size.height) / 2 - 60f
        val center = Offset(size.width / 2, size.height / 2)
        val r = nodeRadius.toPx()

        // Edges first (underneath nodes).
        edges.forEach { (from, to) ->
            val fromPos = nodePosition(from, nodes.size, center, radius)
            val toPos = nodePosition(to, nodes.size, center, radius)
            drawLine(
                color = edgeColor,
                start = fromPos,
                end = toPos,
                strokeWidth = 1.5f
            )
        }

        // Nodes + labels.
        nodes.forEachIndexed { index, node ->
            val pos = nodePosition(index, nodes.size, center, radius)
            drawCircle(
                color = nodeColor,
                radius = r,
                center = pos
            )
            val labelStyle = TextStyle(
                color = nodeLabelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            val layout = textMeasurer.measure(node.displayName, labelStyle)
            val labelOffset = Offset(
                pos.x - layout.size.width / 2f,
                pos.y - layout.size.height / 2f
            )
            // Draw a soft background so the label is readable over edges.
            drawRect(
                color = Color.White.copy(alpha = 0.75f),
                topLeft = labelOffset,
                size = Size(layout.size.width.toFloat(), layout.size.height.toFloat())
            )
            drawText(layout, topLeft = labelOffset)
        }
    }
}

private fun nodePosition(index: Int, total: Int, center: Offset, radius: Float): Offset {
    if (total == 0) return center
    val angle = -Math.PI / 2 + 2 * Math.PI * index / total
    return Offset(
        center.x + radius * cos(angle).toFloat(),
        center.y + radius * sin(angle).toFloat()
    )
}
