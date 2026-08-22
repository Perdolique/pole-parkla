package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.LocationSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessTest {
    private val now = 1_000_000L

    @Test
    fun `accepts recent location`() {
        assertTrue(LocationFreshness.isFresh(location(now - 15_000L), now))
    }

    @Test
    fun `rejects location older than two minutes`() {
        assertFalse(LocationFreshness.isFresh(location(now - 120_001L), now))
    }

    @Test
    fun `rejects implausibly future location`() {
        assertFalse(LocationFreshness.isFresh(location(now + 30_001L), now))
    }

    private fun location(capturedAtEpochMillis: Long) = LocationSnapshot(
        latitude = 59.437,
        longitude = 24.7536,
        accuracyMeters = 8f,
        capturedAtEpochMillis = capturedAtEpochMillis,
    )
}
