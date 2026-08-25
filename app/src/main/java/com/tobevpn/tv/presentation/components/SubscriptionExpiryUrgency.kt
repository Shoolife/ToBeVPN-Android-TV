package com.tobevpn.tv.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.tobevpn.tv.presentation.theme.VpnOrange
import com.tobevpn.tv.presentation.theme.VpnRed

internal enum class SubscriptionExpiryUrgency {
    NORMAL,
    WARNING,
    CRITICAL,
}

/**
 * One expiry policy for every subscription date in the TV app.
 *
 * More than seven days stays neutral, the final seven days are orange, and
 * the final 72 hours (including an expired subscription) are red.
 */
internal fun subscriptionExpiryUrgency(
    expiresAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): SubscriptionExpiryUrgency {
    val millisLeft = expiresAtMillis - nowMillis
    return when {
        millisLeft <= SUBSCRIPTION_CRITICAL_THRESHOLD_MS ->
            SubscriptionExpiryUrgency.CRITICAL
        millisLeft <= SUBSCRIPTION_WARNING_THRESHOLD_MS ->
            SubscriptionExpiryUrgency.WARNING
        else -> SubscriptionExpiryUrgency.NORMAL
    }
}

internal fun subscriptionExpiryDateColor(
    expiresAtMillis: Long,
    normalColor: Color,
    nowMillis: Long = System.currentTimeMillis(),
): Color = when (subscriptionExpiryUrgency(expiresAtMillis, nowMillis)) {
    SubscriptionExpiryUrgency.CRITICAL -> VpnRed
    SubscriptionExpiryUrgency.WARNING -> VpnOrange
    SubscriptionExpiryUrgency.NORMAL -> normalColor
}

/** Applies the urgency colour only to the date, preserving surrounding text. */
internal fun textWithAccentedDate(
    text: String,
    date: String,
    dateColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val dateStart = text.lastIndexOf(date)
    if (dateStart >= 0) {
        addStyle(
            style = SpanStyle(color = dateColor),
            start = dateStart,
            end = dateStart + date.length,
        )
    }
}

private const val SUBSCRIPTION_DAY_MS = 86_400_000L
private const val SUBSCRIPTION_CRITICAL_THRESHOLD_MS = 3L * SUBSCRIPTION_DAY_MS
private const val SUBSCRIPTION_WARNING_THRESHOLD_MS = 7L * SUBSCRIPTION_DAY_MS
