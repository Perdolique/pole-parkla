package com.perdolique.poleparkla.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var reportsDirectory: File
    private lateinit var photoStore: PhotoStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        reportsDirectory = File(context.cacheDir, "photo-store-${UUID.randomUUID()}").apply { mkdirs() }
        photoStore = PhotoStore(context, reportsDirectory)
    }

    @After
    fun tearDown() {
        runBlocking { photoStore.clearTemporaryCopies(REPORT_ID) }
        reportsDirectory.deleteRecursively()
    }

    @Test
    fun smallMailCopyPreservesSourceWhileCloudCopyStripsExif() = runBlocking {
        val source = createJpeg(width = 32, height = 32, randomPixels = false)
        val photo = reportPhoto(source)

        val mailCopy = photoStore.prepareEmailCopies(listOf(photo)).single()
        val cloudCopy = photoStore.prepareCloudImage(photo)

        assertNotNull(ExifInterface(source).latLong)
        assertArrayEquals(source.readBytes(), mailCopy.readBytes())
        assertNotNull(ExifInterface(mailCopy).latLong)
        assertNull(ExifInterface(cloudCopy).latLong)
    }

    @Test
    fun largeJpegMailCopyFitsLimitsAndPreservesExif() = runBlocking {
        val source = createJpeg(width = 3000, height = 2000, randomPixels = true)
        assertTrue(source.length() > PhotoStore.MAIL_MAX_BYTES)

        val mailCopy = photoStore.prepareEmailCopies(listOf(reportPhoto(source))).single()
        val bounds = decodeBounds(mailCopy)
        val sourceExif = ExifInterface(source)
        val outputExif = ExifInterface(mailCopy)

        assertTrue(mailCopy.length() <= PhotoStore.MAIL_MAX_BYTES)
        assertTrue(bounds.first > 0 && bounds.second > 0)
        assertTrue(maxOf(bounds.first, bounds.second) <= PhotoStore.MAIL_MAX_LONG_SIDE)
        assertDecodes(mailCopy)
        assertEquals(
            sourceExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
        )
        assertEquals(sourceExif.getAttribute(ExifInterface.TAG_MAKE), outputExif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals(sourceExif.getAttribute(ExifInterface.TAG_MODEL), outputExif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals(
            sourceExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0),
            outputExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0),
        )
        assertNotNull(outputExif.latLong)
    }

    @Test
    fun oversizedPngKeepsEssentialExifInJpegCopy() = runBlocking {
        val source = createPng(width = 3000, height = 100)

        val mailCopy = photoStore.prepareEmailCopies(listOf(reportPhoto(source))).single()
        val bounds = decodeBounds(mailCopy)
        val outputExif = ExifInterface(mailCopy)

        assertTrue(mailCopy.length() <= PhotoStore.MAIL_MAX_BYTES)
        assertTrue(bounds.first > 0 && bounds.second > 0)
        assertTrue(maxOf(bounds.first, bounds.second) <= PhotoStore.MAIL_MAX_LONG_SIDE)
        assertDecodes(mailCopy)
        assertEquals(EXIF_DATE_TIME, outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals(CAMERA_MAKE, outputExif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals(CAMERA_MODEL, outputExif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, outputExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        assertNotNull(outputExif.latLong)
    }

    @Test
    fun oversizedJpegExifFallsBackWithoutARecompressionLoop() = runBlocking {
        val base = createJpeg(width = 32, height = 32, randomPixels = false)
        val exifSegment = FileInputStream(base).use { input ->
            input.readJpegExifSegments().single()
        }
        val source = File(reportsDirectory, "$REPORT_ID/huge-exif.jpg")
        val repeatedSegments = List(
            (PhotoStore.MAIL_MAX_BYTES / exifSegment.size).toInt() + 2,
        ) { exifSegment }
        FileInputStream(base).use { input ->
            FileOutputStream(source).use { output ->
                input.copyJpegWithExifSegments(output, repeatedSegments)
            }
        }
        assertTrue(source.length() > PhotoStore.MAIL_MAX_BYTES)

        val mailCopy = photoStore.prepareEmailCopies(listOf(reportPhoto(source))).single()
        val outputExif = ExifInterface(mailCopy)

        assertTrue(mailCopy.length() <= PhotoStore.MAIL_MAX_BYTES)
        assertDecodes(mailCopy)
        assertEquals(EXIF_DATE_TIME, outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, outputExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        assertNotNull(outputExif.latLong)
    }

    @Test
    fun readsOnlyTheTimeAndLocationActuallyStoredInExif() = runBlocking {
        val source = createJpeg(width = 32, height = 32, randomPixels = false)

        val metadata = photoStore.readExifMetadata(reportPhoto(source).copy(capturedAtEpochMillis = 1L))

        assertEquals(
            LocalDateTime.of(2026, 8, 21, 12, 34, 56)
                .toInstant(ZoneOffset.ofHours(3))
                .toEpochMilli(),
            metadata.capturedAtEpochMillis,
        )
        assertNotNull(metadata.location)
        assertEquals(59.437, metadata.location?.latitude ?: 0.0, 0.0001)
        assertEquals(24.7536, metadata.location?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun cameraUsesCurrentTimeFallbackWithoutPresentingItAsExif() = runBlocking {
        val source = createJpeg(width = 32, height = 32, randomPixels = false, withExif = false)
        val beforeCapture = System.currentTimeMillis()

        val photo = photoStore.cameraPhoto(REPORT_ID, source, isPrimary = true)
        val afterCapture = System.currentTimeMillis()
        val metadata = photoStore.readExifMetadata(photo)

        assertTrue(photo.capturedAtEpochMillis in beforeCapture..afterCapture)
        assertNull(metadata.capturedAtEpochMillis)
        assertNull(metadata.location)
    }

    private fun reportPhoto(source: File) = ReportPhoto(
        id = "photo",
        reportId = REPORT_ID,
        filePath = source.absolutePath,
        source = PhotoSource.CAMERA,
        capturedAtEpochMillis = 1L,
        isPrimary = true,
    )

    private fun createJpeg(
        width: Int,
        height: Int,
        randomPixels: Boolean,
        withExif: Boolean = true,
    ): File {
        val file = File(reportsDirectory, "$REPORT_ID/photo.jpg")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (randomPixels) fillWithRandomPixels(bitmap) else bitmap.eraseColor(Color.GREEN)
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        if (withExif) addExif(file, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        return file
    }

    private fun createPng(width: Int, height: Int): File {
        val file = File(reportsDirectory, "$REPORT_ID/photo.png")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        addExif(file, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        return file
    }

    private fun addExif(file: File, orientation: Int) {
        ExifInterface(file).apply {
            setLatLong(59.437, 24.7536)
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, EXIF_DATE_TIME)
            setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+03:00")
            setAttribute(ExifInterface.TAG_MAKE, CAMERA_MAKE)
            setAttribute(ExifInterface.TAG_MODEL, CAMERA_MODEL)
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
    }

    private fun fillWithRandomPixels(bitmap: Bitmap) {
        val row = IntArray(bitmap.width)
        var state = 0x12345678
        repeat(bitmap.height) { y ->
            row.indices.forEach { x ->
                state = state * 1664525 + 1013904223
                row[x] = Color.rgb(state ushr 16 and 0xff, state ushr 8 and 0xff, state and 0xff)
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }

    private fun decodeBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    private fun assertDecodes(file: File) {
        requireNotNull(BitmapFactory.decodeFile(file.absolutePath)).recycle()
    }

    private companion object {
        const val REPORT_ID = "report"
        const val EXIF_DATE_TIME = "2026:08:21 12:34:56"
        const val CAMERA_MAKE = "Pole Parkla Test"
        const val CAMERA_MODEL = "Evidence Camera"
    }
}
