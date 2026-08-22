package com.perdolique.poleparkla.service

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayReviewLauncherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun requestsAndLaunchesReviewWithFakeManager() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = RecordingReviewManager(FakeReviewManager(context))

        PlayReviewLauncher(manager).launch(composeRule.activity)

        assertEquals(1, manager.requestCount)
        assertEquals(1, manager.launchCount)
    }

    @Test
    fun requestFailureDoesNotLaunchOrEscapeToTheUi() = runBlocking {
        val manager = FailingReviewManager()

        PlayReviewLauncher(manager).launch(composeRule.activity)

        assertEquals(1, manager.requestCount)
        assertEquals(0, manager.launchCount)
    }

    private class RecordingReviewManager(
        private val delegate: ReviewManager,
    ) : ReviewManager {
        var requestCount = 0
            private set
        var launchCount = 0
            private set

        override fun requestReviewFlow(): Task<ReviewInfo> {
            requestCount++
            return delegate.requestReviewFlow()
        }

        override fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo): Task<Void> {
            launchCount++
            return delegate.launchReviewFlow(activity, reviewInfo)
        }
    }

    private class FailingReviewManager : ReviewManager {
        var requestCount = 0
            private set
        var launchCount = 0
            private set

        override fun requestReviewFlow(): Task<ReviewInfo> {
            requestCount++
            return Tasks.forException(IllegalStateException("Play Store unavailable"))
        }

        override fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo): Task<Void> {
            launchCount++
            return Tasks.forResult(null)
        }
    }
}
