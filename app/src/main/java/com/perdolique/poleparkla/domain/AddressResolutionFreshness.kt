package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.Report

object AddressResolutionFreshness {
    fun canApply(
        report: Report,
        requestedLocation: LocationSnapshot,
        addressBeforeLookup: String,
    ): Boolean =
        report.latitude == requestedLocation.latitude &&
            report.longitude == requestedLocation.longitude &&
            report.accuracyMeters == requestedLocation.accuracyMeters &&
            report.address == addressBeforeLookup
}
