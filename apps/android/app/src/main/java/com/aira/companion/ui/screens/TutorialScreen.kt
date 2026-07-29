package com.aira.companion.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist
import kotlinx.coroutines.launch

/**
 * A three-card introduction, shown once per install and skippable throughout.
 *
 * Deliberately short. The app already personalises itself through the chat-led
 * onboarding that follows, so this exists to answer only what someone needs before
 * deciding whether to continue: what Aira is, what it does with their data, and
 * what it is not.
 *
 * Every claim here is one the build actually keeps. There is no card promising
 * voice conversation or a talking avatar — neither exists — because an intro that
 * oversells is the first thing a new user would catch us on.
 */
private data class TutorialCard(
    val eyebrow: String,
    val title: String,
    val body: String,
    val icon: ImageVector?,
    val accent: androidx.compose.ui.graphics.Color,
)

private val CARDS = listOf(
    TutorialCard(
        eyebrow = "WHAT AIRA IS",
        title = "The care between care.",
        body = "Appointments are short and far apart. Aira is for everything in " +
            "between — questions at odd hours, reminders that matter, and one " +
            "clear next step instead of a feed to keep up with.",
        icon = null, // the brand orb stands in, so the first card leads with identity
        accent = LilacMist,
    ),
    TutorialCard(
        eyebrow = "PRIVATE BY DESIGN",
        title = "Your context stays yours.",
        body = "Aira remembers only what helps, you can read or delete any of it, " +
            "and you can export everything at any time. Your health data is never " +
            "used for advertising — that one isn't a setting you have to find.",
        icon = Icons.Outlined.Lock,
        accent = LilacMist,
    ),
    TutorialCard(
        eyebrow = "NOT A DOCTOR",
        title = "Wellness support, not diagnosis.",
        body = "Aira doesn't diagnose, prescribe, or replace your care team. Every " +
            "message is screened first, and anything urgent goes straight to your " +
            "care team rather than to another AI answer.",
        icon = Icons.Outlined.HealthAndSafety,
        accent = SageMist,
    ),
)

@Composable
fun TutorialScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { CARDS.size })
    val scope = rememberCoroutineScope()
    val onLast = pager.currentPage == CARDS.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .systemBarsPadding(),
    ) {
        // Skip stays available on every card, including the last. Someone who has
        // decided they don't want this shouldn't have to page to the end to leave.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinish) {
                Text("Skip", color = InkMuted, style = MaterialTheme.typography.labelLarge)
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
        ) { page ->
            TutorialPage(CARDS[page])
        }

        PagerDots(
            count = CARDS.size,
            current = pager.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp),
        )

        PrimaryButton(
            label = if (onLast) "Get started" else "Next",
            onClick = {
                if (onLast) {
                    onFinish()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        )

        Text(
            text = "You can use Aira without an account.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TutorialPage(card: TutorialCard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(if (card.icon == null) 132.dp else 96.dp),
            color = card.accent,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (card.icon == null) {
                    BrandOrb()
                } else {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        tint = if (card.accent == SageMist) SageDeep else Plum,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(34.dp))
        Text(
            text = card.eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = Plum,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = card.body,
            style = MaterialTheme.typography.bodyLarge,
            color = InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            val active = i == current
            // The active dot widens rather than only changing colour, so position
            // is readable without relying on colour alone.
            val width by animateDpAsState(if (active) 22.dp else 7.dp, label = "dot width")
            val color by animateColorAsState(if (active) Plum else Lilac, label = "dot color")
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(7.dp)
                    .width(width)
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
    }
}
