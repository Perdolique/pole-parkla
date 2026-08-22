package com.perdolique.poleparkla.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class PlayReviewLauncher(private val manager: ReviewManager) {
    constructor(context: Context) : this(ReviewManagerFactory.create(context.applicationContext))

    suspend fun launch(activity: Activity) {
        try {
            val reviewInfo = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, reviewInfo).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Unable to launch the Google Play review flow", error)
        }
    }

    private companion object {
        const val TAG = "PlayReviewLauncher"
    }
}
