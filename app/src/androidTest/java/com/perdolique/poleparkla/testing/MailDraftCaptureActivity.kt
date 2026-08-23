package com.perdolique.poleparkla.testing

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MailDraftCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sendBroadcast(
            Intent(ACTION_DRAFT_CAPTURED)
                .setPackage(TARGET_PACKAGE)
                .putExtra(EXTRA_DRAFT_INTENT, Intent(intent)),
        )
        finish()
    }

    companion object {
        const val ACTION_DRAFT_CAPTURED =
            "com.perdolique.poleparkla.testing.action.DRAFT_CAPTURED"
        const val EXTRA_DRAFT_INTENT = "draft_intent"
        private const val TARGET_PACKAGE = "com.perdolique.poleparkla"
    }
}
