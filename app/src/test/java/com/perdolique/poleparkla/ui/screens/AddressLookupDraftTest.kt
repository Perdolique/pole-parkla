package com.perdolique.poleparkla.ui.screens

import com.perdolique.poleparkla.domain.AddressDraft
import com.perdolique.poleparkla.model.AddressCandidate
import com.perdolique.poleparkla.model.AddressCandidateType
import com.perdolique.poleparkla.model.AddressResolution
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.ui.AddressLookupResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressLookupDraftTest {
    @Test
    fun `background suggestion fills an empty local address`() {
        val draft = draft(address = "")

        val updated = addressDraftFromLookup(draft, lookup(), applyLocation = false)

        assertEquals("Liivalaia tn 2, Tallinn", updated.address)
        assertEquals(draft.latitude, updated.latitude)
        assertEquals(draft.longitude, updated.longitude)
        assertEquals(draft.accuracyMeters, updated.accuracyMeters)
    }

    @Test
    fun `background suggestion does not overwrite manual input`() {
        val draft = draft(address = "My corrected address")

        val updated = addressDraftFromLookup(draft, lookup(), applyLocation = false)

        assertEquals(draft, updated)
    }

    @Test
    fun `explicit location action applies coordinates and suggestion to local draft`() {
        val updated = addressDraftFromLookup(
            draft(address = "My corrected address"),
            lookup(),
            applyLocation = true,
        )

        assertEquals("Liivalaia tn 2, Tallinn", updated.address)
        assertEquals(59.432, updated.latitude)
        assertEquals(24.757, updated.longitude)
        assertEquals(4.5f, updated.accuracyMeters)
    }

    @Test
    fun `manual coordinate edit clears candidates from the previous point`() {
        val staleCandidate = lookup().resolution?.candidates.orEmpty().single()
        val feedback = AddressLookupFeedback(
            candidates = listOf(staleCandidate),
            addressLookupFailed = true,
        )

        assertEquals(AddressLookupFeedback(), feedback.clearedForManualCoordinates())
    }

    private fun draft(address: String) = AddressDraft(
        address = address,
        latitude = 59.437,
        longitude = 24.7536,
        accuracyMeters = 8.5f,
    )

    private fun lookup(): AddressLookupResult {
        val location = LocationSnapshot(59.432, 24.757, 4.5f, 1L)
        val suggestion = AddressCandidate(
            address = "Liivalaia tn 2, Tallinn",
            distanceMeters = 5,
            type = AddressCandidateType.BUILDING,
        )
        return AddressLookupResult(
            location = location,
            resolution = AddressResolution(
                location = location,
                candidates = listOf(suggestion),
                suggested = suggestion,
                needsReview = false,
            ),
        )
    }
}
