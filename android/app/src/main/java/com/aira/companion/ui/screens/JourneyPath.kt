package com.aira.companion.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aira.companion.model.Milestone
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.Sage
import com.aira.companion.ui.theme.SageMist

private data class PathNode(
    val week: Int,
    val label: String,
    val emoji: String,
    val isHere: Boolean = false,
)

/** The journey as a winding path (client-requested, Duolingo-style layout)
 * ADAPTED for a health product: nodes are the stage's real MILESTONES plus a
 * "you are here" marker — never lessons; nothing is locked, nothing is
 * gamified. Reads bottom-to-top: the journey starts at the bottom, the due
 * date waits at the top. Passed milestones get a gentle check. Tapping a
 * node browses that week's guide. */
@Composable
fun JourneyPathTimeline(
    milestones: List<Milestone>,
    currentWeek: Int?,
    shownWeek: Int?,
    sizeEmoji: String?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showStartLabel: Boolean = true,
) {
    val nodes = buildList {
        addAll(milestones.map { PathNode(it.week, it.label, it.emoji) })
        if (currentWeek != null && milestones.none { it.week == currentWeek }) {
            add(PathNode(currentWeek, "You are here", sizeEmoji ?: "📍", isHere = true))
        }
    }.sortedByDescending { it.week } // due date on top, the beginning at the bottom

    val rowHeight = 96.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight * nodes.size),
    ) {
        val width = maxWidth
        val leftX = width * 0.28f
        val rightX = width * 0.72f

        // The winding ribbon behind the nodes.
        Canvas(Modifier.fillMaxSize()) {
            val xFor = { i: Int ->
                (if (i % 2 == 0) leftX else rightX).toPx()
            }
            val yFor = { i: Int -> rowHeight.toPx() * i + rowHeight.toPx() / 2f }
            val path = Path()
            nodes.indices.forEach { i ->
                val x = xFor(i)
                val y = yFor(i)
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prevX = xFor(i - 1)
                    val prevY = yFor(i - 1)
                    val midY = (prevY + y) / 2f
                    path.cubicTo(prevX, midY, x, midY, x, y)
                }
            }
            drawPath(
                path,
                color = Lilac.copy(alpha = 0.45f),
                style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        nodes.forEachIndexed { i, node ->
            val onLeft = i % 2 == 0
            val nodeX = if (onLeft) leftX else rightX
            val past = currentWeek != null && node.week < currentWeek && !node.isHere
            val selected = shownWeek == node.week

            // Node circle, centred on the path.
            Surface(
                onClick = { onSelect(node.week) },
                shape = CircleShape,
                color = when {
                    node.isHere -> Plum
                    past -> SageMist
                    else -> Paper
                },
                contentColor = if (node.isHere) Paper else Ink,
                shadowElevation = if (node.isHere || selected) 4.dp else 1.dp,
                border = androidx.compose.foundation.BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    when {
                        node.isHere || selected -> Plum
                        past -> Sage
                        else -> OutlineSoft
                    },
                ),
                modifier = Modifier
                    .offset(x = nodeX - 28.dp, y = rowHeight * i + (rowHeight - 56.dp) / 2)
                    .size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (past) "✓" else node.emoji,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (past) Sage else Ink,
                    )
                }
            }

            // Label card on the opposite side of the ribbon.
            val cardWidth = width * 0.42f
            Surface(
                onClick = { onSelect(node.week) },
                shape = RoundedCornerShape(14.dp),
                color = if (node.isHere) Plum else Paper,
                contentColor = if (node.isHere) Paper else Ink,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .offset(
                        x = if (onLeft) width - cardWidth - 8.dp else 8.dp,
                        y = rowHeight * i + (rowHeight - 64.dp) / 2,
                    )
                    .width(cardWidth),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (node.isHere) Paper else Ink,
                    )
                    Text(
                        text = if (node.isHere) "Week ${node.week}" else "Week ${node.week}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (node.isHere) Paper.copy(alpha = 0.85f) else InkMuted,
                    )
                }
            }
        }

        // The start of the path, under the lowest node.
        if (showStartLabel) Text(
            text = "Your journey begins",
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
        )
    }
}

/** A compact slice of the path for the Me tab: the last milestone passed,
 * the "you are here" node, and the next one ahead — the dashboard keeps its
 * cards, the path keeps its presence. Tapping anything opens the full path. */
@Composable
fun JourneyPathPreview(
    milestones: List<Milestone>,
    currentWeek: Int?,
    sizeEmoji: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentWeek == null || milestones.isEmpty()) return
    val prev = milestones.lastOrNull { it.week <= currentWeek }
    val next = milestones.firstOrNull { it.week > currentWeek }
    val slice = listOfNotNull(prev, next).filter { it.week != currentWeek } +
        listOfNotNull(milestones.firstOrNull { it.week == currentWeek })
    JourneyPathTimeline(
        milestones = slice.distinct(),
        currentWeek = currentWeek,
        shownWeek = null,
        sizeEmoji = sizeEmoji,
        onSelect = { onOpen() },
        modifier = modifier,
        showStartLabel = false,
    )
}
