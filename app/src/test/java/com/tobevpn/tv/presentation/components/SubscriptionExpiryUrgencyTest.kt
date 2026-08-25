package com.tobevpn.tv.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionExpiryUrgencyTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `more than seven days is neutral`() {
        assertEquals(
            SubscriptionExpiryUrgency.NORMAL,
            subscriptionExpiryUrgency(now + 7 * day + 1, now),
        )
    }

    @Test
    fun `exactly seven days is warning`() {
        assertEquals(
            SubscriptionExpiryUrgency.WARNING,
            subscriptionExpiryUrgency(now + 7 * day, now),
        )
    }

    @Test
    fun `more than three days remains warning`() {
        assertEquals(
            SubscriptionExpiryUrgency.WARNING,
            subscriptionExpiryUrgency(now + 3 * day + 1, now),
        )
    }

    @Test
    fun `exactly three days is critical`() {
        assertEquals(
            SubscriptionExpiryUrgency.CRITICAL,
            subscriptionExpiryUrgency(now + 3 * day, now),
        )
    }

    @Test
    fun `expired subscription is critical`() {
        assertEquals(
            SubscriptionExpiryUrgency.CRITICAL,
            subscriptionExpiryUrgency(now - 1, now),
        )
    }
}
