package com.perdolique.poleparkla.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import com.perdolique.poleparkla.ui.screens.PlateEvidenceBitmap
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManagedBitmapTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun changingTheKeyDropsTheOldBitmapBeforeLoadingTheReplacement() {
        val bitmapKey = mutableStateOf("first")
        val visible = mutableStateOf(true)
        val first = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val loadSecond = CompletableDeferred<Unit>()

        composeRule.setContent {
            if (visible.value) {
                val currentKey = bitmapKey.value
                val bitmap by rememberManagedBitmap(currentKey) {
                    if (currentKey == "first") first else loadSecond.await().let { second }
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = currentKey,
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("first").assertExists()
        assertFalse(first.isRecycled)

        composeRule.runOnIdle { bitmapKey.value = "second" }
        composeRule.waitUntil(5_000) { first.isRecycled }
        composeRule.onNodeWithContentDescription("first").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("second").assertDoesNotExist()

        loadSecond.complete(Unit)
        composeRule.onNodeWithContentDescription("second").assertExists()
        assertFalse(second.isRecycled)

        composeRule.runOnIdle { visible.value = false }
        composeRule.waitUntil(5_000) { second.isRecycled }
        assertTrue(second.isRecycled)
    }

    @Test
    fun delayedCropShowsOnlyTheNeutralLoadingStateBeforeOneSuccessfulImage() {
        val crop = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val releaseCrop = CompletableDeferred<Unit>()
        var loadCount = 0

        composeRule.setContent {
            val loadState by rememberManagedBitmapLoadState("crop") {
                loadCount += 1
                releaseCrop.await()
                crop
            }
            PlateEvidenceBitmap(
                loadState = loadState,
                contentDescription = "crop",
                modifier = Modifier.size(80.dp),
                fallback = { fallbackModifier -> Box(fallbackModifier) },
            )
        }

        composeRule.onNodeWithTag("plate_evidence_loading").assertExists()
        composeRule.onNodeWithContentDescription("crop").assertExists()
        composeRule.onNodeWithTag("plate_evidence_fallback").assertDoesNotExist()
        composeRule.onNodeWithTag("plate_evidence_crop").assertDoesNotExist()

        releaseCrop.complete(Unit)
        composeRule.onAllNodesWithTag("plate_evidence_crop").assertCountEquals(1)
        composeRule.onNodeWithTag("plate_evidence_loading").assertDoesNotExist()
        composeRule.onNodeWithTag("plate_evidence_fallback").assertDoesNotExist()
        assertEquals(1, loadCount)
    }

    @Test
    fun confirmedCropFailureEnablesTheFullPhotoFallback() {
        composeRule.setContent {
            val loadState by rememberManagedBitmapLoadState("failed-crop") {
                error("broken crop")
            }
            PlateEvidenceBitmap(
                loadState = loadState,
                contentDescription = "crop",
                modifier = Modifier.size(80.dp),
                fallback = { fallbackModifier -> Box(fallbackModifier) },
            )
        }

        composeRule.onNodeWithTag("plate_evidence_fallback").assertExists()
        composeRule.onNodeWithTag("plate_evidence_loading").assertDoesNotExist()
        composeRule.onNodeWithTag("plate_evidence_crop").assertDoesNotExist()
    }

    @Test
    fun retainedCropSurvivesConsumerChangesAndRecyclesOnKeyChangeAndOwnerExit() {
        val ownerVisible = mutableStateOf(true)
        val evidenceKey = mutableStateOf("first")
        val largeConsumer = mutableStateOf(false)
        val first = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        var loadCount = 0

        composeRule.setContent {
            if (ownerVisible.value) {
                val currentKey = evidenceKey.value
                val loadState by rememberManagedBitmapLoadState(currentKey) {
                    loadCount += 1
                    if (currentKey == "first") first else second
                }
                PlateEvidenceBitmap(
                    loadState = loadState,
                    contentDescription = if (largeConsumer.value) "large crop" else "small crop",
                    modifier = Modifier.size(if (largeConsumer.value) 220.dp else 72.dp),
                    fallback = { fallbackModifier -> Box(fallbackModifier) },
                )
            }
        }

        composeRule.onNodeWithContentDescription("small crop").assertExists()
        composeRule.runOnIdle { largeConsumer.value = true }
        composeRule.onNodeWithContentDescription("large crop").assertExists()
        composeRule.runOnIdle { largeConsumer.value = false }
        composeRule.onNodeWithContentDescription("small crop").assertExists()
        assertEquals(1, loadCount)
        assertFalse(first.isRecycled)

        composeRule.runOnIdle { evidenceKey.value = "second" }
        composeRule.waitUntil(5_000) { first.isRecycled }
        composeRule.onNodeWithContentDescription("small crop").assertExists()
        assertEquals(2, loadCount)
        assertFalse(second.isRecycled)

        composeRule.runOnIdle { ownerVisible.value = false }
        composeRule.waitUntil(5_000) { second.isRecycled }
        assertTrue(second.isRecycled)
    }
}
