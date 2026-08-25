package com.tobevpn.tv.presentation.main

import com.tobevpn.tv.domain.model.UserPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionReminderPolicyTest {

    @Test
    fun `paid reminder starts at exactly three days`() {
        val now = 1_000_000L

        assertFalse(
            shouldShowSubscriptionReminder(
                plan = UserPlan.PAID,
                expiresAtMillis = now + 3L * SUBSCRIPTION_DAY_MS + 1L,
                nowMillis = now,
            ),
        )
        assertTrue(
            shouldShowSubscriptionReminder(
                plan = UserPlan.PAID,
                expiresAtMillis = now + 3L * SUBSCRIPTION_DAY_MS,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `expired plan always shows reminder`() {
        assertTrue(
            shouldShowSubscriptionReminder(
                plan = UserPlan.EXPIRED,
                expiresAtMillis = null,
                nowMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun `free trial does not show renewal reminder`() {
        val now = 1_000_000L
        assertFalse(
            shouldShowSubscriptionReminder(
                plan = UserPlan.FREE_TRIAL,
                expiresAtMillis = now + SUBSCRIPTION_DAY_MS,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `days left rounds up for user-facing title`() {
        val now = 1_000_000L
        assertEquals(
            2,
            subscriptionReminderDaysLeft(
                expiresAtMillis = now + SUBSCRIPTION_DAY_MS + 1L,
                nowMillis = now,
            ),
        )
        assertEquals(
            0,
            subscriptionReminderDaysLeft(
                expiresAtMillis = now,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `snooze is capped by plan expiry`() {
        val now = 1_000_000L
        val expiry = now + 2L * 60L * 60L * 1000L

        assertEquals(expiry, subscriptionReminderSnoozeUntil(now, expiry))
    }

    @Test
    fun `changed expiry invalidates persisted snooze`() {
        val now = 1_000_000L
        val originalExpiry = now + SUBSCRIPTION_DAY_MS
        val renewedExpiry = originalExpiry + 30L * SUBSCRIPTION_DAY_MS
        val snoozedUntil = now + 60_000L

        assertTrue(
            isSubscriptionReminderSnoozed(
                snoozedUntilMillis = snoozedUntil,
                snoozedForExpiryMillis = originalExpiry,
                currentExpiryMillis = originalExpiry,
                nowMillis = now,
            ),
        )
        assertFalse(
            isSubscriptionReminderSnoozed(
                snoozedUntilMillis = snoozedUntil,
                snoozedForExpiryMillis = originalExpiry,
                currentExpiryMillis = renewedExpiry,
                nowMillis = now,
            ),
        )
    }
}
