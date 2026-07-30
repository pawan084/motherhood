package com.aira.companion.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The day something happened, written the way someone would say it.
 *
 * Shared by the chat and the care timeline so the two never disagree about what
 * "yesterday" means — they are read one after another, and a record that says
 * Tuesday next to a conversation that says yesterday about the same afternoon
 * is a record you stop trusting.
 *
 * Absolute past two days rather than "3 days ago". The useful question about a
 * symptom logged at 3am is which night it was, and relative phrasing makes that
 * arithmetic the reader's problem.
 */
fun dayLabel(
    epochSeconds: Double,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = Instant.ofEpochMilli((epochSeconds * 1000).toLong()).atZone(zone).toLocalDate()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        // Within the year, the year is noise. Across it, leaving it off would
        // put January's entry and last January's under the same heading.
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
    }
}

/** The day key two entries are compared on, so grouping and the label can never
 *  disagree — one derives from the other. */
fun dayOf(
    epochSeconds: Double,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDate = Instant.ofEpochMilli((epochSeconds * 1000).toLong()).atZone(zone).toLocalDate()
