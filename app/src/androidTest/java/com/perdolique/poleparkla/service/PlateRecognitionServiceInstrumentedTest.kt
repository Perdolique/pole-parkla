package com.perdolique.poleparkla.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlateRecognitionServiceInstrumentedTest {
    @Test
    fun bundledDetectorAndRecognizerReadPlateFromFullPhoto() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDirectory = File(context.cacheDir, "plate-recognition-test").apply {
            deleteRecursively()
            check(mkdirs())
        }
        try {
            val photoFile = File(testDirectory, "synthetic-car.png")
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("synthetic_plate_test_image.png").use { input ->
                photoFile.outputStream().use(input::copyTo)
            }
            val photoStore = PhotoStore(context, File(testDirectory, "reports"))
            val service = PlateRecognitionService(context, photoStore)

            val result = service.recognize(
                listOf(
                    ReportPhoto(
                        id = "photo",
                        reportId = "report",
                        filePath = photoFile.absolutePath,
                        source = PhotoSource.GALLERY,
                        capturedAtEpochMillis = 1L,
                        isPrimary = true,
                    ),
                ),
            )

            assertEquals(listOf("123 ABC"), result.plateCandidates)
        } finally {
            testDirectory.deleteRecursively()
        }
    }
}
