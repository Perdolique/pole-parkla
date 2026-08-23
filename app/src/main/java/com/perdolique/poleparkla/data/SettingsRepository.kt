package com.perdolique.poleparkla.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.perdolique.poleparkla.model.AppSettings
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.DEFAULT_RECIPIENT
import com.perdolique.poleparkla.model.ReporterProfile
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val languageTag = stringPreferencesKey("language_tag")
        val reporterName = stringPreferencesKey("reporter_name")
        val reporterPhone = stringPreferencesKey("reporter_phone")
        val defaultRecipient = stringPreferencesKey("default_recipient")
        val workerUrl = stringPreferencesKey("worker_url")
        val cloudProvider = stringPreferencesKey("cloud_provider")
        val workersAiConsent = booleanPreferencesKey("workers_ai_consent")
        val openAiConsent = booleanPreferencesKey("openai_consent")
        val mailComponent = stringPreferencesKey("mail_component")
        val inAppReviewAttempted = booleanPreferencesKey("in_app_review_attempted")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                loaded = true,
                onboardingComplete = preferences[Keys.onboardingComplete] ?: false,
                languageTag = preferences[Keys.languageTag].orEmpty(),
                profile = ReporterProfile(
                    name = preferences[Keys.reporterName].orEmpty(),
                    phone = preferences[Keys.reporterPhone].orEmpty(),
                ),
                defaultRecipient = preferences[Keys.defaultRecipient] ?: DEFAULT_RECIPIENT,
                workerUrl = preferences[Keys.workerUrl].orEmpty(),
                cloudProvider = preferences[Keys.cloudProvider]
                    ?.let { runCatching { CloudProvider.valueOf(it) }.getOrNull() }
                    ?: CloudProvider.WORKERS_AI,
                workersAiConsent = preferences[Keys.workersAiConsent] ?: false,
                openAiConsent = preferences[Keys.openAiConsent] ?: false,
                mailComponent = preferences[Keys.mailComponent].orEmpty(),
            )
        }

    suspend fun completeOnboarding(
        languageTag: String,
        profile: ReporterProfile,
        defaultRecipient: String,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.languageTag] = languageTag
            preferences[Keys.reporterName] = profile.name.trim()
            preferences[Keys.reporterPhone] = profile.phone.trim()
            preferences[Keys.defaultRecipient] = defaultRecipient.trim()
            preferences[Keys.onboardingComplete] = true
        }
    }

    suspend fun saveSettings(
        languageTag: String,
        profile: ReporterProfile,
        defaultRecipient: String,
        workerUrl: String,
        cloudProvider: CloudProvider,
        clearCloudConsents: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.languageTag] = languageTag
            preferences[Keys.reporterName] = profile.name.trim()
            preferences[Keys.reporterPhone] = profile.phone.trim()
            preferences[Keys.defaultRecipient] = defaultRecipient.trim()
            preferences[Keys.workerUrl] = workerUrl.trim()
            preferences[Keys.cloudProvider] = cloudProvider.name
            if (clearCloudConsents) {
                preferences[Keys.workersAiConsent] = false
                preferences[Keys.openAiConsent] = false
            }
        }
    }

    suspend fun setLanguage(languageTag: String) {
        context.settingsDataStore.edit { it[Keys.languageTag] = languageTag }
    }

    suspend fun setCloudConsent(provider: CloudProvider, consented: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val key = if (provider == CloudProvider.WORKERS_AI) {
                Keys.workersAiConsent
            } else {
                Keys.openAiConsent
            }
            preferences[key] = consented
        }
    }

    suspend fun setMailComponent(component: String) {
        context.settingsDataStore.edit { it[Keys.mailComponent] = component }
    }

    suspend fun reserveInAppReviewAttempt(): Boolean {
        var reserved = false
        context.settingsDataStore.edit { preferences ->
            if (preferences[Keys.inAppReviewAttempted] != true) {
                preferences[Keys.inAppReviewAttempted] = true
                reserved = true
            }
        }
        return reserved
    }

    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }
}
