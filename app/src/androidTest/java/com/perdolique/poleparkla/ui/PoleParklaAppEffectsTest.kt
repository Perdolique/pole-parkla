package com.perdolique.poleparkla.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PoleParklaAppEffectsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requestInAppReviewEffectInvokesCurrentCallback() {
        val effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 1)
        var callbackVersion = 1
        var invokedVersion = 0
        composeRule.setContent {
            CollectUiEffects(effects) { invokedVersion = callbackVersion }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            callbackVersion = 2
            assertTrue(effects.tryEmit(UiEffect.RequestInAppReview))
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) { invokedVersion == 2 }
        assertEquals(2, invokedVersion)
    }
}
