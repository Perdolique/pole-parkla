package com.perdolique.poleparkla.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.coroutines.CompletableDeferred
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
}
