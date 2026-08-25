package com.tobevpn.tv.presentation.subscription

import com.tobevpn.tv.data.remote.dto.PurchasePlanDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchasePlanTabSelectionTest {
    @Test
    fun `regular subscription card selects first tariff`() {
        val plans = listOf(
            plan(id = 1L, name = "Комфорт", purchaseType = "CHANGE"),
            plan(id = 2L, name = "Корпорат", purchaseType = "RENEW"),
        )

        assertEquals(
            "1",
            initialPurchasePlanTabKey(plans, "Корпорат", selectCurrentPlan = false),
        )
    }

    @Test
    fun `renewal reminder selects server renewal tariff`() {
        val plans = listOf(
            plan(id = 1L, name = "Комфорт", purchaseType = "CHANGE"),
            plan(id = 2L, name = "Корпорат", purchaseType = "renew"),
        )

        assertEquals(
            "2",
            initialPurchasePlanTabKey(plans, "Другое название", selectCurrentPlan = true),
        )
    }

    @Test
    fun `current plan name is fallback for older response`() {
        val plans = listOf(
            plan(id = 1L, name = "Комфорт", purchaseType = "CHANGE"),
            plan(id = 2L, name = "  КОРПОРАТ   ", purchaseType = "CHANGE"),
        )

        assertEquals("2", preferredRenewalTariffKey(plans, "корпорат"))
    }

    @Test
    fun `unknown current plan leaves fallback to caller`() {
        val plans = listOf(plan(id = 1L, name = "Комфорт", purchaseType = "CHANGE"))

        assertNull(preferredRenewalTariffKey(plans, "Корпорат"))
    }

    private fun plan(
        id: Long,
        name: String,
        purchaseType: String,
    ): PurchasePlanDto = PurchasePlanDto(
        id = id,
        publicCode = "plan-$id",
        name = name,
        type = "BOTH",
        availability = "ALL",
        purchaseType = purchaseType,
    )
}
