package com.aira.companion.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aira.companion.data.CareItem
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.BackButton
import com.aira.companion.ui.components.EmptyState
import com.aira.companion.ui.components.Eyebrow
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.StatusNote
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Care Vault: scans, reports and prescriptions in one place.
 *
 * The privacy line here is deliberately NOT the reference's. The reference says
 * "Stored on your device"; in this app documents are uploaded to the account
 * (POST /v1/care/documents) and fetched back from it, so that sentence would be
 * a false promise about medical records — the worst possible thing to be wrong
 * about on the screen whose whole job is being trusted with them. What is said
 * instead is what is true and checkable: they live in the account, only that
 * account can open them, and health data is permanently barred from advertising
 * by the consent policy.
 */
@Composable
fun CareVaultScreen(
    documents: List<CareItem>,
    onBack: () -> Unit,
    onOpen: (CareItem) -> Unit,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val today = remember { LocalDate.now() }
    // "Last 3 months" rather than the reference's "This trimester". A trimester
    // is a fact about a pregnancy, and these dates are when a file was added —
    // which is not the same thing, and is wrong for anyone postpartum or not
    // pregnant at all. Roughly the same span, honestly labelled.
    val (recent, earlier) = remember(documents) {
        val cutoff = today.minusDays(90)
        documents.partition { item ->
            val date = item.created?.let {
                Instant.ofEpochSecond(it.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            // Undated files sort as recent: a file with no timestamp is more
            // likely just-added than years old, and burying it under "Earlier"
            // hides the thing somebody is most likely looking for.
            date == null || date >= cutoff
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 108.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Care vault",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = SageMist,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = SageDeep,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Private to your account",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = "Stored in your Aira account, not on this phone. " +
                            "Only you can open them, and health data is never used " +
                            "for advertising.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }

        if (failed) {
            Spacer(modifier = Modifier.height(14.dp))
            StatusNote(
                text = "Couldn't reach your vault just now, so this list may be " +
                    "incomplete. Nothing has been deleted.",
                icon = Icons.Outlined.FolderOff,
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(
                    label = "Try again",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (documents.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            AiraCard {
                EmptyState(
                    icon = Icons.Outlined.Description,
                    title = "Nothing in the vault yet",
                    body = "Scans, blood work and prescriptions kept here are easy to " +
                        "find at a visit, instead of scrolling back through a chat.",
                )
            }
        } else {
            DocumentGroup(
                title = "Last 3 months",
                count = recent.size,
                items = recent,
                onOpen = onOpen,
            )
            DocumentGroup(
                title = "Earlier",
                count = earlier.size,
                items = earlier,
                onOpen = onOpen,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        PrimaryButton(
            label = "Upload document",
            onClick = onUpload,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = null,
        )
    }
}

@Composable
private fun DocumentGroup(
    title: String,
    count: Int,
    items: List<CareItem>,
    onOpen: (CareItem) -> Unit,
) {
    if (items.isEmpty()) return
    Spacer(modifier = Modifier.height(16.dp))
    Eyebrow(text = "$title ($count)")
    Spacer(modifier = Modifier.height(8.dp))
    AiraCard {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(OutlineSoft),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            DocumentRow(item = item, onOpen = onOpen)
        }
    }
}

@Composable
private fun DocumentRow(
    item: CareItem,
    onOpen: (CareItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(item) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconFor(item.contentType),
            contentDescription = null,
            tint = Plum,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
            )
            // Type, size and date — the reference's three, all of them real.
            // The size was on the wire from the start and parsed by nothing, so
            // the vault could not say how big a file was; it is read now rather
            // than printed as a plausible-looking number.
            val details = listOfNotNull(
                item.contentType?.let { typeLabel(it) },
                item.sizeBytes?.let { fileSize(it) },
                item.created?.let { added(it) },
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
    }
}

private fun iconFor(contentType: String?): ImageVector = when {
    contentType == null -> Icons.Outlined.Description
    contentType.contains("pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
    contentType.startsWith("image", ignoreCase = true) -> Icons.Outlined.Image
    else -> Icons.Outlined.Description
}

private fun typeLabel(contentType: String): String = when {
    contentType.contains("pdf", ignoreCase = true) -> "PDF"
    contentType.startsWith("image", ignoreCase = true) ->
        contentType.substringAfter('/').uppercase().take(4)
    else -> contentType.substringAfter('/').uppercase().take(8)
}

/** "640 KB" / "2.1 MB", in the units a person reads rather than bytes. */
internal fun fileSize(bytes: Long): String = when {
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "${bytes / 1_000L} KB"
    else -> "$bytes B"
}

private fun added(epochSeconds: Double): String =
    Instant.ofEpochSecond(epochSeconds.toLong())
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM"))
