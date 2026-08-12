package com.tobevpn.tv.presentation.subscription

import com.tobevpn.tv.data.remote.dto.PurchaseDurationDto
import java.util.Locale

internal data class ResolvedPurchasePrice(
    val currency: String,
    val originalAmount: String,
    val finalAmount: String,
    val discountPercent: Int,
) {
    val hasDiscount: Boolean
        get() {
            if (discountPercent <= 0) return false
            val original = originalAmount.toBigDecimalOrNull() ?: return false
            val final = finalAmount.toBigDecimalOrNull() ?: return false
            return final < original
        }
}

/** Server-calculated payment price is authoritative; prices[] is legacy fallback only. */
internal fun resolvePurchasePrice(
    duration: PurchaseDurationDto,
    preferredCurrencies: List<String>,
): ResolvedPurchasePrice? {
    val currencyOrder = preferredCurrencies
        .map { it.trim().uppercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .distinct()
    val methods = duration.paymentMethods.orEmpty().filter {
        it.currency.isNotBlank() && it.originalAmount.isNotBlank() && it.finalAmount.isNotBlank()
    }
    val method = currencyOrder.firstNotNullOfOrNull { currency ->
        methods.firstOrNull { it.currency.equals(currency, ignoreCase = true) }
    } ?: methods.firstOrNull()
    if (method != null) {
        return ResolvedPurchasePrice(
            currency = method.currency.trim().uppercase(Locale.ROOT),
            originalAmount = method.originalAmount,
            finalAmount = method.finalAmount,
            discountPercent = method.discountPercent.coerceIn(0, 100),
        )
    }
    val prices = duration.prices.orEmpty().filter {
        it.currency.isNotBlank() && it.amount.isNotBlank()
    }
    val price = currencyOrder.firstNotNullOfOrNull { currency ->
        prices.firstOrNull { it.currency.equals(currency, ignoreCase = true) }
    } ?: prices.firstOrNull() ?: return null
    return ResolvedPurchasePrice(
        currency = price.currency.trim().uppercase(Locale.ROOT),
        originalAmount = price.amount,
        finalAmount = price.amount,
        discountPercent = 0,
    )
}
