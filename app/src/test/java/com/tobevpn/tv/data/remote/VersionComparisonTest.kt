package com.tobevpn.tv.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {

    @Test
    fun tvSuffixIsIgnoredForEqualVersion() {
        assertFalse(
            isVersionLowerThanMinimum(
                currentVersion = "1.0.24-tv",
                minimumVersion = "1.0.24",
            )
        )
    }

    @Test
    fun tvSuffixDoesNotPreventMandatoryUpdate() {
        assertTrue(
            isVersionLowerThanMinimum(
                currentVersion = "1.0.24-tv",
                minimumVersion = "1.0.25",
            )
        )
        assertTrue(
            isVersionLowerThanMinimum(
                currentVersion = "1.0.24-tv",
                minimumVersion = "v1.0.25-tv",
            )
        )
    }

    @Test
    fun malformedMinimumDoesNotLockTheApplication() {
        assertFalse(
            isVersionLowerThanMinimum(
                currentVersion = "1.0.24-tv",
                minimumVersion = "invalid",
            )
        )
    }
}
