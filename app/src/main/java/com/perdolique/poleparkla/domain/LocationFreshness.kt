package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.LocationSnapshot

object LocationFreshness {
    private const val MAX_AGE_MILLIS = 2 * 60 * 1_000L
    private const val MAX_FUTURE_SKEW_MILLIS = 30 * 1_000L

    fun isFresh(location: LocationSnapshot, nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        location.capturedAtEpochMillis in
            (nowEpochMillis - MAX_AGE_MILLIS)..(nowEpochMillis + MAX_FUTURE_SKEW_MILLIS)
}
