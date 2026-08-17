package com.aira.companion.ui.components

import com.aira.companion.data.CareData

/**
 * "1 of 3 complete" — the line the reference puts on Today's care card.
 *
 * Pure and tested because the denominator is a promise. A count that includes
 * things you cannot finish today makes the card permanently incomplete, and a
 * wellness app that always shows unfinished work is doing the opposite of what
 * this one is for.
 */
data class CareProgress(val done: Int, val total: Int) {
    val isEmpty: Boolean get() = total == 0
    val allDone: Boolean get() = total > 0 && done == total
}

/**
 * What can actually be completed today: reminders and medicines.
 *
 * Appointments are deliberately excluded. They are events you attend, not tasks
 * you tick, and counting them would leave "2 of 3" on screen all day with no way
 * to reach 3 — the card would be reporting failure at something the user had not
 * been asked to do.
 *
 * Medicines count from the full list rather than [CareData.medicinesDue], which
 * holds only the outstanding ones: taking the last dose would otherwise shrink
 * the denominator instead of advancing the numerator, so the card would read
 * "0 of 0" at the exact moment the user finished everything.
 */
fun careProgress(care: CareData?): CareProgress {
    if (care == null) return CareProgress(0, 0)
    val reminders = care.reminders
    val medicines = care.medicines
    val done = reminders.count { it.done } + medicines.count { it.takenToday }
    return CareProgress(done = done, total = reminders.size + medicines.size)
}
