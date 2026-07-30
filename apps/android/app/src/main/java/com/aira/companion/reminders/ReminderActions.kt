package com.aira.companion.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aira.companion.data.AiraApi
import java.time.Duration
import kotlinx.coroutines.runBlocking

/**
 * Acting on a reminder without opening the app.
 *
 * A reminder that can only be dealt with by unlocking the phone, finding the
 * app, finding the item and tapping it is a reminder most people dismiss and
 * mean to come back to. The two things anyone actually wants at 8pm are "yes,
 * done" and "not now" — so those are the two buttons.
 *
 * Marking done goes through WorkManager rather than straight from the receiver.
 * A broadcast receiver has about ten seconds and no guarantee of a network, and
 * this one fires at exactly the moment a phone is most likely to be on a bad
 * connection — in bed, at night, on wifi that dropped. WorkManager waits for a
 * connection and retries, so "done" is not silently lost.
 */
object ReminderActions {

    const val ACTION_DONE = "com.aira.companion.REMINDER_DONE"
    const val ACTION_SNOOZE = "com.aira.companion.REMINDER_SNOOZE"

    /** How long "not now" buys. Long enough to finish what you are doing,
     *  short enough that it is still today. */
    val SNOOZE = Duration.ofMinutes(15)
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.KEY_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.KEY_TITLE).orEmpty()
        val detail = intent.getStringExtra(ReminderScheduler.KEY_DETAIL).orEmpty()

        // Taken off screen straight away either way: the button has been
        // pressed, and leaving the notification up reads as it not having
        // worked, which invites a second press.
        NotificationManagerCompat.from(context).cancel(id.hashCode())

        when (intent.action) {
            ReminderActions.ACTION_DONE ->
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "aira_done_$id",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ReminderDoneWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                        .setInputData(Data.Builder().putString(ReminderScheduler.KEY_ID, id).build())
                        .build(),
                )

            ReminderActions.ACTION_SNOOZE ->
                // Purely local — nothing about the item changes, it simply
                // arrives again shortly. Unique work keyed by the same id, so
                // snoozing twice does not stack two notifications.
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ReminderScheduler.WORK_PREFIX + id,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(ReminderActions.SNOOZE)
                        .addTag(ReminderScheduler.WORK_PREFIX)
                        .setInputData(
                            Data.Builder()
                                .putString(ReminderScheduler.KEY_ID, id)
                                .putString(ReminderScheduler.KEY_TITLE, title)
                                .putString(ReminderScheduler.KEY_DETAIL, detail)
                                .build(),
                        )
                        .build(),
                )
        }
    }
}

/** Tells the server the reminder is done, retrying until it can. */
class ReminderDoneWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getString(ReminderScheduler.KEY_ID) ?: return Result.success()
        return try {
            runBlocking { AiraApi.setReminderDone(applicationContext, id, true) }
            Result.success()
        } catch (_: Exception) {
            // Retry rather than fail: the user pressed "Done" and it is our job
            // to make that true, not to decide it did not happen because the
            // connection was bad for a moment.
            Result.retry()
        }
    }
}
