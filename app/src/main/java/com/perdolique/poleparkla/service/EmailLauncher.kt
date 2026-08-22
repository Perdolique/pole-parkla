package com.perdolique.poleparkla.service

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.perdolique.poleparkla.model.Report
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MailApp(
    val component: ComponentName,
    val label: String,
)

class EmailLauncher(private val context: Context) {
    suspend fun compatibleApps(): List<MailApp> = withContext(Dispatchers.IO) {
        val mailtoPackages = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SENDTO, "mailto:".toUri()),
            PackageManager.MATCH_DEFAULT_ONLY,
        ).mapTo(mutableSetOf()) { it.activityInfo.packageName }
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/*" }
        context.packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName in mailtoPackages }
            .map { info ->
                MailApp(
                    component = ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                    label = info.loadLabel(context.packageManager).toString(),
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    fun findSavedComponent(flattened: String, apps: List<MailApp>): ComponentName? {
        if (flattened.isBlank()) return null
        val component = ComponentName.unflattenFromString(flattened) ?: return null
        return apps.firstOrNull { it.component == component }?.component
    }

    fun buildIntent(report: Report, files: List<File>, component: ComponentName): Intent {
        val uris = ArrayList(files.map(::fileUri))
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            this.component = component
            putExtra(Intent.EXTRA_EMAIL, arrayOf(report.recipient))
            putExtra(Intent.EXTRA_SUBJECT, report.subject)
            putExtra(Intent.EXTRA_TEXT, report.body)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = uris.firstOrNull()?.let { first ->
                ClipData.newUri(context.contentResolver, "Pole parkla photos", first).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        file,
    )
}
