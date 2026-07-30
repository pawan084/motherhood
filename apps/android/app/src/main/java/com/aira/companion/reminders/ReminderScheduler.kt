package com.aira.companion.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aira.companion.MainActivity
import com.aira.companion.R
import com.aira.companion.data.CareItem
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Reminders that actually arrive.
 *
 * The app has called these "reminders" on four screens — the tool that creates
 * them, the Care list, the care plan count, the notification sheet — while
 * delivering nothing at all. Nothing was ever scheduled with the system, so a
 * reminder to take iron at 8pm was a note you had to remember to go and read.
 * For medication in particular that is the failure mode the feature exists to
 * prevent.
 *
 * Deliberately simple: one WorkManager job per reminder, keyed by the item id,
 * that posts a notification and schedules the next occurrence. No exact alarms
 * — those need SCHEDULE_EXACT_ALARM on Android 12+, which is a heavyweight
 * permission for something that does not need to be to-the-second. WorkManager
 * may fire a few minutes late under Doze; a medicine reminder at 20:03 instead
 * of 20:00 is fine, and a reminder that requires a special permission the user
 * may decline is not.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "aira_reminders"
    // Not private: the snooze action re-enqueues under the same unique name,
    // so a snoozed reminder replaces its own pending work instead of stacking.
    internal const val WORK_PREFIX = "aira_reminder_"

    /** Whether this build can post notifications at all, per the OS. */
    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * Whether the OS is likely to hold reminders back.
     *
     * Permission to post a notification is not the same as being allowed to run
     * at the moment one is due. WorkManager is deferrable by design: under Doze
     * it waits for a maintenance window, and several manufacturers' battery
     * managers go further and stop background work outright unless the app is
     * exempted by hand. On those phones "Aira will notify you" is a promise the
     * app cannot keep, and the person finds out by missing a dose.
     *
     * This does not ask for the exemption. Requesting it directly is a heavier
     * permission than a reminder needs; the honest move is to say what may
     * happen and offer the settings screen where they can decide.
     */
    fun remindersMayBeDelayed(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** The system screen listing which apps are exempt, so the user chooses
     *  there rather than being asked for a permission in a dialog. */
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Medicines, appointments and anything else you asked Aira to remember."
            // No badge on the launcher icon: a maternal-health app announcing
            // an unread count on someone's home screen is a disclosure to
            // whoever is holding the phone.
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Reschedule every reminder from the server's list.
     *
     * Cancels first, so a reminder deleted or renamed on another device stops
     * arriving on this one. Called whenever Care loads, which makes the app's
     * own list the single source of truth rather than something accumulated
     * locally over time.
     */
    fun syncAll(
        context: Context,
        reminders: List<CareItem>,
        appointments: List<CareItem> = emptyList(),
    ) {
        ensureChannel(context)
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(WORK_PREFIX)
        if (!canNotify(context)) return
        reminders.filterNot { it.done }.forEach { schedule(context, it) }
        appointments.forEach { scheduleAppointment(context, it) }
    }

    /**
     * A nudge the evening before a visit.
     *
     * Appointments are the one thing in this app with a fixed date and real
     * consequences for missing it — a scan is rebooked weeks out, not
     * tomorrow. The evening before rather than the morning of, because what a
     * reminder actually buys you is the time to arrange the lift, the childcare
     * or the afternoon off, and at 8am on the day that is all too late.
     *
     * Only for appointments that carry a real date. One notification, not a
     * repeating one: unlike a daily medicine, a visit happens once.
     */
    private fun scheduleAppointment(context: Context, item: CareItem) {
        val at = item.at ?: return
        val visit = java.time.Instant.ofEpochSecond(at.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val remindAt = LocalDateTime.of(visit.minusDays(1), APPOINTMENT_REMINDER_TIME)
        val delay = Duration.between(LocalDateTime.now(), remindAt)
        // Nothing for a visit booked for tomorrow or already past: firing
        // immediately for something the user just typed is noise, not a
        // reminder.
        if (delay.isNegative) return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .addTag(WORK_PREFIX)
            .setInputData(
                Data.Builder()
                    .putString(KEY_ID, item.id)
                    .putString(KEY_TITLE, "Tomorrow: ${item.title}")
                    // No time in this line, so the worker's "book the next one"
                    // finds nothing to parse and the notification stays a
                    // one-off — which is what a single visit needs.
                    .putString(KEY_DETAIL, item.subtitle)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_PREFIX + item.id, ExistingWorkPolicy.REPLACE, request)
    }

    /** 18:00 the day before. Late enough to be the evening, early enough that
     *  it isn't competing with getting a household to bed. */
    internal val APPOINTMENT_REMINDER_TIME: LocalTime = LocalTime.of(18, 0)

    /** When the nudge for a visit on [visitEpochSeconds] would fire, or null if
     *  that moment has already passed. Exposed for tests. */
    fun appointmentReminderAt(
        visitEpochSeconds: Long,
        now: LocalDateTime = LocalDateTime.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): LocalDateTime? {
        val visit = java.time.Instant.ofEpochSecond(visitEpochSeconds).atZone(zone).toLocalDate()
        val remindAt = LocalDateTime.of(visit.minusDays(1), APPOINTMENT_REMINDER_TIME)
        return if (remindAt.isAfter(now)) remindAt else null
    }

    private fun schedule(context: Context, item: CareItem) {
        val at = nextOccurrence(item.subtitle) ?: return
        val delay = Duration.between(LocalDateTime.now(), at)
        if (delay.isNegative) return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .addTag(WORK_PREFIX)
            .setInputData(
                Data.Builder()
                    .putString(KEY_ID, item.id)
                    .putString(KEY_TITLE, item.title)
                    .putString(KEY_DETAIL, item.subtitle)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_PREFIX + item.id, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * When this reminder is next due, from the "8:00 PM · Daily" line the care
     * item already carries. Returns null when no time can be read — a reminder
     * with no time is a note, and scheduling it for an invented hour would be
     * worse than not scheduling it.
     */
    fun nextOccurrence(
        subtitle: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): LocalDateTime? {
        val time = parseTime(subtitle) ?: return null
        val todayAt = LocalDateTime.of(now.toLocalDate(), time)
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    private fun parseTime(subtitle: String): LocalTime? {
        val match = Regex("""(\d{1,2}):(\d{2})\s*([AaPp][Mm])?""").find(subtitle) ?: return null
        val (h, m, meridiem) = match.destructured
        var hour = h.toIntOrNull() ?: return null
        val minute = m.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        when (meridiem.lowercase(Locale.ROOT)) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        return LocalTime.of(hour, minute)
    }

    internal const val KEY_ID = "id"

    /** Set on the tap intent so MainActivity knows to open Care rather than
     *  dropping the user on Today to go looking. */
    const val EXTRA_OPEN_CARE = "aira.open_care"

    /** Everything Aira posts belongs to one group, so a day with a morning
     *  tablet, an evening tablet and tomorrow's scan is one entry in the shade
     *  rather than three competing ones. */
    internal const val GROUP_KEY = "aira_reminders"

    /** Fixed id for the summary, so re-posting replaces it instead of stacking
     *  a second summary on top of the first. */
    internal const val SUMMARY_ID = 1_000_001
    internal const val KEY_TITLE = "title"
    internal const val KEY_DETAIL = "detail"
}

/** Posts one reminder, then books the next one. */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getString(ReminderScheduler.KEY_ID) ?: return Result.success()
        val title = inputData.getString(ReminderScheduler.KEY_TITLE).orEmpty()
        val detail = inputData.getString(ReminderScheduler.KEY_DETAIL).orEmpty()
        if (!ReminderScheduler.canNotify(applicationContext)) return Result.success()

        ReminderScheduler.ensureChannel(applicationContext)
        // Tapping it opens the thing it is about. It used to open MainActivity
        // with no extras, so every reminder — for any item, at any hour —
        // landed on Today and left you to find it. A notification that does not
        // take you to its own subject is a poke, not a reminder.
        val open = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(ReminderScheduler.EXTRA_OPEN_CARE, true)
                .putExtra(ReminderScheduler.KEY_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Appointment nudges carry no time in their detail line, which is also
        // what stops them repeating. "Done" does not apply to a visit — you do
        // not tick off a scan from a notification the evening before — so the
        // buttons are only offered on the kind they mean something for.
        val isReminder = ReminderScheduler.nextOccurrence(detail) != null

        fun actionIntent(action: String) = PendingIntent.getBroadcast(
            applicationContext,
            (action + id).hashCode(),
            Intent(applicationContext, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderScheduler.KEY_ID, id)
                .putExtra(ReminderScheduler.KEY_TITLE, title)
                .putExtra(ReminderScheduler.KEY_DETAIL, detail),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "A reminder from Aira" })
            // The detail line is the time and repeat, never anything clinical:
            // a notification is readable on a lock screen by anyone holding the
            // phone, and this app's subject matter is not always shared.
            .setContentText(detail.ifBlank { null })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(ReminderScheduler.GROUP_KEY)
            .setContentIntent(open)
            .apply {
                if (isReminder) {
                    addAction(0, "Done", actionIntent(ReminderActions.ACTION_DONE))
                    addAction(0, "Snooze 15 min", actionIntent(ReminderActions.ACTION_SNOOZE))
                }
            }
            .build()
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(id.hashCode(), notification)

        // The summary that makes the group a group.
        //
        // Android 7 and up needs one, or grouped children are shown loose and
        // the grouping does nothing. It deliberately carries no titles: the
        // children already show them, and a summary line reading "Iron tablet,
        // Folic acid" on a lock screen says more about somebody, to whoever is
        // holding the phone, than any one of them does alone.
        manager.notify(
            ReminderScheduler.SUMMARY_ID,
            NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Reminders from Aira")
                .setGroup(ReminderScheduler.GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )

        // Book tomorrow's before finishing, so a daily reminder keeps going
        // without the app being opened.
        val next = ReminderScheduler.nextOccurrence(detail)
        if (next != null) {
            val delay = Duration.between(LocalDateTime.now(), next)
            if (!delay.isNegative) {
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "aira_reminder_$id",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(delay)
                        .addTag("aira_reminder_")
                        .setInputData(inputData)
                        .build(),
                )
            }
        }
        return Result.success()
    }
}
