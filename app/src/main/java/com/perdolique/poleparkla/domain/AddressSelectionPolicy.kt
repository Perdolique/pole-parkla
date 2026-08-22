package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.AddressMapSelection

data class AddressDraft(
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
)

object AddressSelectionPolicy {
    fun useMapSelection(
        draft: AddressDraft,
        selection: AddressMapSelection,
    ): AddressDraft {
        if (selection.movedPoint || (draft.latitude == null && draft.longitude == null)) {
            return draft.copy(
                address = selection.address,
                latitude = selection.latitude,
                longitude = selection.longitude,
                accuracyMeters = null,
            )
        }
        return draft.copy(address = selection.address)
    }
}
