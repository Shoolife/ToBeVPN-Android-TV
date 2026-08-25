package com.tobevpn.tv.presentation.subscription

import com.tobevpn.tv.data.remote.dto.PurchasePlanDto
import java.util.Locale

/**
 * A normal subscription-card click starts at the first tariff. The renewal
 * reminder instead opens the tariff that the backend marked as renewable.
 */
internal fun initialPurchasePlanTabKey(
    plans: List<PurchasePlanDto>,
    currentPlanDisplayName: String?,
    selectCurrentPlan: Boolean,
): String? {
    val firstPlanKey = plans.firstOrNull()?.id?.toString() ?: return null
    if (!selectCurrentPlan) return firstPlanKey
    return preferredRenewalTariffKey(plans, currentPlanDisplayName) ?: firstPlanKey
}

internal fun preferredRenewalTariffKey(
    plans: List<PurchasePlanDto>,
    currentPlanDisplayName: String?,
): String? {
    plans.firstOrNull { plan ->
        plan.purchaseType.trim().equals("RENEW", ignoreCase = true)
    }?.let { return it.id.toString() }

    val normalizedCurrentName = currentPlanDisplayName.normalizedPlanName()
        .takeIf { it.isNotEmpty() }
        ?: return null
    return plans.firstOrNull { it.name.normalizedPlanName() == normalizedCurrentName }
        ?.id
        ?.toString()
}

private fun String?.normalizedPlanName(): String =
    this
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(PLAN_WHITESPACE, " ")
        .orEmpty()

private val PLAN_WHITESPACE = Regex("\\s+")
