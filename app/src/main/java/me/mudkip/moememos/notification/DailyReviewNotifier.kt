package me.mudkip.moememos.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.mudkip.moememos.MainActivity
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity

object DailyReviewNotifier {
    const val CHANNEL_ID = "daily_review_reminder"
    const val NOTIFICATION_ID = 20260731

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.daily_review_reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.daily_review_reminder_channel_description)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Shows a notification containing up to [maxItems] randomly picked memos
     * created at least [daysAgo] days before now. Tapping opens the daily review.
     */
    fun showDailyReview(context: Context, memos: List<MemoEntity>, daysAgo: Long = 30, maxItems: Int = 3) {
        if (memos.isEmpty()) return

        val picked = memos.shuffled().take(maxItems)
        val title = context.getString(R.string.daily_review_notification_title)
        val text = picked.joinToString("\n") { memo ->
            memo.content.lineSequence().firstOrNull()?.trim()?.take(60) ?: context.getString(R.string.memo)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_VIEW_DAILY_REVIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; ignore silently.
        }
    }
}
