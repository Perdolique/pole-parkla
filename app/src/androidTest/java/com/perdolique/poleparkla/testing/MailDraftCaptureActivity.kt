package com.perdolique.poleparkla.testing

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

class MailDraftCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The mail activity can run outside the instrumented app process.
        @Suppress("DEPRECATION")
        val testPackage = packageManager.getPackageInfo(packageName, PackageManager.GET_INSTRUMENTATION)
        val targetPackage = requireNotNull(testPackage.instrumentation?.singleOrNull()?.targetPackage)
        sendBroadcast(
            Intent(ACTION_DRAFT_CAPTURED)
                .setPackage(targetPackage)
                .putExtra(EXTRA_DRAFT_INTENT, Intent(intent)),
        )
        finish()
    }

    companion object {
        const val ACTION_DRAFT_CAPTURED =
            "com.perdolique.poleparkla.testing.action.DRAFT_CAPTURED"
        const val EXTRA_DRAFT_INTENT = "draft_intent"
    }
}
