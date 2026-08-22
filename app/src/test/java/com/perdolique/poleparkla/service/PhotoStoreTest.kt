package com.perdolique.poleparkla.service

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoStoreTest {
    @Test
    fun `bounded copy preserves an accepted image`() {
        val source = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(source).copyToWithLimit(output, maxBytes = 4)

        assertEquals(4L, copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun `bounded copy rejects data beyond the limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(ByteArray(5)).copyToWithLimit(ByteArrayOutputStream(), maxBytes = 4)
        }
    }

    @Test
    fun `sampling stays at or above the requested decode size`() {
        assertEquals(2, calculateInSampleSize(width = 5120, height = 5120, maxLongSide = 2560))
        assertEquals(2, calculateInSampleSize(width = 6000, height = 4000, maxLongSide = 2560))
        assertEquals(1, calculateInSampleSize(width = 4032, height = 3024, maxLongSide = 3200))
        assertEquals(2, calculateInSampleSize(width = 1280, height = 964, maxLongSide = 480))
    }

    @Test
    fun `jpeg exif segment survives attachment encoding`() {
        val exifSegment = byteArrayOf(
            0xff.toByte(),
            0xe1.toByte(),
            0x00,
            0x0a,
            'E'.code.toByte(),
            'x'.code.toByte(),
            'i'.code.toByte(),
            'f'.code.toByte(),
            0x00,
            0x00,
            0x01,
            0x02,
        )
        val encodedJpeg = byteArrayOf(
            0xff.toByte(),
            0xd8.toByte(),
            0xff.toByte(),
            0xe0.toByte(),
            0x00,
            0x02,
            0xff.toByte(),
            0xd9.toByte(),
        )

        val output = ByteArrayOutputStream()
        ByteArrayInputStream(encodedJpeg).copyJpegWithExifSegments(output, listOf(exifSegment))
        val withExif = output.toByteArray()

        val extracted = ByteArrayInputStream(withExif).readJpegExifSegments()
        assertEquals(1, extracted.size)
        assertArrayEquals(exifSegment, extracted.single())
    }

    @Test
    fun `jpeg exif reader stops before retained metadata reaches the attachment limit`() {
        val jpeg = byteArrayOf(
            0xff.toByte(),
            0xd8.toByte(),
            0xff.toByte(),
            0xe1.toByte(),
            0x00,
            0x0a,
            'E'.code.toByte(),
            'x'.code.toByte(),
            'i'.code.toByte(),
            'f'.code.toByte(),
            0x00,
            0x00,
            0x01,
            0x02,
            0xff.toByte(),
            0xd9.toByte(),
        )

        val result = ByteArrayInputStream(jpeg).readJpegExifSegmentsUpTo(maxTotalBytes = 12)

        assertEquals(true, result.exceededLimit)
        assertEquals(emptyList<ByteArray>(), result.segments)
    }
}
