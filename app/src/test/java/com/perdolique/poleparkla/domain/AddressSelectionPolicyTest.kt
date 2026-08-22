package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.AddressMapSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressSelectionPolicyTest {
    @Test
    fun `explicit map point changes coordinates and clears accuracy`() {
        val selected = AddressSelectionPolicy.useMapSelection(
            draft(),
            AddressMapSelection(
                address = "Liivalaia tn 2, Tallinn",
                latitude = 59.432,
                longitude = 24.757,
                movedPoint = true,
            ),
        )

        assertEquals(59.432, selected.latitude)
        assertEquals(24.757, selected.longitude)
        assertNull(selected.accuracyMeters)
    }

    @Test
    fun `search result preserves existing coordinates`() {
        val draft = draft()
        val selected = AddressSelectionPolicy.useMapSelection(
            draft,
            AddressMapSelection(
                address = "Liivalaia tn 2, Tallinn",
                latitude = 59.432,
                longitude = 24.757,
                movedPoint = false,
            ),
        )

        assertEquals(draft.latitude, selected.latitude)
        assertEquals(draft.longitude, selected.longitude)
        assertEquals(draft.accuracyMeters, selected.accuracyMeters)
    }

    @Test
    fun `search result supplies coordinates when draft has none`() {
        val selected = AddressSelectionPolicy.useMapSelection(
            draft().copy(latitude = null, longitude = null, accuracyMeters = null),
            AddressMapSelection(
                address = "Liivalaia tn 2, Tallinn",
                latitude = 59.432,
                longitude = 24.757,
                movedPoint = false,
            ),
        )

        assertEquals(59.432, selected.latitude)
        assertEquals(24.757, selected.longitude)
    }

    private fun draft() = AddressDraft(
        address = "Original",
        latitude = 59.437,
        longitude = 24.7536,
        accuracyMeters = 8.5f,
    )
}
