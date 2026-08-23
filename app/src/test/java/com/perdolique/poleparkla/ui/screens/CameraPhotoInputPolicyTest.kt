package com.perdolique.poleparkla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPhotoInputPolicyTest {
    @Test
    fun `gallery selection capacity matches remaining report slots`() {
        assertEquals(3, remainingPhotoCapacity(photoCount = 0))
        assertEquals(2, remainingPhotoCapacity(photoCount = 1))
        assertEquals(1, remainingPhotoCapacity(photoCount = 2))
        assertEquals(0, remainingPhotoCapacity(photoCount = 3))
    }

    @Test
    fun `gallery selection capacity is bounded for stale counts`() {
        assertEquals(3, remainingPhotoCapacity(photoCount = -1))
        assertEquals(0, remainingPhotoCapacity(photoCount = 4))
    }
}
