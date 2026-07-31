package com.aira.companion.ui.screens

import com.aira.companion.ui.theme.Amber
import androidx.compose.material.icons.outlined.Call
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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.JourneyData
import com.aira.companion.model.JourneySection
import com.aira.companion.model.journeyLabel
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.theme.AmberMist
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

@Composable
fun JourneyScreen(
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
    journey: JourneyData? = null,
    onOpenSection: (JourneySection) -> Unit = {},
    /** Opens the video library, which used to be its own tab. */
    onOpenLearn: () -> Unit = {},
) {
    // Journey-aware fields, falling back to the static demo copy when offline.
    val weeks = journey?.weeks
    val stageWord = if (weeks != null) "Week $weeks" else journeyLabel(journey?.journey)
    val heroTitle = journey?.thisWeek?.ifBlank { null } ?: "The size of an ear of corn"
    val heroBody = journey?.body?.ifBlank { null }
        ?: "Growth is steady. Rest and hydration remain useful priorities."
    val sections = journey?.sections?.takeIf { it.isNotEmpty() }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
    ) {
        Text(
            text = "Your journey",
            style = MaterialTheme.typography.headlineLarge,
            color = Ink,
        )
        Text(
            text = "$stageWord · A quieter view of what matters now.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        Spacer(modifier = Modifier.height(22.dp))

        // A pregnancy is a progression, so it is drawn as one: where you are,
        // what is behind, what is coming. See JourneyPath for what was
        // deliberately left out of the pattern this borrows from — no padlocks
        // on weeks you have not reached, no ticks on weeks that merely passed,
        // no streak.
        //
        // Only when there is a week to place someone on. Postpartum and trying
        // to conceive are not a countdown, and stretching a pregnancy path over
        // them with the labels changed would say something untrue about both —
        // they keep the card, which states where they are without implying a
        // direction of travel.
        if (weeks != null) {
            JourneyPath(
                currentWeek = weeks,
                thisWeekTitle = heroTitle,
                thisWeekBody = heroBody,
            )

            // What is worth a call rather than a wait, for this week.
            //
            // Under the path rather than on Today: this is reference material
            // you should be able to find when you are worried, not another card
            // competing for attention on a calm morning. The signals it names
            // are the ones safety.RED_PHRASES already ends a chat turn on — the
            // difference is that this says them before you need them.
            journey?.callTip?.let { tip ->
                Spacer(modifier = Modifier.height(18.dp))
                AiraCard(containerColor = AmberMist) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = tip.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tip.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // Says which it is. Presenting seed copy as clinician-checked
                    // would be the largest overclaim in the product, on the one
                    // screen where being trusted wrongly costs the most.
                    Text(
                        text = if (tip.reviewed) {
                            "Reviewed by your care provider's team."
                        } else {
                            "General guidance, not reviewed by a clinician — " +
                                "your care team's advice comes first."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                    )
                }
            }
        } else {
            AiraCard(containerColor = LilacMist) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(58.dp),
                        color = Plum,
                        contentColor = Paper,
                        shape = CircleShape,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = journeyLabel(journey?.journey).take(1),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel("Where you are")
                        Text(
                            text = heroTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Text(
                            text = heroBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
        }

        // Watching, next to reading.
        //
        // Learn was a sixth tab doing the same job as this screen — "understand
        // where you are", one in words and one in video, both anchored to the
        // same week. Splitting that across two tabs made the reader choose a
        // medium before choosing a subject. It is a full screen still, one tap
        // from here, so nothing upstream built has been reshaped to fit.
        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Watch")
        Spacer(modifier = Modifier.height(9.dp))
        JourneyCard(
            icon = Icons.Outlined.PlayCircle,
            title = "Short guided videos",
            body = "Picked for where you are, and saveable for later.",
            color = SageMist,
            iconColor = SageDeep,
            onClick = onOpenLearn,
        )

        // The section cards.
        //
        // These used to be three fixed cards whose titles came from the server
        // and whose taps went to a hardcoded, unrelated list of tools: "Your
        // body" opened the care plan and "Your baby" opened the companion and
        // avatar settings. The title said one thing and the tap did another.
        //
        // Now the list is the server's sections, each card opens the section it
        // is showing, and a journey with no sections renders nothing rather
        // than inventing three.
        if (!sections.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel("Read about")
            Spacer(modifier = Modifier.height(9.dp))

            val palettes = sectionPalette()
            sections.forEachIndexed { index, section ->
                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                val palette = palettes[index % palettes.size]
                JourneyCard(
                    icon = palette.icon,
                    title = section.title,
                    body = section.text,
                    color = palette.background,
                    iconColor = palette.tint,
                    onClick = { onOpenSection(section) },
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Plum,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    // Was "Next milestone · Glucose screening" — a pregnancy test
                    // announced to postpartum and trying-to-conceive users alike.
                    // Nothing here knows the user's actual schedule, so it must
                    // not name one.
                    text = "Preparing for your next visit",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "Aira can help you gather what's worth asking and what you've " +
                        "noticed since last time. Your care team decides what testing " +
                        "is right for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.height(15.dp))
            PrimaryButton(
                // Named after what opens.
                //
                // This said "Ask Aira about this", which is the phrase the web
                // client uses for a button that opens the conversation — and
                // there it does. Here it opens the appointment sheet, so the
                // one phrase meant two different things depending on which
                // client you were holding, and on this one it promised a chat
                // and produced a form.
                label = "Prepare for a visit",
                onClick = { onOpenTool(AiraTool.Appointment) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = Icons.Outlined.ArrowForward,
            )
        }
    }
}

/** Colour and icon per section slot, so the cards stay visually distinct
 *  without any of them claiming to be about a particular subject. */
private data class SectionPalette(
    val icon: ImageVector,
    val background: Color,
    val tint: Color,
)

/** Composable because the colours now depend on the active theme; the icons
 *  are the only fixed part. */
@Composable
private fun sectionPalette(): List<SectionPalette> = listOf(
    SectionPalette(Icons.Outlined.PersonOutline, SageMist, SageDeep),
    SectionPalette(Icons.Outlined.FavoriteBorder, LilacMist, Plum),
    SectionPalette(Icons.Outlined.Checklist, AmberMist, Plum),
)

@Composable
private fun JourneyCard(
    icon: ImageVector,
    title: String,
    body: String,
    color: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Paper,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(color, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
            Spacer(modifier = Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            Icon(
                imageVector = Icons.Outlined.ArrowForward,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
