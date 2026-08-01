package me.mudkip.moememos.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import me.mudkip.moememos.data.local.MoeMemosDatabase
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.ext.settingsDataStore
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Reads the current user's memos created at least 30 days ago, picks a few
 * random ones and shows the daily-review notification. Reschedules the next
 * run if the reminder is still enabled.
 */
class DailyReviewNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val settings = appContext.settingsDataStore.data.first()
        val accountKey = settings.currentUser
        val userSettings = settings.usersList.firstOrNull { it.accountKey == accountKey }?.settings

        if (accountKey.isBlank() || userSettings?.dailyReviewReminderEnabled != true) {
            return Result.success()
        }

        val dao = MoeMemosDatabase.getDatabase(appContext).memoDao()
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        val memos = dao.getAllMemos(accountKey)
            .filter { !it.archived && it.date.isBefore(cutoff) }

        if (memos.isEmpty()) {
            // No memos to review; still keep the schedule alive.
            reschedule(appContext, settings, userSettings.dailyReviewReminderHour, userSettings.dailyReviewReminderMinute)
            return Result.success()
        }

        DailyReviewNotifier.showDailyReview(appContext, memos)
        reschedule(appContext, settings, userSettings.dailyReviewReminderHour, userSettings.dailyReviewReminderMinute)
        return Result.success()
    }

    private suspend fun reschedule(
        context: Context,
        settings: Settings,
        hour: Int,
        minute: Int
    ) {
        if (settings.usersList.firstOrNull { it.accountKey == settings.currentUser }?.settings?.dailyReviewReminderEnabled != true) {
            return
        }
        DailyReviewReminderScheduler.scheduleNext(context, hour, minute)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_review_reminder_work"
    }
}

object DailyReviewReminderScheduler {

    /**
     * Schedules a one-time [DailyReviewNotificationWorker] to fire at the next
     * occurrence of [hour]:[minute] in the device's local timezone. Each run
     * reschedules the following day, keeping the cadence while enabling a
     * precise daily time.
     */
    fun scheduleNext(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }

        val delayMillis = Duration.between(now, next).toMillis()

        val request = OneTimeWorkRequestBuilder<DailyReviewNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DailyReviewNotificationWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DailyReviewNotificationWorker.UNIQUE_WORK_NAME)
    }

    fun isScheduled(context: Context): Boolean {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DailyReviewNotificationWorker.UNIQUE_WORK_NAME)
            .get()
            .any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING }
    }
}
