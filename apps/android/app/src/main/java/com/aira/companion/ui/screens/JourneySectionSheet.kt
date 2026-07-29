package com.aira.companion.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.model.JourneySection
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory

/**
 * Reading one section of the Journey content.
 *
 * This exists because the three Journey cards had no destination of their own.
 * Their titles came from the server's journey sections — "Your body", "Your
 * baby", "Your next visit" — while their taps were wired to a fixed, unrelated
 * list of tools: "Your body" opened the care plan, and "Your baby" opened the
 * companion/avatar settings. Someone tapping "Your baby — growth explained
 * without overload" landed in voice preferences.
 *
 * A card that shows content now opens that content. Nothing is invented here:
 * the sheet renders exactly the title and text the server sent for that
 * section, so it can never describe a week the user isn't in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneySectionSheet(
    section: JourneySection,
    stageLabel: String,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ivory,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            SectionLabel(stageLabel)
            Spacer(Modifier.height(8.dp))
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = section.text,
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                // The one line that has to be here. This is educational content
                // about a body, and the app must not be mistaken for the person
                // who knows that body.
                text = "General guidance for where you are — not a diagnosis, and " +
                    "not specific to you. Anything that worries you is worth taking " +
                    "to your care team.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
