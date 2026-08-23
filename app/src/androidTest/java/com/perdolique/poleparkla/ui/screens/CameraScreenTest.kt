package com.perdolique.poleparkla.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.ImageCapture
import androidx.camera.core.ZoomState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.PoleParklaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deniedAndUnavailableCameraKeepGalleryFallback() {
        val cameraPresent = mutableStateOf(true)
        composeRule.setContent {
            PoleParklaTheme {
                CameraUnavailable(
                    cameraPresent = cameraPresent.value,
                    galleryEnabled = true,
                    onGrantCamera = {},
                    onGallery = {},
                )
            }
        }

        composeRule.onNodeWithTag("camera_permission").assertExists().assertIsEnabled()
        composeRule.onNodeWithTag("camera_gallery_empty").assertExists().assertIsEnabled()

        composeRule.runOnIdle { cameraPresent.value = false }

        composeRule.onNodeWithTag("camera_permission").assertDoesNotExist()
        composeRule.onNodeWithTag("camera_gallery_empty").assertExists().assertIsEnabled()
    }

    @Test
    fun unavailableCameraDisablesGalleryWhenNoPhotoSlotRemains() {
        composeRule.setContent {
            PoleParklaTheme {
                CameraUnavailable(
                    cameraPresent = false,
                    galleryEnabled = false,
                    onGrantCamera = {},
                    onGallery = {},
                )
            }
        }

        composeRule.onNodeWithTag("camera_gallery_empty").assertIsNotEnabled()
    }

    @Test
    fun galleryOnlyCaptureAndThreePhotoLimitHaveDistinctControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photoStore = PhotoStore(context, File(context.cacheDir, "camera-screen-test"))
        val cameraAvailable = mutableStateOf(false)
        val photos = mutableStateOf(emptyList<ReportPhoto>())
        var removedPhotoId: String? = null
        composeRule.setContent {
            PoleParklaTheme {
                Box(Modifier.fillMaxSize()) {
                    CameraTrayWithDeletion(
                        reportId = "report",
                        photos = photos.value,
                        photoStore = photoStore,
                        cameraAvailable = cameraAvailable.value,
                        busy = false,
                        onGallery = {},
                        onCapture = {},
                        onRemoveConfirmed = { removed ->
                            removedPhotoId = removed.id
                            photos.value = photos.value.filterNot { it.id == removed.id }
                        },
                        onReview = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("camera_gallery").assertExists().assertIsEnabled()
        composeRule.onNodeWithTag("camera_capture").assertDoesNotExist()
        composeRule.onNodeWithTag("camera_review").assertDoesNotExist()

        composeRule.runOnIdle { cameraAvailable.value = true }
        composeRule.onNodeWithTag("camera_capture").assertExists().assertIsEnabled()
        assertCaptureIsCentered()

        composeRule.runOnIdle { photos.value = threePhotos(context.cacheDir) }
        composeRule.onNodeWithTag("camera_gallery").assertIsNotEnabled()
        composeRule.onNodeWithTag("camera_capture").assertIsNotEnabled()
        composeRule.onNodeWithTag("camera_review").assertExists().assertIsEnabled()
        composeRule.onNodeWithTag("camera_photo_limit_hint")
            .assertTextEquals(context.getString(R.string.photo_limit_replace_hint))
        composeRule.onNodeWithTag("camera_photo_delete_photo-1").assertExists().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertNull(removedPhotoId) }
        composeRule.onNodeWithTag("camera_gallery").assertIsNotEnabled()
        composeRule.onNodeWithTag("camera_capture").assertIsNotEnabled()
        composeRule.onNodeWithTag("camera_delete_confirm").performClick()
        composeRule.runOnIdle { assertEquals("photo-1", removedPhotoId) }
        composeRule.onNodeWithTag("camera_gallery").assertIsEnabled()
        composeRule.onNodeWithTag("camera_capture").assertIsEnabled()
        composeRule.onNodeWithTag("camera_photo_limit_hint").assertDoesNotExist()
        assertCaptureIsCentered()
    }

    @Test
    fun pinchGestureRequestsZoomShowsIndicatorAndKeepsOverlayControlsClickable() {
        val zoomRatio = mutableFloatStateOf(1f)
        val controlClicked = mutableStateOf(false)
        composeRule.setContent {
            PoleParklaTheme {
                CameraZoomOverlay(
                    currentZoomRatio = zoomRatio.floatValue,
                    minZoomRatio = 0.5f,
                    maxZoomRatio = 3f,
                    onZoomRatioChange = { zoomRatio.floatValue = it },
                    modifier = Modifier.fillMaxSize().testTag("zoom_surface"),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                            .clickable { controlClicked.value = true }
                            .testTag("preview_control"),
                    )
                }
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("zoom_surface").performTouchInput {
            pinch(
                start0 = center - Offset(20f, 0f),
                end0 = centerLeft,
                start1 = center + Offset(20f, 0f),
                end1 = centerRight,
                durationMillis = 200L,
            )
        }

        composeRule.runOnIdle { assertTrue(zoomRatio.floatValue > 1f) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("camera_zoom_indicator").assertExists()
        composeRule.onNodeWithTag("preview_control").performClick()
        composeRule.runOnIdle { assertTrue(controlClicked.value) }

        composeRule.mainClock.advanceTimeBy(801L)
        composeRule.onNodeWithTag("camera_zoom_indicator").assertDoesNotExist()
    }

    @Test
    fun accessibilityActionsAdjustZoomInBothDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val zoomRatio = mutableFloatStateOf(1f)
        composeRule.setContent {
            PoleParklaTheme {
                CameraZoomOverlay(
                    currentZoomRatio = zoomRatio.floatValue,
                    minZoomRatio = 0.5f,
                    maxZoomRatio = 3f,
                    onZoomRatioChange = { zoomRatio.floatValue = it },
                    modifier = Modifier.fillMaxSize().testTag("zoom_surface"),
                ) {}
            }
        }

        val zoomInAction = composeRule.onNodeWithTag("zoom_surface")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == context.getString(R.string.camera_zoom_in) }
        composeRule.runOnIdle { assertTrue(zoomInAction.action()) }
        composeRule.runOnIdle { assertEquals(1.25f, zoomRatio.floatValue, 0.0001f) }

        val zoomOutAction = composeRule.onNodeWithTag("zoom_surface")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == context.getString(R.string.camera_zoom_out) }
        composeRule.runOnIdle { assertTrue(zoomOutAction.action()) }
        composeRule.runOnIdle { assertEquals(1f, zoomRatio.floatValue, 0.0001f) }
    }

    @Test
    fun realCameraPinchUpdatesCameraXZoomState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.CAMERA,
        )
        val cameraAvailable = mutableStateOf(false)
        val zoomState = mutableStateOf<ZoomState?>(null)
        val imageCapture = ImageCapture.Builder().build()
        composeRule.setContent {
            PoleParklaTheme {
                CameraPreview(
                    imageCapture = imageCapture,
                    onAvailabilityChanged = { cameraAvailable.value = it },
                    onZoomStateChanged = { zoomState.value = it },
                    modifier = Modifier.fillMaxSize().testTag("real_camera_zoom_surface"),
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) { cameraAvailable.value && zoomState.value != null }
        val initialState = requireNotNull(zoomState.value)
        val initialZoomRatio = initialState.zoomRatio
        val canZoomOut = initialState.minZoomRatio < initialZoomRatio
        val canZoomIn = initialState.maxZoomRatio > initialZoomRatio
        assumeTrue(canZoomOut || canZoomIn)

        if (canZoomOut) {
            composeRule.onNodeWithTag("real_camera_zoom_surface").performTouchInput {
                pinch(
                    start0 = centerLeft + Offset(20f, 0f),
                    end0 = center - Offset(20f, 0f),
                    start1 = centerRight - Offset(20f, 0f),
                    end1 = center + Offset(20f, 0f),
                    durationMillis = 300L,
                )
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                requireNotNull(zoomState.value).zoomRatio < initialZoomRatio
            }
            assertEquals(
                initialState.minZoomRatio,
                requireNotNull(zoomState.value).zoomRatio,
                0.02f,
            )
        }

        if (canZoomIn) {
            composeRule.onNodeWithTag("real_camera_zoom_surface").performTouchInput {
                pinch(
                    start0 = center - Offset(20f, 0f),
                    end0 = centerLeft + Offset(20f, 0f),
                    start1 = center + Offset(20f, 0f),
                    end1 = centerRight - Offset(20f, 0f),
                    durationMillis = 300L,
                )
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                requireNotNull(zoomState.value).zoomRatio > initialZoomRatio
            }
            assertTrue(requireNotNull(zoomState.value).zoomRatio > initialZoomRatio)
        }
    }

    private fun assertCaptureIsCentered() {
        val controls = composeRule.onNodeWithTag("camera_controls").getUnclippedBoundsInRoot()
        val capture = composeRule.onNodeWithTag("camera_capture").getUnclippedBoundsInRoot()
        val controlsCenter = (controls.left.value + controls.right.value) / 2f
        val captureCenter = (capture.left.value + capture.right.value) / 2f

        assertEquals(controlsCenter, captureCenter, 0.5f)
    }

    private fun threePhotos(directory: File): List<ReportPhoto> = List(3) { index ->
        ReportPhoto(
            id = "photo-$index",
            reportId = "report",
            filePath = File(directory, "missing-$index.jpg").absolutePath,
            source = PhotoSource.CAMERA,
            capturedAtEpochMillis = index.toLong(),
            isPrimary = index == 0,
        )
    }
}
