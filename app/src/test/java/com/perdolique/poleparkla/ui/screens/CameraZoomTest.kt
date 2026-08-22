package com.perdolique.poleparkla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test
    fun zoomFactorScalesCurrentRatioInBothDirections() {
        assertEquals(2f, calculateCameraZoomRatio(1f, 2f, 0.5f, 30f), 0.0001f)
        assertEquals(1f, calculateCameraZoomRatio(2f, 0.5f, 0.5f, 30f), 0.0001f)
    }

    @Test
    fun zoomOutReachesDeviceMinimum() {
        assertEquals(0.5f, calculateCameraZoomRatio(1f, 0.1f, 0.5f, 30f), 0.0001f)
    }

    @Test
    fun zoomRatioIsClampedToDeviceRange() {
        assertEquals(0.5f, calculateCameraZoomRatio(0.5f, 0.5f, 0.5f, 30f), 0.0001f)
        assertEquals(30f, calculateCameraZoomRatio(20f, 2f, 0.5f, 30f), 0.0001f)
    }

    @Test
    fun unsupportedZoomRemainsAtOnlyAvailableRatio() {
        assertEquals(1f, calculateCameraZoomRatio(1f, 2f, 1f, 1f), 0.0001f)
    }
}
