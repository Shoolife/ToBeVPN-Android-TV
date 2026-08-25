package com.tobevpn.tv.presentation.main

import com.tobevpn.tv.domain.model.UserPlan
import kotlin.math.ceil

/**
 * Subscription-renewal reminder policy shared by the TV UI and unit tests.
 *
 * A warning colour may be shown earlier, but the blocking TV-sized reminder is
 * deliberately limited to the final 72 hours (or an already expired plan).
 */
internal fun shouldShowSubscriptionReminder(
    plan: UserPlan,
    expiresAtMillis: Long?,
    nowMillis: Long,
): Boolean = when (plan) {
    UserPlan.EXPIRED -> true
    UserPlan.PAID,
    UserPlan.ADMIN,
    -> expiresAtMillis
        ?.minus(nowMillis)
        ?.let { it in 0L..SUBSCRIPTION_REMINDER_THRESHOLD_MS }
        ?: false
    UserPlan.FREE_TRIAL -> false
}

internal fun subscriptionReminderDaysLeft(
    expiresAtMillis: Long,
    nowMillis: Long,
): Int {
    val millisLeft = (expiresAtMillis - nowMillis).coerceAtLeast(0L)
    return ceil(millisLeft.toDouble() / SUBSCRIPTION_DAY_MS).toInt()
}

/**
 * Keeps "Later" across process restarts without hiding the expired reminder.
 * If less than 12 hours remain, the snooze ends exactly at plan expiry.
 */
internal fun subscriptionReminderSnoozeUntil(
    nowMillis: Long,
    expiresAtMillis: Long?,
): Long {
    val regularSnoozeUntil = nowMillis + SUBSCRIPTION_REMINDER_SNOOZE_MS
    return expiresAtMillis
        ?.takeIf { it > nowMillis }
        ?.let { minOf(regularSnoozeUntil, it) }
        ?: regularSnoozeUntil
}

/** A renewal changes the expiry timestamp and invalidates the old snooze. */
internal fun isSubscriptionReminderSnoozed(
    snoozedUntilMillis: Long,
    snoozedForExpiryMillis: Long?,
    currentExpiryMillis: Long?,
    nowMillis: Long,
): Boolean =
    snoozedUntilMillis > nowMillis &&
        snoozedForExpiryMillis == currentExpiryMillis

internal const val SUBSCRIPTION_DAY_MS = 86_400_000L
private const val SUBSCRIPTION_REMINDER_THRESHOLD_MS = 3L * SUBSCRIPTION_DAY_MS
private const val SUBSCRIPTION_REMINDER_SNOOZE_MS = 12L * 60L * 60L * 1000L
