package com.perdolique.poleparkla.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.ui.components.LanguageSelector
import com.perdolique.poleparkla.ui.components.PoleParklaSystemBars
import com.perdolique.poleparkla.ui.components.PpBadge
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpCard
import com.perdolique.poleparkla.ui.components.PpChoiceRow
import com.perdolique.poleparkla.ui.components.PpDialog
import com.perdolique.poleparkla.ui.components.PpField
import com.perdolique.poleparkla.ui.components.PpIconButton
import com.perdolique.poleparkla.ui.components.PpIcons
import com.perdolique.poleparkla.ui.components.PpSheet
import com.perdolique.poleparkla.ui.components.PpSheetHeader
import com.perdolique.poleparkla.ui.components.PpTopBar
import com.perdolique.poleparkla.ui.components.PpTextButton
import java.net.URI

private const val PROJECT_URL = "https://github.com/Perdolique/pole-parkla"

private enum class SettingsSection {
    LANGUAGE,
    PROFILE,
    RECIPIENT,
    RECOGNITION,
    TEMPLATES,
    PRIVACY,
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    cloudConfigured: Boolean,
    templates: List<CustomViolationTemplate>,
    appVersion: String,
    onBack: () -> Unit,
    onRateApp: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onSave: (String, ReporterProfile, String, String, CloudProvider, String, () -> Unit) -> Unit,
    onClearToken: () -> Unit,
    onUpsertTemplate: (String?, String, String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val context = LocalContext.current
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    var editingTemplate by remember { mutableStateOf<CustomViolationTemplate?>(null) }
    var templateToDelete by remember { mutableStateOf<CustomViolationTemplate?>(null) }
    var addingTemplate by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }

    PoleParklaSystemBars()
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        PpTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsCategoryRow(
                    title = stringResource(R.string.language),
                    summary = languageSummary(settings.languageTag),
                    icon = PpIcons.Language,
                    onClick = { section = SettingsSection.LANGUAGE },
                    modifier = Modifier.testTag("settings_category_language"),
                )
                SettingsCategoryRow(
                    title = stringResource(R.string.profile),
                    summary = settings.profile.name.ifBlank { stringResource(R.string.not_configured) },
                    icon = PpIcons.Person,
                    onClick = { section = SettingsSection.PROFILE },
                    modifier = Modifier.testTag("settings_category_profile"),
                )
                SettingsCategoryRow(
                    title = stringResource(R.string.default_recipient),
                    summary = settings.defaultRecipient,
                    icon = PpIcons.Email,
                    onClick = { section = SettingsSection.RECIPIENT },
                    modifier = Modifier.testTag("settings_category_recipient"),
                )
                SettingsCategoryRow(
                    title = stringResource(R.string.recognition_settings),
                    summary = if (!cloudConfigured) {
                        stringResource(R.string.cloud_disabled)
                    } else {
                        providerLabel(settings.cloudProvider)
                    },
                    icon = PpIcons.Recognition,
                    onClick = { section = SettingsSection.RECOGNITION },
                    modifier = Modifier.testTag("settings_category_recognition"),
                )
                SettingsCategoryRow(
                    title = stringResource(R.string.custom_templates),
                    summary = stringResource(R.string.template_count, templates.size),
                    icon = PpIcons.Flag,
                    onClick = { section = SettingsSection.TEMPLATES },
                    modifier = Modifier.testTag("settings_category_templates"),
                )
                SettingsCategoryRow(
                    title = stringResource(R.string.privacy_and_data),
                    summary = stringResource(R.string.local_only),
                    icon = PpIcons.Privacy,
                    onClick = { section = SettingsSection.PRIVACY },
                    modifier = Modifier.testTag("settings_category_privacy"),
                )
                Spacer(Modifier.size(10.dp))
                PpButton(
                    text = stringResource(R.string.rate_app),
                    onClick = onRateApp,
                    style = PpButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_rate_app"),
                )
                PpTextButton(
                    text = stringResource(R.string.project_source),
                    onClick = { context.openProjectPage(PROJECT_URL) },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("settings_project_source"),
                )
                PpTextButton(
                    text = stringResource(R.string.project_feedback),
                    onClick = { context.openProjectPage("$PROJECT_URL/issues") },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("settings_project_feedback"),
                )
                Text(
                    text = stringResource(R.string.app_version, appVersion),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 10.dp)
                        .testTag("settings_app_version"),
                )
            }
        }
    }

    when (section) {
        SettingsSection.LANGUAGE -> LanguageSettingsSheet(
            current = settings.languageTag,
            onClose = { section = null },
            onSave = { tag ->
                onLanguageSelected(tag)
                section = null
            },
        )
        SettingsSection.PROFILE -> ProfileSettingsSheet(
            profile = settings.profile,
            onClose = { section = null },
            onSave = { profile ->
                onSave(
                    settings.languageTag,
                    profile,
                    settings.defaultRecipient,
                    settings.workerUrl,
                    settings.cloudProvider,
                    "",
                ) { section = null }
            },
        )
        SettingsSection.RECIPIENT -> RecipientSettingsSheet(
            recipient = settings.defaultRecipient,
            onClose = { section = null },
            onSave = { recipient ->
                onSave(
                    settings.languageTag,
                    settings.profile,
                    recipient,
                    settings.workerUrl,
                    settings.cloudProvider,
                    "",
                ) { section = null }
            },
        )
        SettingsSection.RECOGNITION -> RecognitionSettingsSheet(
            settings = settings,
            cloudConfigured = cloudConfigured,
            onClose = { section = null },
            onClearToken = onClearToken,
            onSave = { workerUrl, provider, token ->
                onSave(
                    settings.languageTag,
                    settings.profile,
                    settings.defaultRecipient,
                    workerUrl,
                    provider,
                    token,
                ) { section = null }
            },
        )
        SettingsSection.TEMPLATES -> TemplateSettingsSheet(
            templates = templates,
            onClose = { section = null },
            onAdd = { addingTemplate = true },
            onEdit = { editingTemplate = it },
            onDelete = { templateToDelete = it },
        )
        SettingsSection.PRIVACY -> PrivacySettingsSheet(
            onClose = { section = null },
            onOpenPrivacyPolicy = { showPrivacyPolicy = true },
            onDeleteAll = { confirmDeleteAll = true },
        )
        null -> Unit
    }

    if (addingTemplate || editingTemplate != null) {
        TemplateDialog(
            template = editingTemplate,
            onDismiss = {
                addingTemplate = false
                editingTemplate = null
            },
            onSave = { displayName, description ->
                onUpsertTemplate(editingTemplate?.id, displayName, description)
                addingTemplate = false
                editingTemplate = null
            },
        )
    }
    templateToDelete?.let { template ->
        PpDialog(
            title = stringResource(R.string.delete_template_title, template.displayName),
            onDismissRequest = { templateToDelete = null },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                onDeleteTemplate(template.id)
                templateToDelete = null
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
            confirmTestTag = "template_confirm_delete",
            content = { Text(stringResource(R.string.delete_template_text)) },
        )
    }
    if (confirmDeleteAll) {
        PpDialog(
            title = stringResource(R.string.delete_all_data_title),
            onDismissRequest = { confirmDeleteAll = false },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                confirmDeleteAll = false
                section = null
                onDeleteAll()
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
            confirmTestTag = "settings_confirm_delete_all",
            content = { Text(stringResource(R.string.delete_all_data_text)) },
        )
    }
    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }
}

private fun Context.openProjectPage(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (error: ActivityNotFoundException) {
        Log.w("PoleParkla", "Could not open project page", error)
        Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PpChoiceRow(
        title = title,
        supportingText = summary,
        leadingIcon = icon,
        selected = false,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun LanguageSettingsSheet(current: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var language by rememberSaveable(current) { mutableStateOf(current) }
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.language), dismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            LanguageSelector(language) { language = it }
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(language) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp)
                    .testTag("settings_language_save"),
            )
        }
    }
}

@Composable
private fun ProfileSettingsSheet(
    profile: ReporterProfile,
    onClose: () -> Unit,
    onSave: (ReporterProfile) -> Unit,
) {
    var name by rememberSaveable(profile.name) { mutableStateOf(profile.name) }
    var phone by rememberSaveable(profile.phone) { mutableStateOf(profile.phone) }
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.profile), dismiss)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PpField(
                name,
                { name = it },
                stringResource(R.string.name),
                modifier = Modifier.testTag("settings_profile_name"),
            )
            PpField(
                phone,
                { phone = it },
                stringResource(R.string.phone),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.testTag("settings_profile_phone"),
            )
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(ReporterProfile(name, phone)) },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 20.dp)
                    .testTag("settings_profile_save"),
            )
        }
    }
}

@Composable
private fun RecipientSettingsSheet(recipient: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(recipient) { mutableStateOf(recipient) }
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.default_recipient), dismiss)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            PpField(
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.default_recipient),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = value.isBlank(),
                modifier = Modifier.testTag("settings_recipient"),
            )
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(value) },
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp)
                    .testTag("settings_recipient_save"),
            )
        }
    }
}

@Composable
private fun RecognitionSettingsSheet(
    settings: AppSettings,
    cloudConfigured: Boolean,
    onClose: () -> Unit,
    onClearToken: () -> Unit,
    onSave: (String, CloudProvider, String) -> Unit,
) {
    var workerUrl by rememberSaveable(settings.workerUrl) { mutableStateOf(settings.workerUrl) }
    var provider by rememberSaveable(settings.cloudProvider) { mutableStateOf(settings.cloudProvider) }
    var token by rememberSaveable(settings.workerUrl) { mutableStateOf("") }
    val normalizedWorkerUrl = workerUrl.trim()
    val validWorkerUrl = normalizedWorkerUrl.isBlank() || isValidHttpsUrl(normalizedWorkerUrl)
    val sameWorkerUrl = normalizedWorkerUrl.trimEnd('/') == settings.workerUrl.trim().trimEnd('/')
    val existingTokenAvailable = sameWorkerUrl && cloudConfigured
    val cloudSettingsValid = normalizedWorkerUrl.isBlank() ||
        (validWorkerUrl && (token.isNotBlank() || existingTokenAvailable))
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.recognition_settings), dismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.on_device_recognition), style = MaterialTheme.typography.titleMedium)
            PpBadge(
                text = stringResource(R.string.plate_model_ready),
                isComplete = true,
                modifier = Modifier.testTag("settings_plate_model_ready"),
            )
            Text(
                stringResource(R.string.cloud_optional),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            PpField(
                workerUrl,
                { workerUrl = it },
                stringResource(R.string.worker_url),
                placeholder = stringResource(R.string.worker_url_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = normalizedWorkerUrl.isNotBlank() && !validWorkerUrl,
                modifier = Modifier.testTag("settings_worker_url"),
            )
            PpField(
                token,
                { token = it },
                stringResource(R.string.new_token_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.testTag("settings_token"),
            )
            PpButton(
                text = stringResource(R.string.clear_token),
                onClick = onClearToken,
                enabled = cloudConfigured,
                style = PpButtonStyle.Ghost,
            )
            PpChoiceRow(
                title = stringResource(R.string.workers_ai),
                selected = provider == CloudProvider.WORKERS_AI,
                onClick = { provider = CloudProvider.WORKERS_AI },
                leadingIcon = PpIcons.Recognition,
                modifier = Modifier.testTag("settings_provider_workers"),
            )
            PpChoiceRow(
                title = stringResource(R.string.openai),
                selected = provider == CloudProvider.OPENAI,
                onClick = { provider = CloudProvider.OPENAI },
                leadingIcon = PpIcons.Recognition,
                modifier = Modifier.testTag("settings_provider_openai"),
            )
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(workerUrl, provider, token) },
                enabled = cloudSettingsValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 20.dp)
                    .testTag("settings_recognition_save"),
            )
        }
    }
}

private fun isValidHttpsUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}

@Composable
private fun TemplateSettingsSheet(
    templates: List<CustomViolationTemplate>,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CustomViolationTemplate) -> Unit,
    onDelete: (CustomViolationTemplate) -> Unit,
) {
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.custom_templates), dismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.built_in_templates_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            templates.forEach { template ->
                PpCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(template.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                template.estonianDescription,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PpIconButton(
                            icon = PpIcons.Edit,
                            contentDescription = stringResource(R.string.edit),
                            onClick = { onEdit(template) },
                            modifier = Modifier.testTag("template_edit_${template.id}"),
                            containerColor = MaterialTheme.colorScheme.surface,
                        )
                        PpIconButton(
                            icon = PpIcons.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = { onDelete(template) },
                            modifier = Modifier.testTag("template_delete_${template.id}"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            PpButton(
                text = stringResource(R.string.add_template),
                onClick = onAdd,
                icon = PpIcons.Add,
                style = PpButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("settings_add_template"),
            )
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PrivacySettingsSheet(
    onClose: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.privacy_and_data), dismiss)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PpTextButton(
                text = stringResource(R.string.privacy_policy),
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.testTag("settings_privacy_policy"),
            )
            PpButton(
                text = stringResource(R.string.delete_all_data),
                onClick = onDeleteAll,
                icon = PpIcons.Delete,
                style = PpButtonStyle.Danger,
                modifier = Modifier.fillMaxWidth().testTag("settings_delete_all"),
            )
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TemplateDialog(
    template: CustomViolationTemplate?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var displayName by rememberSaveable(template?.id) { mutableStateOf(template?.displayName.orEmpty()) }
    var description by rememberSaveable(template?.id) { mutableStateOf(template?.estonianDescription.orEmpty()) }
    PpDialog(
        title = stringResource(if (template == null) R.string.add_template else R.string.edit_template),
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.save),
        onConfirm = { onSave(displayName, description) },
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = displayName.isNotBlank() && description.isNotBlank(),
        confirmTestTag = "template_save",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PpField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = stringResource(R.string.template_name),
                    modifier = Modifier.testTag("template_name"),
                )
                PpField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.estonian_description),
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.testTag("template_description"),
                )
            }
        },
    )
}

@Composable
private fun languageSummary(tag: String): String = stringResource(
    when (tag) {
        "en" -> R.string.language_english
        "ru" -> R.string.language_russian
        "et" -> R.string.language_estonian
        else -> R.string.language_system
    },
)

@Composable
private fun providerLabel(provider: CloudProvider): String = stringResource(
    if (provider == CloudProvider.WORKERS_AI) R.string.workers_ai else R.string.openai,
)
