package com.aira.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ChevronRight
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
import com.aira.companion.ui.components.MetricPill
import com.aira.companion.ui.components.EmptyState
import com.aira.companion.ui.components.MoodCheckInCard
import com.aira.companion.ui.components.QuietDaysCard
import com.aira.companion.ui.components.TodayCareCard
import com.aira.companion.ui.components.WatchThisWeekCard
import com.aira.companion.ui.components.careProgress
import com.aira.companion.ui.components.MoodWeek
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.WeekHero
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

/**
 * "Good morning" / "Good afternoon" / "Good evening", from the clock.
 *
 * The greeting was fixed at "Good morning" regardless of the hour, which the
 * reference's own mock shows as "Good afternoon" at 15:53. A wellness app that
 * says good morning at midnight is a small thing, but this app is used at 3am
 * more than most and the wrong greeting is the first thing on the page.
 */
private fun greetingForNow(): String =
    when (java.time.LocalTime.now().hour) {
        in 0..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

@Composable
fun TodayScreen(
    onDestination: (MainDestination) -> Unit,
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
    today: TodayData? = null,
    /** Appointments, medicines due and open reminders — the same count the
     *  bell badges, so the two can't disagree. */
    waiting: Int = 0,
    /** Journey folded in: this screen was already headed "Where you are", and
     *  a second tab saying the same thing about the same week was two answers
     *  to one question. */
    journey: com.aira.companion.model.JourneyData? = null,
    onOpenSection: (com.aira.companion.model.JourneySection) -> Unit = {},
    onOpenLearn: () -> Unit = {},
    /** Opens the journey page: the path, the reading and the videos. */
    onOpenJourney: () -> Unit = {},
    /** Check-ins and symptom logs, newest first — the source for the mood week. */
    timeline: List<com.aira.companion.data.CareItem> = emptyList(),
    onLogMood: (String) -> Unit = {},
    /** Reminders and medicines, for the "N of M complete" summary. */
    care: com.aira.companion.data.CareData? = null,
    onOpenCare: () -> Unit = {},
    /** The week's topic, from GET /v1/videos. */
    weekVideo: com.aira.companion.model.VideoTopic? = null,
    /** True once "Not now" has been tapped today — see AppPrefs. */
    quietCardDismissed: Boolean = false,
    onDismissQuietCard: () -> Unit = {},
    onOpenCheckIn: () -> Unit = {},
    onPauseReminders: () -> Unit = {},
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
    // After a loss, two things on this card stop making sense.
    //
    // The ring is a countdown with nothing to count, and rendering it with the
    // journey's first letter produces a large "A" above the word "stage" —
    // furniture where a pregnancy used to be, on the screen where that is
    // hardest to look at.
    //
    // The priorities were chosen during a different stage. "Understand changes"
    // meant pregnancy changes; repeating it back as something this person asked
    // for is the app quoting them out of context. They are hidden rather than
    // deleted — if the journey changes again they are still theirs.
    val afterLoss = today?.journey.equals("loss", ignoreCase = true)
    val priorities = if (afterLoss) emptyList() else today?.priorities.orEmpty()
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
    // The week's detail, when the server has it. The headline above already
    // carries the week's title, so this must not repeat it.
    val heroSubtitle = journey?.body?.ifBlank { null }
        ?: today?.contextLine?.ifBlank { null }
        ?: "What's worth knowing right now"
    val plumRing = Plum

    // "Day 5" in the reference's hero. Derived from the due date or not shown:
    // a pregnancy is dated from 40 weeks before the due date, so the day within
    // the current week is real arithmetic when dueDate is present and pure
    // invention otherwise. The week itself can arrive without one — a reported
    // week carried forward — and in that case the number stands on its own.
    val dayInWeek = remember(today?.dueDate) {
        today?.dueDate?.let { iso ->
            runCatching {
                val due = java.time.LocalDate.parse(iso)
                val conception = due.minusWeeks(40)
                val elapsed = java.time.temporal.ChronoUnit.DAYS
                    .between(conception, java.time.LocalDate.now())
                if (elapsed < 0) null else (elapsed % 7).toInt() + 1
            }.getOrNull()
        }
    }
    // Only a pregnancy has a fixed length to be a fraction of. Every other
    // journey would need an arc that lies in order to have one.
    val heroProgress = weeks
        ?.takeIf { today?.journey.equals("pregnant", ignoreCase = true) }
        ?.let { (it / 40f).coerceIn(0f, 1f) }
    val weekRail = weeks?.let { w -> ((w - 3)..(w + 3)).filter { it in 1..42 } }.orEmpty()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                // The nav is a floating pill now rather than a bar the Scaffold
                // reserves space for, so the last card scrolled under it and
                // could not be scrolled clear. The reference reserves the same
                // room for the same reason.
                .padding(top = 18.dp, bottom = 108.dp),
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

        // The reference's week hero: the week as the largest thing on the page,
        // with the day inside it, a sentence about what it means, and the
        // fraction of the pregnancy elapsed. It replaces the "Where you are"
        // ring card, which drew a circle around a number that was not a
        // proportion of anything until the arc was added underneath it.
        WeekHero(
            greeting = listOfNotNull(
                greetingForNow(),
                name,
            ).joinToString(", "),
            week = if (afterLoss) null else weeks,
            dayInWeek = if (afterLoss) null else dayInWeek,
            subtitle = if (loaded) heroSubtitle else "Not loaded yet.",
            progress = if (afterLoss) null else heroProgress,
            weekRail = if (afterLoss) emptyList() else weekRail,
            onSelectWeek = { onOpenJourney() },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClickLabel = "See where this sits in your pregnancy",
                    onClick = onOpenJourney,
                ),
        )

        // The "6h sleep / Steady mood / None new concern" pills that used to sit
        // here were invented readings — nothing in the app had measured any of
        // them. They come back when check-ins are aggregated server-side; until
        // then the user's own priorities are real and worth showing.
        if (priorities.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
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

        // Someone returning after a lapse sees this before anything else on the
        // page. Three days is the reference's own threshold, and the gap is
        // measured from the timeline rather than assumed — a user who has never
        // checked in gets nothing, because they are new rather than returning.
        val quietDays = remember(timeline) {
            MoodWeek.daysSinceLastCheckIn(timeline, java.time.LocalDate.now())
        }
        if (quietDays != null && quietDays >= 3 && !quietCardDismissed) {
            Spacer(modifier = Modifier.height(18.dp))
            QuietDaysCard(
                onCheckIn = onOpenCheckIn,
                onDismiss = onDismissQuietCard,
                onPauseReminders = onPauseReminders,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        // Today's care sits above the mood row, matching the reference's own
        // note that the card belongs above the fold rather than behind the nav.
        TodayCareCard(
            progress = careProgress(care),
            loaded = care != null,
            onViewAll = onOpenCare,
        )

        Spacer(modifier = Modifier.height(14.dp))
        // The mood check-in, above the fold rather than at the bottom of the
        // page. It is the one thing on Today the user is asked to give rather
        // than read, and it was previously not on this screen at all.
        val moodToday = remember(timeline) { MoodWeek.today(timeline, java.time.LocalDate.now()) }
        val moodWeek = remember(timeline) { MoodWeek.lastDays(timeline, java.time.LocalDate.now()) }
        val moodLabels = remember { MoodWeek.dayInitials(java.time.LocalDate.now()) }
        MoodCheckInCard(
            selectedKey = moodToday,
            recent = moodWeek,
            dayLabels = moodLabels,
            onSelect = onLogMood,
        )

        // The week's topic. Only shown when the server has one — an empty card
        // headed "What to watch this week" is a promise with nothing behind it.
        if (weekVideo != null) {
            Spacer(modifier = Modifier.height(14.dp))
            WatchThisWeekCard(
                categoryLabel = weekVideo.categoryLabel.ifBlank { weekVideo.category },
                title = weekVideo.title,
                description = weekVideo.description,
                // A range, because that is what the catalog stores. Printing a
                // single figure would invent a precision the data does not have.
                duration = weekVideo.maxSeconds
                    .takeIf { it > 0 }
                    ?.let { max ->
                        val min = weekVideo.minSeconds.takeIf { it > 0 }
                        if (min != null && min != max) {
                            "${min / 60}–${max / 60} min"
                        } else {
                            "${max / 60} min"
                        }
                    },
                playable = weekVideo.playable,
                onOpen = onOpenLearn,
                onViewAll = onOpenLearn,
            )
        }

        Spacer(modifier = Modifier.height(26.dp))
        // A day with nothing scheduled and nothing suggested gets its own
        // design rather than an empty "Do this next" heading over a card with no
        // content. The reference calls this out specifically: a first session,
        // or simply a quiet Tuesday, is a real state and not a missing one.
        val nothingToDo = loaded && actionTitle == null && careProgress(care).isEmpty
        if (nothingToDo) {
            AiraCard {
                EmptyState(
                    icon = Icons.Outlined.WbSunny,
                    title = "Nothing scheduled yet today",
                    body = "Your care list fills in each morning. Check back after " +
                        "breakfast, or open Chat if something's on your mind right now.",
                )
                PrimaryButton(
                    label = "Open Chat",
                    onClick = { onDestination(MainDestination.Aira) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@Column
        }

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
        // The path and the reading moved to their own page.
        //
        // Today is the answer to "what now". Where you are in the arc of a
        // pregnancy is a different question — worth asking, not worth asking
        // every time you open the app. It is one tap away from the card that
        // already states the week, which is the natural place to look for it.

        // "Add something" moved off Today.
        //
        // Check in / Reminder / Symptom were three more CTAs on the screen that
        // is supposed to propose one thing — and they duplicate the chat's own
        // chips, which sit next to the composer where somebody is already
        // typing. Care keeps an Add on every section, which is where records
        // are kept and where adding one belongs.

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
                        // Was an unconditional "You're caught up for today.",
                        // which sat directly under a header saying four things
                        // needed attention. Two claims about the same state,
                        // one screen apart, disagreeing.
                        text = when {
                            !loaded -> "Aira can’t reach its backend."
                            waiting > 0 -> "Your care is up to date."
                            else -> "You’re caught up for today."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = when {
                            !loaded -> "This screen can’t tell you what needs attention until it connects."
                            waiting > 0 -> "Everything waiting is listed in Care."
                            else -> "Aira will surface something only when it matters."
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
                        text = "Ask anything",
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

/** A reading or watching entry on Today. Deliberately plainer than the Journey
 *  cards it replaces — this screen already carries a hero and an action card,
 *  and a third weight of card below them made the page read as three competing
 *  headlines rather than one page. */
@Composable
private fun TodayReadRow(title: String, body: String, onClick: () -> Unit) {
    AiraCard(
        modifier = Modifier.clickable(
            role = androidx.compose.ui.semantics.Role.Button,
            onClickLabel = "Open $title",
            onClick = onClick,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
                Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = InkMuted,
            )
        }
    }
}
