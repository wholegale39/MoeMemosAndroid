package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MemoEditGesture {
    NONE,
    SINGLE,
    DOUBLE,
    LONG,
}

@Serializable
data class UserSettings(
    val draft: String = "",
    val acceptedUnsupportedSyncVersions: List<String> = emptyList(),
    val editGesture: MemoEditGesture = MemoEditGesture.NONE,
    val dailyReviewReminderEnabled: Boolean = false,
    val dailyReviewReminderHour: Int = 21,
    val dailyReviewReminderMinute: Int = 0,
)
