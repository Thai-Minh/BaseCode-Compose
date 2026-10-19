/**
*Created by: ThaiND
*/
package com.looper.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenOwnershipTest {
    @Test
    fun `fullscreen owner excludes other formats and only owner releases`() {
        val first = Any()
        val second = Any()
        try {
            assertTrue(AdsManager.acquireFullscreen(owner = first))
            assertFalse(AdsManager.acquireFullscreen(owner = second))
            AdsManager.releaseFullscreen(owner = second)
            assertFalse(AdsManager.acquireFullscreen(owner = second))
            AdsManager.releaseFullscreen(owner = first)
            assertTrue(AdsManager.acquireFullscreen(owner = second))
        } finally {
            AdsManager.releaseFullscreen(owner = first)
            AdsManager.releaseFullscreen(owner = second)
        }
    }
}
