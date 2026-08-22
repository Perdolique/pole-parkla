package com.perdolique.poleparkla.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.domain.ViolationTemplates
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.MailApp
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.AddressLookupResult
import com.perdolique.poleparkla.ui.MailPreparation
import com.perdolique.poleparkla.ui.PhotoEditorMetadata
import com.perdolique.poleparkla.ui.ReportCompletion
import com.perdolique.poleparkla.ui.ReportCompletionStep
import com.perdolique.poleparkla.ui.PoleParklaViewModel
import com.perdolique.poleparkla.ui.completion
import com.perdolique.poleparkla.ui.findActivity
import com.perdolique.poleparkla.ui.openAppSettings
import com.perdolique.poleparkla.ui.components.PhotoThumbnail
import com.perdolique.poleparkla.ui.components.PoleParklaSystemBars
import com.perdolique.poleparkla.ui.components.PpBadge
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpCard
import com.perdolique.poleparkla.ui.components.PpDialog
import com.perdolique.poleparkla.ui.components.PpIcons
import com.perdolique.poleparkla.ui.components.PpSheet
import com.perdolique.poleparkla.ui.components.PpSheetHeader
import com.perdolique.poleparkla.ui.components.PpTopBar

@Composable
fun ReviewScreen(
    reportId: String,
    viewModel: PoleParklaViewModel,
    photoStore: PhotoStore,
    onBack: () -> Unit,
    onAddPhotos: () -> Unit,
) {
    val reportFlow = remember(reportId) { viewModel.report(reportId) }
    val report by reportFlow.collectAsStateWithLifecycle(initialValue = null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val cloudConfigured by viewModel.cloudConfigured.collectAsStateWithLifecycle()
    val mailPreparation by viewModel.mailPreparation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var activeEditor by remember { mutableStateOf<ReportEditor?>(null) }
    var consentProvider by remember { mutableStateOf<CloudProvider?>(null) }
    var photoToDelete by remember { mutableStateOf<ReportPhoto?>(null) }
    var pendingMailReportId by rememberSaveable { mutableStateOf<String?>(null) }
    val mailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingMailReportId?.let(viewModel::onMailClientReturned)
        pendingMailReportId = null
    }

    val current = report
    PoleParklaSystemBars()
    if (current == null) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PpTopBar(stringResource(R.string.review_title), onBack = onBack)
            CircularProgressIndicator(Modifier.padding(32.dp))
        }
        return
    }

    LaunchedEffect(
        current.id,
        current.localRecognitionFingerprint,
        current.photos.map { it.id },
    ) {
        viewModel.ensureLocalRecognition(current.id)
    }

    var locationPermissionRefresh by remember { mutableIntStateOf(0) }
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var showLocationPermissionNotice by remember { mutableStateOf(false) }
    var locationPermissionPermanentlyDenied by remember { mutableStateOf(false) }
    var useLocationAfterPermission by remember { mutableStateOf(false) }
    var locationLoading by remember { mutableStateOf(false) }
    var locationRequestId by remember(current.id) { mutableLongStateOf(0L) }
    var locationSuggestion by remember(current.id) { mutableStateOf<LocationEditorSuggestion?>(null) }
    var photoEditorMetadata by remember(current.id, current.primaryPhoto?.id) {
        mutableStateOf<PhotoEditorMetadata?>(null)
    }
    val locationPermissionGranted = remember(locationPermissionRefresh) {
        context.hasLocationPermission()
    }
    val completeLocationLookup: (Long, AddressLookupResult?, Boolean) -> Unit =
        { requestId, result, applyLocation ->
            if (requestId == locationRequestId) {
                locationLoading = false
                locationSuggestion = result?.toEditorSuggestion(requestId, applyLocation)
            }
        }
    val loadCurrentLocation = {
        locationRequestId++
        val requestId = locationRequestId
        locationLoading = true
        viewModel.loadCurrentLocation { result ->
            completeLocationLookup(requestId, result, true)
        }
    }
    val loadPhotoLocation: () -> Unit = {
        val location = photoEditorMetadata?.location
        if (location != null) {
            locationRequestId++
            val requestId = locationRequestId
            locationLoading = true
            viewModel.loadPhotoLocation(location) { result ->
                completeLocationLookup(requestId, result, true)
            }
        }
    }
    val retryAddressLookup: (LocationSnapshot) -> Unit = { location ->
        locationRequestId++
        val requestId = locationRequestId
        locationLoading = true
        viewModel.loadAddressCandidates(location, forceRefresh = true) { result ->
            completeLocationLookup(requestId, result, false)
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        locationPermissionRefresh++
        val granted = LOCATION_PERMISSIONS.any { result[it] == true }
        if (result.isNotEmpty() && !granted && !context.shouldShowLocationPermissionRationale()) {
            locationPermissionPermanentlyDenied = true
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        locationPermissionRefresh++
    }

    LaunchedEffect(locationPermissionGranted, useLocationAfterPermission) {
        if (locationPermissionGranted && useLocationAfterPermission) {
            useLocationAfterPermission = false
            loadCurrentLocation()
        }
    }

    LaunchedEffect(activeEditor, current.primaryPhoto?.id) {
        if (activeEditor == ReportEditor.LOCATION && photoEditorMetadata == null) {
            photoEditorMetadata = current.primaryPhoto?.let { viewModel.readPhotoEditorMetadata(it) }
        }
    }

    LaunchedEffect(
        activeEditor,
        current.latitude,
        current.longitude,
        current.accuracyMeters,
    ) {
        val latitude = current.latitude
        val longitude = current.longitude
        if (activeEditor == ReportEditor.LOCATION && latitude != null && longitude != null) {
            val location = LocationSnapshot(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = current.accuracyMeters,
                capturedAtEpochMillis = current.occurredAtEpochMillis,
            )
            locationRequestId++
            val requestId = locationRequestId
            locationLoading = true
            viewModel.loadAddressCandidates(location, forceRefresh = false) { result ->
                completeLocationLookup(requestId, result, false)
            }
        }
    }

    val description = current.violationDescription(templates)
    val completion = current.completion(settings.profile, description)
    val requestCloudRecognition: (CloudProvider) -> Unit = { provider ->
        val consented = if (provider == CloudProvider.WORKERS_AI) {
            settings.workersAiConsent
        } else {
            settings.openAiConsent
        }
        if (consented) viewModel.recognizeWithCloud(reportId, provider)
        else consentProvider = provider
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        PpTopBar(
            title = stringResource(R.string.review_title),
            onBack = onBack,
            actions = {
                PpBadge(
                    text = statusLabel(current.status),
                    isComplete = completion.ready,
                )
            },
        )
        ReportOverview(
            report = current,
            completion = completion,
            templates = templates,
            photoStore = photoStore,
            onPhotos = { activeEditor = ReportEditor.PHOTOS },
            onVehicle = { activeEditor = ReportEditor.VEHICLE },
            onViolation = { activeEditor = ReportEditor.VIOLATION },
            onLocation = {
                locationSuggestion = null
                locationLoading = false
                activeEditor = ReportEditor.LOCATION
            },
            onDelivery = { activeEditor = ReportEditor.DELIVERY },
            modifier = Modifier.weight(1f),
        )
        ReviewAction(
            completion = completion,
            busy = busy,
            onClick = {
                performReportPrimaryAction(
                    nextStep = completion.nextStep,
                    onAddPhotos = onAddPhotos,
                    onEditor = { editor ->
                        if (editor == ReportEditor.LOCATION) {
                            locationSuggestion = null
                            locationLoading = false
                        }
                        activeEditor = editor
                    },
                    onReady = { viewModel.prepareMail(reportId) },
                )
            },
        )
    }

    when (activeEditor) {
        ReportEditor.PHOTOS -> PhotoEditorSheet(
            report = current,
            photoStore = photoStore,
            busy = busy,
            onClose = { activeEditor = null },
            onAddPhotos = {
                activeEditor = null
                onAddPhotos()
            },
            onRetry = { viewModel.retryPhotoRecognition(reportId, it) },
            onDelete = { photoToDelete = it },
        )
        ReportEditor.VEHICLE -> VehicleEditorSheet(
            report = current,
            busy = busy,
            cloudConfigured = cloudConfigured,
            defaultProvider = settings.cloudProvider,
            onClose = { activeEditor = null },
            onSave = { plate, make, model ->
                viewModel.updateVehicleDetails(reportId, plate, make, model)
                activeEditor = null
            },
            onRecognize = requestCloudRecognition,
        )
        ReportEditor.VIOLATION -> ViolationEditorSheet(
            report = current,
            templates = templates,
            onClose = { activeEditor = null },
            onSelect = { type, templateId ->
                viewModel.chooseViolation(reportId, type, templateId)
                activeEditor = null
            },
        )
        ReportEditor.LOCATION -> {
            val photoLocation = photoEditorMetadata?.location?.let { location ->
                LocationEditorSuggestion(
                    address = null,
                    latitude = location.latitude.toString(),
                    longitude = location.longitude.toString(),
                    accuracyMeters = location.accuracyMeters,
                )
            }
            LocationEditorSheet(
                report = current,
                formattedTime = viewModel.formatOccurredAt(current.occurredAtEpochMillis),
                photoTime = photoEditorMetadata?.capturedAtEpochMillis?.let(viewModel::formatOccurredAt),
                photoLocation = photoLocation,
                locationLoading = locationLoading,
                locationRequestId = locationRequestId,
                suggestedLocation = locationSuggestion,
                onUsePhotoLocation = loadPhotoLocation,
                onClose = {
                    locationSuggestion = null
                    locationLoading = false
                    activeEditor = null
                },
                onUseCurrentLocation = {
                    if (locationPermissionGranted) {
                        loadCurrentLocation()
                    } else {
                        showLocationPermissionNotice = true
                    }
                },
                onRetryAddressLookup = retryAddressLookup,
                resolveAddress = viewModel::resolveAddress,
                onUseCurrentTime = { viewModel.formatOccurredAt(System.currentTimeMillis()) },
                onSave = { address, latitude, longitude, occurredAt, accuracyMeters ->
                    viewModel.updateLocation(
                        reportId,
                        address,
                        latitude,
                        longitude,
                        occurredAt,
                        accuracyMeters,
                    )
                },
            )
        }
        ReportEditor.DELIVERY -> DeliveryEditorSheet(
            report = current,
            onClose = { activeEditor = null },
            onSaveRecipient = { viewModel.updateRecipient(reportId, it) },
            onSaveLetter = { subject, body -> viewModel.updateLetter(reportId, subject, body) },
            onSaveRegeneratedLetter = { viewModel.regenerateLetter(reportId) },
            onRegenerate = { viewModel.previewRegeneratedLetter(current) },
        )
        null -> Unit
    }

    if (showLocationPermissionNotice) {
        PpDialog(
            title = stringResource(R.string.location_permission_title),
            onDismissRequest = { showLocationPermissionNotice = false },
            confirmText = stringResource(R.string.grant_location),
            onConfirm = {
                showLocationPermissionNotice = false
                useLocationAfterPermission = true
                if (locationPermissionRequested && !context.shouldShowLocationPermissionRationale()) {
                    locationPermissionPermanentlyDenied = true
                } else {
                    locationPermissionRequested = true
                    locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                }
            },
            dismissText = stringResource(R.string.cancel),
            content = {
                Text(
                    stringResource(R.string.location_permission_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    if (locationPermissionPermanentlyDenied) {
        PpDialog(
            title = stringResource(R.string.location_permission_title),
            onDismissRequest = { locationPermissionPermanentlyDenied = false },
            confirmText = stringResource(R.string.open_settings),
            onConfirm = {
                locationPermissionPermanentlyDenied = false
                context.openAppSettings()
            },
            dismissText = stringResource(R.string.cancel),
            content = {
                Text(
                    stringResource(R.string.location_permission_settings_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    photoToDelete?.let { photo ->
        PpDialog(
            title = stringResource(R.string.remove_photo),
            onDismissRequest = { photoToDelete = null },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                viewModel.removePhoto(reportId, photo.id)
                photoToDelete = null
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    consentProvider?.let { provider ->
        val providerName = stringResource(
            if (provider == CloudProvider.WORKERS_AI) R.string.workers_ai else R.string.openai,
        )
        PpDialog(
            title = stringResource(R.string.cloud_consent_title, providerName),
            onDismissRequest = { consentProvider = null },
            confirmText = stringResource(R.string.send_photo),
            onConfirm = {
                viewModel.setCloudConsent(provider, true)
                viewModel.recognizeWithCloud(reportId, provider)
                consentProvider = null
            },
            dismissText = stringResource(R.string.cancel),
            content = {
                Text(
                    stringResource(R.string.cloud_consent_text, providerName),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
    MailDialogs(
        preparation = mailPreparation,
        context = context,
        onDismiss = viewModel::dismissMailPreparation,
        onChoose = { preparation, app ->
            runCatching {
                viewModel.rememberMailComponent(app.component)
                pendingMailReportId = preparation.report.id
                mailLauncher.launch(viewModel.buildMailIntent(preparation, app.component))
            }.onSuccess {
                viewModel.markMailOpened(preparation.report.id)
            }.onFailure {
                pendingMailReportId = null
                viewModel.mailLaunchFailed()
            }
        },
    )
}

@Composable
private fun ReportOverview(
    report: Report,
    completion: ReportCompletion,
    templates: List<CustomViolationTemplate>,
    photoStore: PhotoStore,
    onPhotos: () -> Unit,
    onVehicle: () -> Unit,
    onViolation: () -> Unit,
    onLocation: () -> Unit,
    onDelivery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PpBadge(
                    text = stringResource(
                        R.string.completion_progress,
                        completion.completedCoreSteps,
                        completion.totalCoreSteps,
                    ),
                    isComplete = completion.completedCoreSteps == completion.totalCoreSteps,
                )
                if (report.locationNeedsReview) {
                    PpBadge(
                        text = stringResource(R.string.location_review_short),
                        isError = true,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    EvidencePane(
                        report = report,
                        photoStore = photoStore,
                        onClick = onPhotos,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCards(
                        report = report,
                        templates = templates,
                        onVehicle = onVehicle,
                        onViolation = onViolation,
                        onLocation = onLocation,
                        onDelivery = onDelivery,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                EvidencePane(report, photoStore, onPhotos, Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                SummaryCards(
                    report = report,
                    templates = templates,
                    onVehicle = onVehicle,
                    onViolation = onViolation,
                    onLocation = onLocation,
                    onDelivery = onDelivery,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EvidencePane(
    report: Report,
    photoStore: PhotoStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PpCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(8.dp)) {
            val primary = report.primaryPhoto
            if (primary != null) {
                PhotoThumbnail(
                    photo = primary,
                    photoStore = photoStore,
                    contentDescription = stringResource(R.string.report_photo),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PpIcons.Camera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.photo_counter, report.photos.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(PpIcons.ChevronRight, stringResource(R.string.photos))
            }
        }
    }
}

@Composable
private fun SummaryCards(
    report: Report,
    templates: List<CustomViolationTemplate>,
    onVehicle: () -> Unit,
    onViolation: () -> Unit,
    onLocation: () -> Unit,
    onDelivery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val violation = report.violationSummary(templates)
    val location = report.address.ifBlank { report.coordinatesText() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryCard(
            title = stringResource(R.string.vehicle_editor_title),
            value = report.plate.ifBlank { stringResource(R.string.missing_plate) },
            icon = PpIcons.Car,
            complete = report.plate.isNotBlank(),
            onClick = onVehicle,
        )
        SummaryCard(
            title = stringResource(R.string.violation),
            value = violation ?: stringResource(R.string.missing_violation),
            icon = PpIcons.Violation,
            complete = violation != null,
            onClick = onViolation,
        )
        SummaryCard(
            title = stringResource(R.string.location_and_time),
            value = location.ifBlank { stringResource(R.string.missing_location) },
            icon = PpIcons.Location,
            complete = location.isNotBlank(),
            warning = report.locationNeedsReview,
            onClick = onLocation,
        )
        SummaryCard(
            title = stringResource(R.string.letter_and_recipient),
            value = report.recipient.ifBlank { stringResource(R.string.missing_delivery) },
            icon = PpIcons.Email,
            complete = report.recipient.isNotBlank(),
            onClick = onDelivery,
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    complete: Boolean,
    onClick: () -> Unit,
    warning: Boolean = false,
) {
    val state = when {
        warning -> stringResource(R.string.location_review_short)
        complete -> stringResource(R.string.complete)
        else -> stringResource(R.string.incomplete)
    }
    PpCard(
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = state },
        onClick = onClick,
        selected = warning,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        when {
                            warning -> MaterialTheme.colorScheme.errorContainer
                            complete -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (warning) PpIcons.Warning else icon,
                    contentDescription = null,
                    tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!complete || warning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Icon(
                if (complete && !warning) PpIcons.CheckCircle else PpIcons.ChevronRight,
                contentDescription = null,
                tint = if (complete && !warning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun performReportPrimaryAction(
    nextStep: ReportCompletionStep?,
    onAddPhotos: () -> Unit,
    onEditor: (ReportEditor) -> Unit,
    onReady: () -> Unit,
) {
    when (nextStep) {
        ReportCompletionStep.PHOTOS -> onAddPhotos()
        ReportCompletionStep.VEHICLE -> onEditor(ReportEditor.VEHICLE)
        ReportCompletionStep.VIOLATION -> onEditor(ReportEditor.VIOLATION)
        ReportCompletionStep.LOCATION -> onEditor(ReportEditor.LOCATION)
        ReportCompletionStep.DELIVERY -> onEditor(ReportEditor.DELIVERY)
        null -> onReady()
    }
}

@Composable
private fun ReviewAction(completion: ReportCompletion, busy: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when (completion.nextStep) {
            ReportCompletionStep.PHOTOS -> R.string.next_add_photos
            ReportCompletionStep.VEHICLE -> R.string.next_fill_plate
            ReportCompletionStep.VIOLATION -> R.string.next_choose_violation
            ReportCompletionStep.LOCATION -> R.string.next_add_location
            ReportCompletionStep.DELIVERY -> R.string.next_check_delivery
            null -> R.string.open_mail
        },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PpButton(
            text = label,
            onClick = onClick,
            enabled = !busy,
            icon = if (completion.ready) PpIcons.Send else null,
            modifier = Modifier.fillMaxWidth().testTag("review_primary_action"),
        )
    }
}

@Composable
private fun MailDialogs(
    preparation: MailPreparation,
    context: Context,
    onDismiss: () -> Unit,
    onChoose: (MailPreparation.Ready, MailApp) -> Unit,
) {
    when (preparation) {
        MailPreparation.Idle -> Unit
        is MailPreparation.Ready -> {
            if (preparation.savedComponent != null) {
                val savedApp = preparation.apps.firstOrNull { it.component == preparation.savedComponent }
                LaunchedEffect(preparation) {
                    if (savedApp != null) onChoose(preparation, savedApp) else onDismiss()
                }
            } else if (preparation.apps.isNotEmpty()) {
                PpSheet(onDismissRequest = onDismiss) { dismiss ->
                    PpSheetHeader(stringResource(R.string.choose_mail_app), dismiss)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        preparation.apps.forEach { app ->
                            PpButton(
                                text = app.label,
                                onClick = { onChoose(preparation, app) },
                                style = PpButtonStyle.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            } else {
                PpSheet(onDismissRequest = onDismiss) { dismiss ->
                    PpSheetHeader(stringResource(R.string.no_mail_app_title), dismiss)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.no_mail_app_text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CopyButton(context, stringResource(R.string.copy_recipient), preparation.report.recipient)
                        CopyButton(context, stringResource(R.string.copy_subject), preparation.report.subject)
                        CopyButton(context, stringResource(R.string.copy_body), preparation.report.body)
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyButton(context: Context, label: String, value: String) {
    PpButton(
        text = label,
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        },
        style = PpButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun statusLabel(status: ReportStatus): String = stringResource(
    when (status) {
        ReportStatus.DRAFT -> R.string.status_draft
        ReportStatus.READY -> R.string.status_ready
        ReportStatus.HANDED_OFF_TO_MAIL -> R.string.status_handed_off
    },
)

private fun Report.violationDescription(templates: List<CustomViolationTemplate>): String? =
    when (violationType) {
        ViolationType.CUSTOM -> templates.firstOrNull { it.id == customTemplateId }?.estonianDescription
        else -> ViolationTemplates.description(violationType)
    }

@Composable
private fun Report.violationSummary(templates: List<CustomViolationTemplate>): String? = when (violationType) {
    ViolationType.CYCLE_PATH -> stringResource(R.string.cycle_path)
    ViolationType.PEDESTRIAN_PATH -> stringResource(R.string.pedestrian_path)
    ViolationType.CUSTOM -> templates.firstOrNull { it.id == customTemplateId }?.displayName
    null -> null
}

private fun Report.coordinatesText(): String =
    if (latitude != null && longitude != null) "%.6f, %.6f".format(latitude, longitude) else ""

private fun AddressLookupResult.toEditorSuggestion(
    requestId: Long,
    applyLocation: Boolean,
): LocationEditorSuggestion = LocationEditorSuggestion(
    address = resolution?.suggested?.address,
    latitude = location.latitude.toString(),
    longitude = location.longitude.toString(),
    accuracyMeters = location.accuracyMeters,
    candidates = resolution?.candidates.orEmpty(),
    addressLookupFailed = resolution?.suggested == null,
    requestId = requestId,
    applyLocation = applyLocation,
)

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.shouldShowLocationPermissionRationale(): Boolean {
    val activity = findActivity() ?: return false
    return LOCATION_PERMISSIONS.any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}
