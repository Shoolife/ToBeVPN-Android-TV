package com.tobevpn.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalNetworkSelectorTest {
    @Test
    fun unvalidatedPhysicalNetworkRemainsUsable() {
        val selector = PhysicalNetworkSelector<String>()

        assertEquals(
            PhysicalNetworkSelector.ChangeType.INITIAL,
            selector.update("restricted-wifi", validated = false, priority = 200).type,
        )
        assertEquals("restricted-wifi", selector.selectedOrNull())
    }

    @Test
    fun validatedNetworkIsPreferredOverHigherPriorityUnvalidatedNetwork() {
        val selector = PhysicalNetworkSelector<String>()
        selector.update("ethernet", validated = false, priority = 300)

        assertEquals(
            PhysicalNetworkSelector.ChangeType.HANDOVER,
            selector.update("wifi", validated = true, priority = 200).type,
        )
        assertEquals("wifi", selector.selectedOrNull())
    }

    @Test
    fun preservesCurrentNetworkUntilABetterValidatedOneAppears() {
        val selector = PhysicalNetworkSelector<String>()
        assertEquals(
            PhysicalNetworkSelector.ChangeType.INITIAL,
            selector.update("wifi", validated = true, priority = 200).type,
        )
        assertEquals(
            PhysicalNetworkSelector.ChangeType.UNCHANGED,
            selector.update("cellular", validated = true, priority = 100).type,
        )
        assertEquals(
            PhysicalNetworkSelector.ChangeType.HANDOVER,
            selector.update("ethernet", validated = true, priority = 300).type,
        )
    }

    @Test
    fun losingSelectedNetworkHandsOverToRemainingCandidate() {
        val selector = PhysicalNetworkSelector<String>()
        selector.update("wifi", validated = true, priority = 200)
        selector.update("cellular", validated = true, priority = 100)
        assertEquals(
            PhysicalNetworkSelector.ChangeType.HANDOVER,
            selector.onLost("wifi").type,
        )
        assertEquals("cellular", selector.selectedOrNull())
    }
}
