package com.perdolique.poleparkla.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.ui.components.PpDialog

@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    PpDialog(
        title = stringResource(R.string.privacy_policy),
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.close),
        onConfirm = onDismiss,
        confirmTestTag = "privacy_policy_close",
        modifier = Modifier.testTag("privacy_policy_dialog"),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("privacy_policy_content"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PrivacyPolicySection(
                    title = stringResource(R.string.privacy_policy_device_title),
                    body = stringResource(R.string.privacy_policy_device_body),
                )
                PrivacyPolicySection(
                    title = stringResource(R.string.privacy_policy_location_title),
                    body = stringResource(R.string.privacy_policy_location_body),
                )
                PrivacyPolicySection(
                    title = stringResource(R.string.privacy_policy_cloud_title),
                    body = stringResource(R.string.privacy_policy_cloud_body),
                )
                PrivacyPolicySection(
                    title = stringResource(R.string.privacy_policy_mail_title),
                    body = stringResource(R.string.privacy_policy_mail_body),
                )
            }
        },
    )
}

@Composable
private fun PrivacyPolicySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
