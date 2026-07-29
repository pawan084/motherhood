package com.aira.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.MainDestination
import com.aira.companion.model.TodayData
import com.aira.companion.model.journeyLabel
import com.aira.companion.model.toolKeyToTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.GradientHeroSurface
import com.aira.companion.ui.components.MetricPill
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

@Composable
fun TodayScreen(
    onDestination: (MainDestination) -> Unit,
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
    today: TodayData? = null,
    /** Appointments, medicines due and open reminders — the same count the
     *  bell badges, so the two can't disagree. */
    waiting: Int = 0,
) {
    // Every field here comes from /v1/today or is omitted. The fallbacks that
    // used to sit on these lines were caught on a real device with an expired
    // token: with the backend unreachable the screen still rendered "Second
    // trimester" and "Prepare for tomorrow's appointment" in full confidence,
    // so a postpartum user — or anyone simply offline — was told they were
    // mid-pregnancy with a visit booked for the next day. An empty screen is
    // recoverable; a confidently wrong one is not.
    val weeks = today?.weeks
    // No invented name. The fallback used to greet every user as "Maya" — a
    // stranger's name on a private health app.
    val name = today?.name?.ifBlank { null }
    val priorities = today?.priorities.orEmpty()
    // The chip used to read "Week 24" — the same string the app bar shows two
    // rows above it, and the same one the ring below repeats. Today had no date
    // anywhere, so the word "today" was never anchored to one; that is what
    // this line is for now.
    val headerText = remember {
        java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM"))
    }
    val contextLine = today?.contextLine?.ifBlank { null }
    val action = today?.nextAction
    val actionTitle = action?.title?.ifBlank { null }
    val actionDetail = action?.detail?.ifBlank { null }
    val actionMinutes = action?.minutes
    val actionTool = toolKeyToTool(action?.tool) ?: AiraTool.Appointment
    // "Loaded" means the server answered. Until it does, the screen says it is
    // still loading rather than asserting anything about the user's care.
    val loaded = today != null
    val plumRing = Plum
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(34.dp),
                color = SageMist,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = SageDeep,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                // Date and greeting on one line. They were two stacked blocks
                // with a 16dp gap, and together with a headlineLarge headline
                // they pushed the actual next action off a 720x1604 screen —
                // the one thing Today exists to show was below the fold.
                text = listOfNotNull(headerText, name?.let { "Good morning, $it" })
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        // The headline says where you are, not how you're doing.
        //
        // It was "You're on track." — unconditional, so it claimed a state the
        // screen had no way to know, and after that was gated it still spent
        // the largest text on the screen saying nothing the user could act on.
        // The stage line was already computed for the ring below; putting it
        // here means the biggest words on Today are the truest ones.
        Text(
            text = when {
                !loaded -> "Just a moment…"
                contextLine != null -> contextLine
                else -> "Here's your day."
            },
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            // Was "Nothing urgent needs your attention right now" — true, and
            // an answer to a question nobody asked. When there IS something
            // waiting, say what and how much; the count comes from the same
            // data the bell badges.
            text = when {
                !loaded -> "Aira is fetching your care. Nothing here is your data yet."
                waiting > 0 -> "$waiting thing${if (waiting == 1) "" else "s"} " +
                    "${if (waiting == 1) "needs" else "need"} you today."
                else -> "Nothing needs you right now."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        Spacer(modifier = Modifier.height(24.dp))

        GradientHeroSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionLabel("Where you are")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(72.dp)
                                .background(
                                    brush = Brush.radialGradient(listOf(Lilac, Paper)),
                                    shape = CircleShape,
                                )
                                // The ring was a circle with a number inside it —
                                // shaped like a progress indicator, showing no
                                // progress. For a pregnancy it now draws the
                                // fraction of ~40 weeks elapsed, so the shape
                                // means what its shape implies. Nothing is drawn
                                // for other journeys, which have no fixed length
                                // and would need an arc that lies to have one.
                                .drawBehind {
                                    val fraction = weeks?.let { (it / 40f).coerceIn(0f, 1f) }
                                        ?: return@drawBehind
                                    val stroke = 5.dp.toPx()
                                    drawArc(
                                        color = plumRing,
                                        startAngle = -90f,
                                        sweepAngle = 360f * fraction,
                                        useCenter = false,
                                        topLeft = Offset(stroke / 2, stroke / 2),
                                        size = Size(
                                            this.size.width - stroke,
                                            this.size.height - stroke,
                                        ),
                                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                                    )
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = weeks?.toString() ?: journeyLabel(today?.journey).take(1),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Plum,
                            )
                            Text(
                                text = if (weeks != null) "weeks" else "stage",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // The stage line moved up to the headline, so this card
                        // stops repeating it. It says where the tap goes
                        // instead, which is the one thing the row wasn't saying.
                        Text(
                            text = "Your journey",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                        )
                        Text(
                            text = if (loaded) {
                                "What's worth knowing right now"
                            } else {
                                "Not loaded yet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Open Journey",
                        tint = Plum,
                    )
                }
                // The "6h sleep / Steady mood / None new concern" pills that used
                // to sit here were invented readings — nothing in the app had
                // measured any of them. They come back when check-ins are
                // aggregated server-side; until then the user's own priorities
                // are real and worth showing.
                if (priorities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    // One heading for the group, rather than the word "focus"
                    // stamped under every chip. Repeating a label under each
                    // item says nothing the group heading didn't, and "focus"
                    // on its own reads as an instruction rather than a caption.
                    Text(
                        text = "You asked Aira to focus on",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        priorities.take(3).forEach { priority ->
                            MetricPill(
                                value = priority,
                                label = "",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
        SectionLabel("Do this next")
        Spacer(modifier = Modifier.height(10.dp))

        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
                            .background(Lilac, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Plum,
                    )
                }
                Spacer(modifier = Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // The eyebrow said "Next best action" directly under a
                    // heading that already said "Do this next", above a title
                    // that says what the action is — three labels before any
                    // content. Only the duration survives, because that is
                    // information rather than a description of the card.
                    if (actionMinutes != null) {
                        Text(
                            text = "About $actionMinutes min",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(15.dp))
            Text(
                text = actionTitle ?: "Nothing to suggest yet",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = actionDetail ?: if (loaded) {
                    "Aira will surface one step here when it has something useful."
                } else {
                    "Aira hasn't been able to load your care."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.height(17.dp))
            // Offering "Start" for an action the server never sent would open a
            // tool chosen by a fallback, not by the user's actual context.
            if (actionTitle != null) {
                PrimaryButton(
                    label = "Start with Aira",
                    onClick = { onOpenTool(actionTool) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = Icons.Outlined.ArrowForward,
                )
            }
        }

        // Quick add.
        //
        // Recording something took four taps from here — Aira, the tool tray,
        // the tool, then save — for the three things people add most often, and
        // the ones they add at the worst moments. A check-in at 3am should not
        // require finding a menu.
        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Add something")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAdd(
                icon = Icons.Outlined.FavoriteBorder,
                label = "Check in",
                onClick = { onOpenTool(AiraTool.CheckIn) },
                modifier = Modifier.weight(1f),
            )
            QuickAdd(
                icon = Icons.Outlined.AccessTime,
                label = "Reminder",
                onClick = { onOpenTool(AiraTool.Reminder) },
                modifier = Modifier.weight(1f),
            )
            QuickAdd(
                icon = Icons.Outlined.TrackChanges,
                label = "Symptom",
                onClick = { onOpenTool(AiraTool.Symptom) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SageMist,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SageDeep,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    // "Caught up" is a claim about the user's care, so it needs
                    // the server to have actually said so.
                    Text(
                        text = if (loaded) {
                            "You’re caught up for today."
                        } else {
                            "Aira can’t reach its backend."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = if (loaded) {
                            "Aira will surface something only when it matters."
                        } else {
                            "This screen can’t tell you what needs attention until it connects."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Plum,
            contentColor = Paper,
            shape = RoundedCornerShape(22.dp),
            onClick = { onDestination(MainDestination.Aira) },
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continue with Aira",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Ask anything, by text or voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.copy(alpha = 0.72f),
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                )
            }
        }
    }
}


/** One quick-add tile: an icon, a word, and the sheet it opens. */
@Composable
private fun QuickAdd(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Paper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineSoft),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Plum)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Ink,
            )
        }
    }
}
