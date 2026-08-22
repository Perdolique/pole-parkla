package com.perdolique.poleparkla.ui.screens

import com.perdolique.poleparkla.model.AddressCandidate
import com.perdolique.poleparkla.model.AddressCandidateType
import com.perdolique.poleparkla.model.AddressResolution
import com.perdolique.poleparkla.model.LocationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

class AddressMapPickerStateTest {
    @Test
    fun `map position uses longitude latitude axis order`() {
        val point = Position(24.7536, 59.437).toAddressMapPoint()

        assertEquals(59.437, point.latitude, 0.0)
        assertEquals(24.7536, point.longitude, 0.0)
        val location = point.toLocationSnapshot(capturedAtEpochMillis = 123L)
        assertEquals(59.437, location.latitude, 0.0)
        assertEquals(24.7536, location.longitude, 0.0)
        assertNull(location.accuracyMeters)
    }

    @Test
    fun `resolved point returns suggestion and every candidate`() {
        val point = AddressMapPoint(latitude = 59.437, longitude = 24.7536)
        val candidates = listOf(
            candidate("Tartu mnt, Tallinn", AddressCandidateType.STREET),
            candidate("Tartu mnt 24, Tallinn", AddressCandidateType.BUILDING),
        )
        val selection = addressMapSelection(
            point = point,
            initialAddress = "Manual address",
            resolution = AddressResolution(
                location = point.toLocationSnapshot(123L),
                candidates = candidates,
                suggested = candidates.last(),
                needsReview = true,
            ),
        )

        requireNotNull(selection)
        assertEquals("Tartu mnt 24, Tallinn", selection.address)
        assertEquals(candidates, selection.candidates)
        assertFalse(selection.addressLookupFailed)
        assertTrue(selection.movedPoint)
    }

    @Test
    fun `empty or failed lookup preserves manual address`() {
        val point = AddressMapPoint(latitude = 52.52, longitude = 13.405)

        val failed = addressMapSelection(point, "Manual Berlin address", resolution = null)
        val empty = addressMapSelection(
            point,
            "Manual Berlin address",
            AddressResolution(
                location = point.toLocationSnapshot(123L),
                candidates = emptyList(),
                suggested = null,
                needsReview = true,
            ),
        )

        requireNotNull(failed)
        requireNotNull(empty)
        assertEquals("Manual Berlin address", failed.address)
        assertEquals("Manual Berlin address", empty.address)
        assertTrue(failed.addressLookupFailed)
        assertTrue(empty.addressLookupFailed)
    }

    @Test
    fun `response for another point is ignored`() {
        val currentPoint = AddressMapPoint(latitude = 59.437, longitude = 24.7536)
        val stalePoint = AddressMapPoint(latitude = 59.432, longitude = 24.757)
        val staleCandidate = candidate("Liivalaia tn 2, Tallinn", AddressCandidateType.BUILDING)

        val selection = addressMapSelection(
            point = currentPoint,
            initialAddress = "Current",
            resolution = AddressResolution(
                location = stalePoint.toLocationSnapshot(123L),
                candidates = listOf(staleCandidate),
                suggested = staleCandidate,
                needsReview = false,
            ),
        )

        assertNull(selection)
    }

    private fun candidate(address: String, type: AddressCandidateType) = AddressCandidate(
        address = address,
        distanceMeters = 0,
        type = type,
    )
}
