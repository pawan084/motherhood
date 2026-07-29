package com.aira.companion.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
    private const val WORK_PREFIX = "aira_reminder_"

    /** Whether this build can post notifications at all, per the OS. */
    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

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
    fun syncAll(context: Context, reminders: List<CareItem>) {
        ensureChannel(context)
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(WORK_PREFIX)
        if (!canNotify(context)) return
        reminders.filterNot { it.done }.forEach { schedule(context, it) }
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
        val open = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(id.hashCode(), notification)

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
