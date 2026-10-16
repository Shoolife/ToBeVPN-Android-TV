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

    @Test
    fun termSavingStrikesThroughMonthlyPriceTimesMonths() {
        val month = term(30, "450.00", "450.00", "450", 0)
        val quarter = term(90, "1199.00", "1199.00", "1199", 11)
        val resolved = requireNotNull(
            resolvePurchasePrice(quarter, listOf("RUB"), listOf(month, quarter)),
        )
        assertEquals("1350", resolved.originalAmount)
        assertEquals("1199", resolved.finalAmount)
        assertEquals(11, resolved.discountPercent)
        assertTrue(resolved.hasDiscount)
    }

    @Test
    fun promoCodeSavingUsesTheSameBase() {
        val month = term(30, "450.00", "450.00", "360", 20)
        val quarter = term(90, "1199.00", "1199.00", "959", 28)
        val resolved = requireNotNull(
            resolvePurchasePrice(quarter, listOf("RUB"), listOf(month, quarter)),
        )
        assertEquals("1350", resolved.originalAmount)
        assertEquals("959", resolved.finalAmount)
        assertEquals(28, resolved.discountPercent)
        // The shortest term itself strikes through its own price.
        val monthResolved = requireNotNull(
            resolvePurchasePrice(month, listOf("RUB"), listOf(month, quarter)),
        )
        assertEquals("450.00", monthResolved.originalAmount)
        assertEquals(20, monthResolved.discountPercent)
    }

    @Test
    fun dailyShortestTermScalesByDays() {
        val day = term(1, "15.00", "15.00", "15", 0)
        val quarter = term(90, "550.00", "550.00", "550", 59)
        val year = term(365, "1500.00", "1500.00", "1500", 72)
        val all = listOf(day, quarter, year)
        assertEquals(
            "1350",
            requireNotNull(resolvePurchasePrice(quarter, listOf("RUB"), all)).originalAmount,
        )
        assertEquals(
            "5475",
            requireNotNull(resolvePurchasePrice(year, listOf("RUB"), all)).originalAmount,
        )
    }

    private fun term(
        days: Int,
        price: String,
        original: String,
        final: String,
        discount: Int,
    ) = PurchaseDurationDto(
        id = days.toLong(),
        days = days,
        prices = listOf(PurchasePriceDto(currency = "RUB", amount = price, discountPercent = discount)),
        paymentMethods = listOf(
            PurchasePaymentMethodDto(
                gatewayType = "PLATEGA",
                currency = "RUB",
                originalAmount = original,
                finalAmount = final,
                discountPercent = discount,
            ),
        ),
    )
}
