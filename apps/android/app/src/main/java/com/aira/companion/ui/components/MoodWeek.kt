package com.aira.companion.ui.components

import com.aira.companion.data.CareItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The last seven days of mood, from the care timeline.
 *
 * Check-ins arrive as timeline rows whose title is the feeling and whose
 * `created` is a unix second. Turning that into a week is small but fiddly
 * enough to be worth doing in one tested place rather than inside a composable:
 * the timezone, the "which day is this" rounding and the "two check-ins in one
 * day" case are all easy to get subtly wrong, and wrong here means showing
 * somebody a week of their own feelings that did not happen.
 *
 * Returned oldest-first with exactly [days] entries, null where nothing was
 * logged. A gap stays a gap — compacting it would show an unbroken week to
 * someone who logged twice.
 */
object MoodWeek {

    /** Timeline rows that are check-ins carrying a mood we recognise. */
    private fun CareItem.moodKey(): String? {
        if (!kind.equals("checkin", ignoreCase = true)) return null
        val key = title.trim().lowercase()
        return key.takeIf { candidate -> moodStyles.any { it.key == candidate } }
    }

    fun lastDays(
        timeline: List<CareItem>,
        today: LocalDate,
        days: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String?> {
        if (days <= 0) return emptyList()
        val start = today.minusDays((days - 1).toLong())

        // Newest-wins within a day. The timeline arrives newest-first, so the
        // first row seen for a date is the latest check-in that day — which is
        // the one that should show, because it is the person's latest word on
        // how they feel.
        val byDate = HashMap<LocalDate, String>()
        timeline.forEach { item ->
            val mood = item.moodKey() ?: return@forEach
            val created = item.created ?: return@forEach
            val date = Instant.ofEpochSecond(created.toLong()).atZone(zone).toLocalDate()
            if (date < start || date > today) return@forEach
            byDate.putIfAbsent(date, mood)
        }

        return (0 until days).map { offset -> byDate[start.plusDays(offset.toLong())] }
    }

    /** Today's mood, or null if nothing has been logged since midnight. */
    fun today(
        timeline: List<CareItem>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? = lastDays(timeline, today, days = 1, zone = zone).firstOrNull()

    /**
     * Weekday initials aligned to [today], oldest-first, so the strip's labels
     * match the dots above them. Hardcoding "M T W T F S S" would only be right
     * on a Sunday.
     */
    fun dayInitials(today: LocalDate, days: Int = 7): List<String> =
        (0 until days).map { offset ->
            today.minusDays((days - 1 - offset).toLong())
                .dayOfWeek
                .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
        }
}
