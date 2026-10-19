/**
*Created by: ThaiND
*/
package com.looper.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdRevenueMetadataTest {
    @Test
    fun `micros retain fractional currency value`() {
        assertEquals(1.234567, revenueInCurrency(valueMicros = 1_234_567L), 0.000000001)
        assertEquals(0.0, revenueInCurrency(valueMicros = 0L), 0.0)
    }

    @Test
    fun `metadata is optional and placement is logical`() {
        assertNull(AdRevenueMetadata().adFormat)
        assertNull(AdRevenueMetadata().placement)
        assertEquals("Home", AdRevenueMetadata(adFormat = "native", placement = "Home").placement)
    }

    @Test
    fun `register replaces listener and dispatches once with metadata`() {
        var oldCalls = 0
        var newCalls = 0
        val metadata = AdRevenueMetadata(adFormat = "banner", placement = "test")
        AdjustManager.setPaidEventListener { _: String, _: Long -> oldCalls++ }
        AdjustManager.setPaidEventListener { currency, micros, actual ->
            newCalls++
            assertEquals("USD", currency)
            assertEquals(123L, micros)
            assertEquals(metadata, actual)
        }
        try {
            AdjustManager.dispatchPaidEvent(currency = "USD", revenue = 123L, metadata = metadata)
            assertEquals(0, oldCalls)
            assertEquals(1, newCalls)
        } finally {
            AdjustManager.setPaidEventListener { _: String, _: Long, _: AdRevenueMetadata -> }
        }
    }
}
