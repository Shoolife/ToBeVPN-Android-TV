package com.tobevpn.tv.presentation.subscription

import com.tobevpn.tv.data.remote.dto.PurchaseDurationDto
import com.tobevpn.tv.data.remote.dto.PurchasePaymentMethodDto
import com.tobevpn.tv.data.remote.dto.PurchasePriceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchasePriceResolverTest {
    @Test
    fun prefersServerFinalAmountOverLegacyBasePrice() {
        val duration = PurchaseDurationDto(
            id = 1,
            days = 30,
            prices = listOf(PurchasePriceDto("RUB", "333")),
            paymentMethods = listOf(
                PurchasePaymentMethodDto("STARS", "RUB", "333", "299", 10),
            ),
        )
        val resolved = requireNotNull(resolvePurchasePrice(duration, listOf("RUB")))
        assertEquals("299", resolved.finalAmount)
        assertEquals("333", resolved.originalAmount)
        assertEquals(10, resolved.discountPercent)
        assertTrue(resolved.hasDiscount)
    }
}
