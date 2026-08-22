package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecognitionStateTest {
    @Test
    fun `completion is current only for the persisted photo set`() {
        val first = photo("first")
        val second = photo("second")
        val fingerprint = listOf(first, second).localRecognitionFingerprint()

        val completed = sampleReport().copy(
            photos = listOf(second, first),
            localRecognitionFingerprint = fingerprint,
        )

        assertTrue(completed.hasCurrentLocalRecognition())
        assertFalse(completed.copy(localRecognitionFingerprint = "").hasCurrentLocalRecognition())
        assertFalse(completed.copy(photos = listOf(first)).hasCurrentLocalRecognition())
    }

    private fun photo(id: String) = ReportPhoto(
        id = id,
        reportId = "report",
        filePath = "/tmp/$id.jpg",
        source = PhotoSource.CAMERA,
        capturedAtEpochMillis = 1,
        isPrimary = id == "first",
    )
}
