package com.perdolique.poleparkla.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.screens.CameraScreen
import com.perdolique.poleparkla.ui.screens.HistoryScreen
import com.perdolique.poleparkla.ui.screens.OnboardingScreen
import com.perdolique.poleparkla.ui.screens.ReportWizardScreen
import com.perdolique.poleparkla.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.Flow

private object Routes {
    const val Onboarding = "onboarding"
    const val Camera = "camera"
    const val History = "history"
    const val Settings = "settings"
    const val SettingsFromReportPattern = "settings/report/{reportId}"
    const val ReviewPattern = "review/{reportId}"
    fun review(reportId: String) = "review/$reportId"
    fun settingsFromReport(reportId: String) = "settings/report/$reportId"
}

@Composable
internal fun rememberInitialRoute(
    onboardingComplete: Boolean,
    reports: List<Report>,
): String = remember {
    val restoredDraft = reports.firstOrNull { it.photos.isNotEmpty() }
    when {
        !onboardingComplete -> Routes.Onboarding
        restoredDraft != null -> Routes.review(restoredDraft.id)
        else -> Routes.Camera
    }
}

internal fun navigateBackFromReview(
    popBackStack: () -> Boolean,
    onEmptyBackStack: () -> Unit,
) {
    if (!popBackStack()) onEmptyBackStack()
}

@Composable
fun PoleParklaApp(
    viewModel: PoleParklaViewModel,
    photoStore: PhotoStore,
    onRequestInAppReview: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val cloudConfigured by viewModel.cloudConfigured.collectAsStateWithLifecycle()
    if (!settings.loaded || reports == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val appVersion = remember(context) { context.appVersionName() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { notice -> snackbar.showSnackbar(context.noticeMessage(notice)) }
    }
    CollectUiEffects(viewModel.effects, onRequestInAppReview)
    val initialRoute = rememberInitialRoute(settings.onboardingComplete, reports.orEmpty())
    val navController = rememberNavController()

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = initialRoute) {
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    settings = settings,
                    onComplete = { language, profile, recipient ->
                        viewModel.completeOnboarding(language, profile, recipient) {
                            navController.navigate(Routes.Camera) {
                                popUpTo(Routes.Onboarding) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(Routes.Camera) {
                CameraScreen(
                    viewModel = viewModel,
                    photoStore = photoStore,
                    onHistory = { navController.navigate(Routes.History) },
                    onSettings = { navController.navigate(Routes.Settings) },
                    onReview = { navController.navigate(Routes.review(it)) },
                )
            }
            composable(Routes.History) {
                HistoryScreen(
                    reports = reports.orEmpty(),
                    photoStore = photoStore,
                    onBack = { navController.popBackStack() },
                    onNewReport = {
                        viewModel.startNewCameraSession()
                        navController.navigate(Routes.Camera)
                    },
                    onOpen = { navController.navigate(Routes.review(it)) },
                    onDelete = viewModel::deleteReport,
                )
            }
            composable(Routes.Settings) {
                val templates by viewModel.templates.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    cloudConfigured = cloudConfigured,
                    templates = templates,
                    appVersion = appVersion,
                    onBack = { navController.popBackStack() },
                    onRateApp = context::openPlayStorePage,
                    onLanguageSelected = { languageTag ->
                        viewModel.setLanguage(languageTag)
                        AppCompatDelegate.setApplicationLocales(
                            if (languageTag.isBlank()) LocaleListCompat.getEmptyLocaleList()
                            else LocaleListCompat.forLanguageTags(languageTag),
                        )
                    },
                    onSave = viewModel::saveSettings,
                    onClearToken = viewModel::clearToken,
                    onUpsertTemplate = viewModel::upsertTemplate,
                    onDeleteTemplate = viewModel::deleteTemplate,
                    onDeleteAll = {
                        viewModel.deleteAllLocalData {
                            navController.navigate(Routes.Onboarding) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(
                route = Routes.SettingsFromReportPattern,
                arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            ) {
                val templates by viewModel.templates.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    cloudConfigured = cloudConfigured,
                    templates = templates,
                    appVersion = appVersion,
                    onBack = { navController.popBackStack() },
                    onRateApp = context::openPlayStorePage,
                    onLanguageSelected = { languageTag ->
                        viewModel.setLanguage(languageTag)
                        AppCompatDelegate.setApplicationLocales(
                            if (languageTag.isBlank()) LocaleListCompat.getEmptyLocaleList()
                            else LocaleListCompat.forLanguageTags(languageTag),
                        )
                    },
                    onSave = { languageTag, profile, recipient, workerUrl, provider, newToken, onSaved ->
                        viewModel.saveSettings(
                            languageTag,
                            profile,
                            recipient,
                            workerUrl,
                            provider,
                            newToken,
                        ) {
                            onSaved()
                            navController.popBackStack()
                        }
                    },
                    onClearToken = viewModel::clearToken,
                    onUpsertTemplate = viewModel::upsertTemplate,
                    onDeleteTemplate = viewModel::deleteTemplate,
                    onDeleteAll = {
                        viewModel.deleteAllLocalData {
                            navController.navigate(Routes.Onboarding) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(
                route = Routes.ReviewPattern,
                arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            ) { entry ->
                val reportId = requireNotNull(entry.arguments?.getString("reportId"))
                val onReviewBack = {
                    navigateBackFromReview(
                        popBackStack = navController::popBackStack,
                        onEmptyBackStack = {
                            viewModel.continueCameraSession(reportId)
                            navController.navigate(Routes.Camera) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                ReportWizardScreen(
                    reportId = reportId,
                    viewModel = viewModel,
                    photoStore = photoStore,
                    onBack = onReviewBack,
                    onAddPhotos = {
                        viewModel.continueCameraSession(reportId)
                        navController.navigate(Routes.Camera)
                    },
                    onEditSettings = { navController.navigate(Routes.settingsFromReport(reportId)) },
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) { data ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
internal fun CollectUiEffects(
    effects: Flow<UiEffect>,
    onRequestInAppReview: () -> Unit,
) {
    val currentOnRequestInAppReview by rememberUpdatedState(onRequestInAppReview)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                UiEffect.RequestInAppReview -> currentOnRequestInAppReview()
            }
        }
    }
}

private fun Context.appVersionName(): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName.orEmpty()
}

private fun Context.openPlayStorePage() {
    val productionPackage = "com.perdolique.poleparkla"
    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$productionPackage".toUri(),
    ).setPackage("com.android.vending")

    try {
        startActivity(playStoreIntent)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$productionPackage".toUri(),
                ),
            )
        }
    }
}

private fun Context.noticeMessage(notice: UiNotice): String = when (notice.kind) {
    NoticeKind.PHOTO_FAILED -> getString(R.string.notice_photo_failed)
    NoticeKind.OCR_FAILED -> getString(R.string.notice_ocr_failed)
    NoticeKind.PLATE_RECOGNITION_FAILED -> getString(R.string.notice_plate_recognition_failed)
    NoticeKind.CLOUD_FAILED -> getString(R.string.notice_cloud_failed, notice.detail ?: "error")
    NoticeKind.INVALID_REPORT -> getString(R.string.validation_error)
    NoticeKind.MAIL_FAILED -> getString(R.string.notice_mail_failed)
    NoticeKind.LOCATION_UNAVAILABLE -> getString(R.string.notice_location_unavailable)
    NoticeKind.SAVED -> getString(R.string.notice_saved)
    NoticeKind.DATA_DELETED -> getString(R.string.notice_data_deleted)
    NoticeKind.DATA_DELETE_FAILED -> getString(R.string.notice_data_delete_failed)
}
