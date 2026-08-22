package com.perdolique.poleparkla.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.ui.PoleParklaColors
import com.perdolique.poleparkla.ui.components.LanguageSelector
import com.perdolique.poleparkla.ui.components.PoleParklaSystemBars
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpCard
import com.perdolique.poleparkla.ui.components.PpField
import com.perdolique.poleparkla.ui.components.PpIconButton
import com.perdolique.poleparkla.ui.components.PpIcons
import com.perdolique.poleparkla.ui.components.PpTextButton

@Composable
fun OnboardingScreen(
    settings: AppSettings,
    onComplete: (String, ReporterProfile, String) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable(settings.languageTag) { mutableStateOf(settings.languageTag) }
    var name by rememberSaveable(settings.profile.name) { mutableStateOf(settings.profile.name) }
    var phone by rememberSaveable(settings.profile.phone) { mutableStateOf(settings.profile.phone) }
    var recipient by rememberSaveable(settings.defaultRecipient) { mutableStateOf(settings.defaultRecipient) }
    var editRecipient by rememberSaveable { mutableStateOf(false) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }

    PoleParklaSystemBars()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step == 1) {
                    PpIconButton(
                        icon = PpIcons.Back,
                        contentDescription = stringResource(R.string.back),
                        onClick = { step = 0 },
                        containerColor = MaterialTheme.colorScheme.background,
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = if (step == 1) 8.dp else 4.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (step == 0) {
                    WelcomeStep(
                        language = language,
                        onLanguageSelected = { tag ->
                            language = tag
                            val locales = if (tag.isBlank()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(tag)
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        },
                    )
                } else {
                    ProfileStep(
                        name = name,
                        onNameChange = { name = it; showValidation = false },
                        phone = phone,
                        onPhoneChange = { phone = it; showValidation = false },
                        recipient = recipient,
                        onRecipientChange = { recipient = it; showValidation = false },
                        editRecipient = editRecipient,
                        onEditRecipient = { editRecipient = true },
                        showValidation = showValidation,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step == 0) {
                    PpTextButton(
                        text = stringResource(R.string.onboarding_privacy_agreement),
                        onClick = { showPrivacyPolicy = true },
                        modifier = Modifier.testTag("onboarding_privacy_policy"),
                    )
                }
                StepIndicator(step)
                PpButton(
                    text = stringResource(if (step == 0) R.string.continue_button else R.string.start_camera),
                    onClick = {
                        if (step == 0) {
                            step = 1
                        } else if (name.isBlank() || phone.isBlank() || recipient.isBlank()) {
                            showValidation = true
                        } else {
                            onComplete(language, ReporterProfile(name, phone), recipient)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_submit"),
                )
            }
        }
    }
    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }
}

@Composable
private fun WelcomeStep(
    language: String,
    onLanguageSelected: (String) -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .size(88.dp)
            .background(PoleParklaColors.LightForest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(48.dp).testTag("brand_mark"),
        )
    }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.onboarding_value_title), style = MaterialTheme.typography.displaySmall)
    Text(
        stringResource(R.string.onboarding_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
    PpCard(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(PpIcons.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.safe_warning_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.safe_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    Text(
        stringResource(R.string.language),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
    LanguageSelector(language, onLanguageSelected)
}

@Composable
private fun ProfileStep(
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    recipient: String,
    onRecipientChange: (String) -> Unit,
    editRecipient: Boolean,
    onEditRecipient: () -> Unit,
    showValidation: Boolean,
) {
    Text(stringResource(R.string.reporter_setup_title), style = MaterialTheme.typography.displaySmall)
    Text(
        stringResource(R.string.reporter_setup_intro),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PpField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.name),
            isError = showValidation && name.isBlank(),
            modifier = Modifier.testTag("onboarding_name"),
        )
        PpField(
            value = phone,
            onValueChange = onPhoneChange,
            label = stringResource(R.string.phone),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = showValidation && phone.isBlank(),
            modifier = Modifier.testTag("onboarding_phone"),
        )
    }
    Text(
        stringResource(R.string.recipient_summary),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
    if (editRecipient) {
        PpField(
            value = recipient,
            onValueChange = onRecipientChange,
            label = stringResource(R.string.default_recipient),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = showValidation && recipient.isBlank(),
            modifier = Modifier.testTag("onboarding_recipient"),
        )
    } else {
        PpCard(modifier = Modifier.fillMaxWidth(), onClick = onEditRecipient) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(PpIcons.Email, null, tint = MaterialTheme.colorScheme.primary)
                Text(recipient, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(PpIcons.Edit, stringResource(R.string.edit))
            }
        }
    }
    if (showValidation) {
        Text(
            stringResource(R.string.onboarding_validation),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp).testTag("onboarding_validation"),
        )
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { index ->
            Box(
                Modifier
                    .size(if (index == step) 10.dp else 8.dp)
                    .background(
                        if (index == step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
            )
        }
        Text(
            stringResource(R.string.onboarding_step, step + 1),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
