package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.PlumSoft

/**
 * Where you are in a pregnancy, and what is coming.
 *
 * Built from a learning-app path the client liked — the sense of place, the
 * "you are here", the peek at what is next. Three things from that pattern are
 * deliberately absent, because they mean something different here:
 *
 *  - **No padlock.** In a lesson app, locked content is earned. A week you have
 *    not reached is not an achievement, and someone at 24 weeks may need to read
 *    what week 30 says tonight. Everything ahead is open; it is labelled
 *    "Coming up" rather than gated.
 *
 *  - **No completion ticks.** You do not complete week 20; it passes. A ticked
 *    path marching toward a baby is a cruel object for anyone whose pregnancy
 *    ends, and this app already refuses to assert a week past 42 rather than
 *    count into fiction. Weeks behind you read as behind you, not as won.
 *
 *  - **No streak, no points.** A streak counter punishes someone for not opening
 *    the app on precisely the days they were least able to.
 *
 * The milestones are calendar structure — when the trimesters begin, when full
 * term starts — not guidance. Nothing here tells anyone what to do; the week's
 * actual content still comes from the server, and the sections below still open
 * the sheet they always did.
 *
 * Only rendered for a journey measured in weeks. Postpartum and trying to
 * conceive are not a countdown, and stretching a pregnancy path over them with
 * the labels changed would say something untrue about both.
 */

/** A point on the path. `week` is when it begins. */
data class JourneyMilestone(
    val week: Int,
    val title: String,
    val detail: String,
)

/** Pregnancy's calendar shape. Definitional, not advice. */
internal fun pregnancyMilestones(): List<JourneyMilestone> = listOf(
    JourneyMilestone(1, "First trimester", "Weeks 1 to 13."),
    JourneyMilestone(14, "Second trimester", "Weeks 14 to 27."),
    JourneyMilestone(28, "Third trimester", "Week 28 onward."),
    JourneyMilestone(37, "Full term begins", "From week 37 a birth is no longer early."),
    JourneyMilestone(40, "Your due date", "Weeks 40 and after. Many arrive either side of it."),
)

internal enum class PathPosition { BEHIND, HERE, AHEAD }

/**
 * Which milestones to draw around the current week.
 *
 * Everything behind is kept — a pregnancy is short enough that the whole path
 * fits, and seeing the road behind you is most of the point of a path.
 */
internal fun positionOf(milestone: JourneyMilestone, currentWeek: Int,
                        next: JourneyMilestone?): PathPosition = when {
    next != null && currentWeek >= next.week -> PathPosition.BEHIND
    currentWeek >= milestone.week -> PathPosition.HERE
    else -> PathPosition.AHEAD
}

@Composable
fun JourneyPath(
    currentWeek: Int,
    modifier: Modifier = Modifier,
    thisWeekTitle: String? = null,
    thisWeekBody: String? = null,
) {
    val milestones = pregnancyMilestones()
    Column(modifier = modifier.fillMaxWidth()) {
        milestones.forEachIndexed { index, milestone ->
            val position = positionOf(milestone, currentWeek, milestones.getOrNull(index + 1))
            PathRow(
                milestone = milestone,
                position = position,
                currentWeek = currentWeek,
                isLast = index == milestones.lastIndex,
                thisWeekTitle = thisWeekTitle.takeIf { position == PathPosition.HERE },
                thisWeekBody = thisWeekBody.takeIf { position == PathPosition.HERE },
            )
        }
    }
}

@Composable
private fun PathRow(
    milestone: JourneyMilestone,
    position: PathPosition,
    currentWeek: Int,
    isLast: Boolean,
    thisWeekTitle: String?,
    thisWeekBody: String?,
) {
    val here = position == PathPosition.HERE
    // IntrinsicSize.Min so the row reports the height its content needs, and the
    // connector can then fill whatever is left. With a fixed height the spine
    // broke exactly where the current week's card made the row taller — the one
    // row where the line matters most.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // The spine: a node, and the line running on to the next one. Decorative
        // — the row's text carries the meaning, so this is hidden from screen
        // readers rather than announced as an unlabelled shape.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(46.dp).fillMaxHeight().clearAndSetSemantics {},
        ) {
            Surface(
                modifier = Modifier.size(if (here) 44.dp else 26.dp),
                shape = CircleShape,
                color = when (position) {
                    PathPosition.HERE -> Plum
                    PathPosition.BEHIND -> LilacMist
                    PathPosition.AHEAD -> Paper
                },
                contentColor = if (here) Paper else PlumSoft,
                border = if (position == PathPosition.AHEAD) {
                    androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft)
                } else {
                    null
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (here) {
                        Text(
                            text = currentWeek.toString(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        // Fills the row rather than guessing at it.
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(
                            color = if (position == PathPosition.AHEAD) OutlineSoft else LilacMist,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 22.dp)) {
            Text(
                text = when (position) {
                    // Not "done". A week that has passed has passed; it was not
                    // won, and nothing here should read like a prize.
                    PathPosition.BEHIND -> "BEHIND YOU"
                    PathPosition.HERE -> "YOU ARE HERE"
                    PathPosition.AHEAD -> "COMING UP"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (here) Plum else InkMuted,
            )
            Text(
                text = milestone.title,
                style = if (here) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = if (position == PathPosition.AHEAD) InkMuted else Ink,
            )
            Text(
                text = milestone.detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            // The week's real content, from the server, on the node it belongs
            // to — rather than in a hero card that repeats the week number the
            // path is already showing.
            if (here && (thisWeekTitle != null || thisWeekBody != null)) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = LilacMist,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        thisWeekTitle?.let {
                            Text(it, style = MaterialTheme.typography.titleSmall, color = Ink)
                        }
                        thisWeekBody?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
