package com.perdolique.poleparkla.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLocationInputTest {
    private val tallinn = ZoneId.of("Europe/Tallinn")

    @Test
    fun `parses strict time and comma coordinates`() {
        val result = ReportLocationInput.validate(
            address = " Lastekodu tn 42, Tallinn ",
            latitude = "59,437",
            longitude = "24,7536",
            occurredAt = "13.08.2026 16:58",
            zoneId = tallinn,
        )

        val parsed = (result as ReportLocationInputResult.Valid).location
        assertEquals("Lastekodu tn 42, Tallinn", parsed.address)
        assertEquals(59.437, parsed.latitude)
        assertEquals(24.7536, parsed.longitude)
        assertEquals("13.08.2026 16:58", ReportLocationInput.format(parsed.occurredAtEpochMillis, tallinn))
    }

    @Test
    fun `shared coordinate parsers accept comma decimals and enforce ranges`() {
        assertEquals(59.437, ReportLocationInput.parseLatitude(" 59,437 "))
        assertEquals(24.7536, ReportLocationInput.parseLongitude("24,7536"))
        assertNull(ReportLocationInput.parseLatitude("91"))
        assertNull(ReportLocationInput.parseLongitude("181"))
    }

    @Test
    fun `accepts an address without coordinates`() {
        val result = ReportLocationInput.validate(
            address = "Lastekodu tn 42, Tallinn",
            latitude = "",
            longitude = "",
            occurredAt = "13.08.2026 16:58",
            zoneId = tallinn,
        )

        val parsed = (result as ReportLocationInputResult.Valid).location
        assertNull(parsed.latitude)
        assertNull(parsed.longitude)
    }

    @Test
    fun `reports an empty time separately from blank coordinates`() {
        val result = ReportLocationInput.validate(
            address = "Lastekodu tn 42, Tallinn",
            latitude = "",
            longitude = "",
            occurredAt = "",
            zoneId = tallinn,
        )

        assertEquals(
            ReportLocationInputResult.Invalid(ReportLocationInputError.TIME),
            result,
        )
    }

    @Test
    fun `reports an invalid coordinate pair separately from time`() {
        val result = ReportLocationInput.validate(
            address = "Lastekodu tn 42, Tallinn",
            latitude = "59.4",
            longitude = "",
            occurredAt = "13.08.2026 16:58",
            zoneId = tallinn,
        )

        assertEquals(
            ReportLocationInputResult.Invalid(ReportLocationInputError.COORDINATES),
            result,
        )
    }

    @Test
    fun `requires either an address or a coordinate pair`() {
        val result = ReportLocationInput.validate(
            address = "",
            latitude = "",
            longitude = "",
            occurredAt = "13.08.2026 16:58",
            zoneId = tallinn,
        )

        assertEquals(
            ReportLocationInputResult.Invalid(ReportLocationInputError.LOCATION),
            result,
        )
    }

    @Test
    fun `rejects invalid time ranges non-finite values and half a coordinate pair`() {
        val valid = arrayOf("Lastekodu tn 42, Tallinn", "59.4", "24.7", "13.08.2026 16:58")

        listOf(
            ReportLocationInput.validate(valid[0], valid[1], valid[2], "31.02.2026 12:15", tallinn),
            ReportLocationInput.validate(valid[0], "91", valid[2], valid[3], tallinn),
            ReportLocationInput.validate(valid[0], valid[1], "181", valid[3], tallinn),
            ReportLocationInput.validate(valid[0], "NaN", valid[2], valid[3], tallinn),
            ReportLocationInput.validate(valid[0], valid[1], "", valid[3], tallinn),
        ).forEach { result ->
            assertTrue(result is ReportLocationInputResult.Invalid)
        }
    }
}
