package com.perdolique.poleparkla.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.domain.AddressDraft
import com.perdolique.poleparkla.domain.AddressSelectionPolicy
import com.perdolique.poleparkla.domain.PlateCandidateParser
import com.perdolique.poleparkla.domain.ReportLocationInput
import com.perdolique.poleparkla.domain.ReportLocationInputError
import com.perdolique.poleparkla.domain.ViolationTemplates
import com.perdolique.poleparkla.domain.aggregatePlateCandidates
import com.perdolique.poleparkla.domain.exactPlateCandidate
import com.perdolique.poleparkla.model.AddressCandidate
import com.perdolique.poleparkla.model.AddressCandidateType
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.NormalizedPhotoRect
import com.perdolique.poleparkla.model.PlateCandidate
import com.perdolique.poleparkla.model.PlateObservation
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.MailApp
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.AddressLookupResult
import com.perdolique.poleparkla.ui.MailPreparation
import com.perdolique.poleparkla.ui.PhotoEditorMetadata
import com.perdolique.poleparkla.ui.PoleParklaViewModel
import com.perdolique.poleparkla.ui.ReportWizardStep
import com.perdolique.poleparkla.ui.components.PhotoThumbnail
import com.perdolique.poleparkla.ui.components.PoleParklaSystemBars
import com.perdolique.poleparkla.ui.components.PpBadge
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpCard
import com.perdolique.poleparkla.ui.components.PpChoiceRow
import com.perdolique.poleparkla.ui.components.PpDialog
import com.perdolique.poleparkla.ui.components.PpDisclosureRow
import com.perdolique.poleparkla.ui.components.PpField
import com.perdolique.poleparkla.ui.components.PpIcons
import com.perdolique.poleparkla.ui.components.PpSheet
import com.perdolique.poleparkla.ui.components.PpSheetHeader
import com.perdolique.poleparkla.ui.components.PpTopBar
import com.perdolique.poleparkla.ui.components.rememberManagedBitmap
import com.perdolique.poleparkla.ui.findActivity
import com.perdolique.poleparkla.ui.openAppSettings
import com.perdolique.poleparkla.ui.resolveWizardStep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val REPORT_TIME_ZONE: ZoneId = ZoneId.of("Europe/Tallinn")

@Composable
fun ReportWizardScreen(
    reportId: String,
    viewModel: PoleParklaViewModel,
    photoStore: PhotoStore,
    onBack: () -> Unit,
    onAddPhotos: () -> Unit,
    onEditSettings: () -> Unit,
) {
    val reportFlow = remember(reportId) { viewModel.report(reportId) }
    val report by reportFlow.collectAsStateWithLifecycle(initialValue = null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val cloudConfigured by viewModel.cloudConfigured.collectAsStateWithLifecycle()
    val mailPreparation by viewModel.mailPreparation.collectAsStateWithLifecycle()
    val context = LocalContext.current
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

    var step by remember(reportId) { mutableStateOf(current.resolveWizardStep()) }
    var editingFromSummary by remember(reportId) { mutableStateOf(false) }
    var summaryHasWizardBack by remember(reportId) { mutableStateOf(false) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var showRecipientEditor by remember { mutableStateOf(false) }
    var photoToDelete by remember { mutableStateOf<ReportPhoto?>(null) }
    var fullPhoto by remember { mutableStateOf<ReportPhoto?>(null) }
    var consentProvider by remember { mutableStateOf<CloudProvider?>(null) }
    var pendingMailReportId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(current.id, current.localRecognitionFingerprint, current.photos.map(ReportPhoto::id)) {
        viewModel.ensureLocalRecognition(current.id)
    }
    LaunchedEffect(current.vehicleConfirmed, current.photos.map(ReportPhoto::id), showPhotoEditor) {
        if (!current.vehicleConfirmed && step == ReportWizardStep.SUMMARY && !showPhotoEditor) {
            step = ReportWizardStep.VEHICLE
            editingFromSummary = false
            summaryHasWizardBack = false
        }
    }

    val mailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingMailReportId?.let(viewModel::onMailClientReturned)
        pendingMailReportId = null
    }
    val requestCloudRecognition: (CloudProvider) -> Unit = { provider ->
        val consented = if (provider == CloudProvider.WORKERS_AI) {
            settings.workersAiConsent
        } else {
            settings.openAiConsent
        }
        if (consented) viewModel.recognizeWithCloud(reportId, provider) else consentProvider = provider
    }
    val returnFromEditOrContinue: (ReportWizardStep) -> Unit = { next ->
        if (editingFromSummary) {
            editingFromSummary = false
            summaryHasWizardBack = false
            step = ReportWizardStep.SUMMARY
        } else {
            step = next
        }
    }
    val handleBack = {
        when {
            editingFromSummary -> {
                editingFromSummary = false
                summaryHasWizardBack = false
                step = ReportWizardStep.SUMMARY
            }
            step == ReportWizardStep.VEHICLE -> onBack()
            step == ReportWizardStep.LOCATION -> step = ReportWizardStep.VEHICLE
            step == ReportWizardStep.PROBLEM -> step = ReportWizardStep.LOCATION
            step == ReportWizardStep.SUMMARY && summaryHasWizardBack -> step = ReportWizardStep.PROBLEM
            else -> onBack()
        }
    }
    BackHandler(onBack = handleBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PpTopBar(
            title = wizardTitle(step),
            onBack = handleBack,
            actions = {
                PpBadge(
                    text = stringResource(R.string.wizard_progress, step.ordinal + 1, 4),
                    isComplete = step == ReportWizardStep.SUMMARY,
                )
            },
        )
        when (step) {
            ReportWizardStep.VEHICLE -> VehicleWizardStep(
                report = current,
                photoStore = photoStore,
                busy = busy,
                cloudConfigured = cloudConfigured,
                defaultProvider = settings.cloudProvider,
                onOpenPhoto = { fullPhoto = it },
                onRecognize = requestCloudRecognition,
                onConfirm = { plate, make, model ->
                    viewModel.updateVehicleDetails(reportId, plate, make, model)
                    val nextStep = current.copy(
                        plate = plate.trim().uppercase(Locale.ROOT),
                        vehicleConfirmed = true,
                    ).resolveWizardStep()
                    returnFromEditOrContinue(nextStep)
                },
            )
            ReportWizardStep.LOCATION -> LocationWizardStep(
                report = current,
                viewModel = viewModel,
                onConfirm = {
                    val nextStep = if (current.violationType == null) {
                        ReportWizardStep.PROBLEM
                    } else {
                        ReportWizardStep.SUMMARY
                    }
                    returnFromEditOrContinue(nextStep)
                },
            )
            ReportWizardStep.PROBLEM -> ProblemWizardStep(
                report = current,
                templates = templates,
                onSelect = { type, templateId ->
                    val wasEditing = editingFromSummary
                    viewModel.chooseViolation(reportId, type, templateId)
                    editingFromSummary = false
                    summaryHasWizardBack = !wasEditing
                    step = ReportWizardStep.SUMMARY
                },
            )
            ReportWizardStep.SUMMARY -> ReportSummaryStep(
                report = current,
                profile = settings.profile,
                templates = templates,
                photoStore = photoStore,
                busy = busy,
                editorsEnabled = mailPreparation is MailPreparation.Idle,
                onPhotos = { showPhotoEditor = true },
                onVehicle = {
                    editingFromSummary = true
                    step = ReportWizardStep.VEHICLE
                },
                onLocation = {
                    editingFromSummary = true
                    step = ReportWizardStep.LOCATION
                },
                onProblem = {
                    editingFromSummary = true
                    step = ReportWizardStep.PROBLEM
                },
                onRecipient = { showRecipientEditor = true },
                onSender = onEditSettings,
                onOpenMail = { viewModel.prepareMail(reportId) },
            )
        }
    }

    if (showPhotoEditor) {
        PhotoEditorSheet(
            report = current,
            photoStore = photoStore,
            busy = busy,
            onClose = { showPhotoEditor = false },
            onAddPhotos = {
                showPhotoEditor = false
                onAddPhotos()
            },
            onRetry = { viewModel.retryPhotoRecognition(reportId, it) },
            onDelete = { photoToDelete = it },
        )
    }
    if (showRecipientEditor) {
        RecipientEditorSheet(
            recipient = current.recipient,
            onClose = { showRecipientEditor = false },
            onSave = {
                viewModel.updateRecipient(reportId, it)
                showRecipientEditor = false
            },
        )
    }
    photoToDelete?.let { photo ->
        PpDialog(
            title = stringResource(R.string.remove_photo),
            onDismissRequest = { photoToDelete = null },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                val deletingLastPhoto = current.photos.size == 1
                viewModel.removePhoto(reportId, photo.id)
                photoToDelete = null
                showPhotoEditor = false
                if (deletingLastPhoto) onAddPhotos()
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    fullPhoto?.let { photo ->
        FullPhotoDialog(photo, photoStore) { fullPhoto = null }
    }
    consentProvider?.let { provider ->
        val providerName = providerLabel(provider)
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
internal fun VehicleWizardStep(
    report: Report,
    photoStore: PhotoStore,
    busy: Boolean,
    cloudConfigured: Boolean,
    defaultProvider: CloudProvider,
    onOpenPhoto: (ReportPhoto) -> Unit,
    onRecognize: (CloudProvider) -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    val candidates = remember(report.plateObservations) {
        aggregatePlateCandidates(report.plateObservations)
    }
    var plate by remember(report.id) { mutableStateOf(report.plate) }
    var make by remember(report.id) { mutableStateOf(report.vehicleMake) }
    var model by remember(report.id) { mutableStateOf(report.vehicleModel) }
    var dirty by remember(report.id) { mutableStateOf(false) }
    var optionalExpanded by remember(report.id) {
        mutableStateOf(report.vehicleMake.isNotBlank() || report.vehicleModel.isNotBlank())
    }
    LaunchedEffect(report.plate, report.vehicleMake, report.vehicleModel) {
        if (!dirty) {
            plate = report.plate
            make = report.vehicleMake
            model = report.vehicleModel
            if (make.isNotBlank() || model.isNotBlank()) optionalExpanded = true
        }
    }
    val selectedCandidate = remember(plate, report.plateObservations) {
        exactPlateCandidate(plate, report.plateObservations)
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val scroll = rememberScrollState()
            if (wide) {
                Row(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    VehicleEvidence(
                        report = report,
                        candidate = selectedCandidate,
                        photoStore = photoStore,
                        onOpenPhoto = onOpenPhoto,
                        modifier = Modifier.weight(1f),
                    )
                    VehicleFields(
                        plate = plate,
                        make = make,
                        model = model,
                        candidates = candidates,
                        optionalExpanded = optionalExpanded,
                        busy = busy,
                        cloudConfigured = cloudConfigured,
                        defaultProvider = defaultProvider,
                        onPlateChange = { dirty = true; plate = it.uppercase(Locale.ROOT) },
                        onMakeChange = { dirty = true; make = it },
                        onModelChange = { dirty = true; model = it },
                        onCandidate = { dirty = true; plate = it },
                        onToggleOptional = { optionalExpanded = !optionalExpanded },
                        onRecognize = onRecognize,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    VehicleEvidence(report, selectedCandidate, photoStore, onOpenPhoto)
                    VehicleFields(
                        plate = plate,
                        make = make,
                        model = model,
                        candidates = candidates,
                        optionalExpanded = optionalExpanded,
                        busy = busy,
                        cloudConfigured = cloudConfigured,
                        defaultProvider = defaultProvider,
                        onPlateChange = { dirty = true; plate = it.uppercase(Locale.ROOT) },
                        onMakeChange = { dirty = true; make = it },
                        onModelChange = { dirty = true; model = it },
                        onCandidate = { dirty = true; plate = it },
                        onToggleOptional = { optionalExpanded = !optionalExpanded },
                        onRecognize = onRecognize,
                    )
                }
            }
        }
        WizardBottomButton(
            text = stringResource(R.string.confirm_vehicle),
            enabled = plate.isNotBlank() && !busy,
            onClick = { onConfirm(plate, make, model) },
            modifier = Modifier.testTag("vehicle_save"),
        )
    }
}

@Composable
private fun VehicleFields(
    plate: String,
    make: String,
    model: String,
    candidates: List<PlateCandidate>,
    optionalExpanded: Boolean,
    busy: Boolean,
    cloudConfigured: Boolean,
    defaultProvider: CloudProvider,
    onPlateChange: (String) -> Unit,
    onMakeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onCandidate: (String) -> Unit,
    onToggleOptional: () -> Unit,
    onRecognize: (CloudProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.vehicle_confirmation_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        PpField(
            value = plate,
            onValueChange = onPlateChange,
            label = stringResource(R.string.plate),
            isError = plate.isBlank(),
            modifier = Modifier.testTag("vehicle_plate"),
        )
        if (candidates.isNotEmpty()) {
            Text(stringResource(R.string.recognized_variants), style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                candidates.forEach { candidate ->
                    PlateCandidateCard(
                        candidate = candidate,
                        selected = PlateCandidateParser.normalize(plate) == candidate.value,
                        onClick = { onCandidate(candidate.value) },
                        modifier = Modifier.testTag("vehicle_suggestion_${candidate.value}"),
                    )
                }
            }
        }
        PpDisclosureRow(
            title = stringResource(R.string.optional_vehicle_details),
            expanded = optionalExpanded,
            stateDescription = stringResource(if (optionalExpanded) R.string.expanded else R.string.collapsed),
            onClick = onToggleOptional,
            leadingIcon = PpIcons.Car,
            supportingText = listOf(make, model).filter(String::isNotBlank).joinToString(" ").ifBlank { null },
        )
        if (optionalExpanded) {
            PpField(
                value = make,
                onValueChange = onMakeChange,
                label = stringResource(R.string.vehicle_make),
                modifier = Modifier.testTag("vehicle_make"),
            )
            PpField(
                value = model,
                onValueChange = onModelChange,
                label = stringResource(R.string.vehicle_model),
                modifier = Modifier.testTag("vehicle_model"),
            )
        }
        if (cloudConfigured) {
            PpButton(
                text = stringResource(R.string.cloud_default_action, providerLabel(defaultProvider)),
                onClick = { onRecognize(defaultProvider) },
                enabled = !busy,
                icon = PpIcons.Recognition,
                style = PpButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("vehicle_cloud_recognition"),
            )
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.recognition_in_progress), Modifier.padding(start = 10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlateCandidateCard(
    candidate: PlateCandidate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onDeviceLabel = stringResource(R.string.recognition_source_on_device)
    val sources = candidate.sources.joinToString(" · ") { source ->
        when (source) {
            RecognitionSource.ML_KIT_OCR -> "OCR"
            RecognitionSource.LOCAL_PLATE_MODEL -> onDeviceLabel
            RecognitionSource.WORKERS_AI -> "Workers AI"
            RecognitionSource.OPENAI -> "OpenAI"
        }
    }
    PpCard(
        modifier = modifier.widthIn(min = 190.dp, max = 240.dp),
        onClick = onClick,
        selected = selected,
        role = Role.RadioButton,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(candidate.value, style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(
                    R.plurals.plate_found_on_photos,
                    candidate.supportingPhotoCount,
                    candidate.supportingPhotoCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                sources,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun VehicleEvidence(
    report: Report,
    candidate: PlateCandidate?,
    photoStore: PhotoStore,
    onOpenPhoto: (ReportPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val evidence = candidate?.observations
        .orEmpty()
        .groupBy(PlateObservation::photoId)
        .values
        .mapNotNull { matches -> matches.firstOrNull { it.bounds != null } ?: matches.firstOrNull() }
    val mainObservation = evidence.firstOrNull()
    val mainPhoto = report.photos.firstOrNull { it.id == mainObservation?.photoId } ?: report.primaryPhoto
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.plate_evidence), style = MaterialTheme.typography.titleMedium)
        if (mainPhoto != null) {
            PpCard(onClick = { onOpenPhoto(mainPhoto) }, modifier = Modifier.fillMaxWidth()) {
                EvidenceImage(
                    photo = mainPhoto,
                    bounds = mainObservation?.bounds,
                    photoStore = photoStore,
                    contentDescription = stringResource(R.string.plate_evidence),
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp),
                )
            }
        }
        if (evidence.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                evidence.drop(1).forEachIndexed { index, observation ->
                    report.photos.firstOrNull { it.id == observation.photoId }?.let { photo ->
                        PpCard(onClick = { onOpenPhoto(photo) }, modifier = Modifier.width(150.dp)) {
                            EvidenceImage(
                                photo = photo,
                                bounds = observation.bounds,
                                photoStore = photoStore,
                                contentDescription = stringResource(R.string.photo_description, index + 2),
                                modifier = Modifier.fillMaxWidth().height(92.dp).padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.tap_evidence_to_open_photo),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EvidenceImage(
    photo: ReportPhoto,
    bounds: NormalizedPhotoRect?,
    photoStore: PhotoStore,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val crop by rememberManagedBitmap(photo.filePath to bounds) {
        bounds?.let { photoStore.decodePlateCrop(photo, it) }
    }
    if (crop != null) {
        Image(
            bitmap = requireNotNull(crop).asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        PhotoThumbnail(photo, photoStore, contentDescription, modifier)
    }
}

@Composable
private fun LocationWizardStep(
    report: Report,
    viewModel: PoleParklaViewModel,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    var address by remember(report.id) { mutableStateOf(report.address) }
    var latitude by remember(report.id) { mutableStateOf(report.latitude?.toString().orEmpty()) }
    var longitude by remember(report.id) { mutableStateOf(report.longitude?.toString().orEmpty()) }
    var accuracyMeters by remember(report.id) { mutableStateOf(report.accuracyMeters) }
    var occurredAtEpochMillis by remember(report.id) { mutableLongStateOf(report.occurredAtEpochMillis) }
    var lookupFeedback by remember(report.id) { mutableStateOf(AddressLookupFeedback()) }
    var advanced by remember(report.id) { mutableStateOf(false) }
    var showMap by remember(report.id) { mutableStateOf(false) }
    var loading by remember(report.id) { mutableStateOf(false) }
    var validationError by remember(report.id) { mutableStateOf<ReportLocationInputError?>(null) }
    var photoMetadata by remember(report.id, report.primaryPhoto?.id) { mutableStateOf<PhotoEditorMetadata?>(null) }
    var editRevision by remember(report.id) { mutableLongStateOf(0L) }
    var permissionRefresh by remember { mutableLongStateOf(0L) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val addressFocusRequester = remember(report.id) { FocusRequester() }
    val latitudeFocusRequester = remember(report.id) { FocusRequester() }
    val locationPermissionGranted = remember(permissionRefresh) { context.hasLocationPermission() }
    val invalidLocationMessage = stringResource(R.string.invalid_location_value)
    val invalidCoordinatesMessage = stringResource(R.string.invalid_coordinates_values)
    val displayLocale = LocalConfiguration.current.locales[0]
    val use24HourTime = DateFormat.is24HourFormat(context)
    val dateFormatter = remember(displayLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(displayLocale)
    }
    val timeFormatter = remember(displayLocale, use24HourTime) {
        DateTimeFormatter.ofPattern(if (use24HourTime) "HH:mm" else "h:mm a", displayLocale)
    }
    val occurredAtDateTime = remember(occurredAtEpochMillis) {
        Instant.ofEpochMilli(occurredAtEpochMillis).atZone(REPORT_TIME_ZONE)
    }

    fun showDatePicker() {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                occurredAtEpochMillis = LocalDate.of(year, month + 1, dayOfMonth)
                    .atTime(occurredAtDateTime.toLocalTime())
                    .atZone(REPORT_TIME_ZONE)
                    .toInstant()
                    .toEpochMilli()
                validationError = null
            },
            occurredAtDateTime.year,
            occurredAtDateTime.monthValue - 1,
            occurredAtDateTime.dayOfMonth,
        ).show()
    }

    fun showTimePicker() {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                occurredAtEpochMillis = occurredAtDateTime.toLocalDate()
                    .atTime(hourOfDay, minute)
                    .atZone(REPORT_TIME_ZONE)
                    .toInstant()
                    .toEpochMilli()
                validationError = null
            },
            occurredAtDateTime.hour,
            occurredAtDateTime.minute,
            use24HourTime,
        ).show()
    }

    fun currentDraft() = AddressDraft(
        address = address,
        latitude = ReportLocationInput.parseLatitude(latitude),
        longitude = ReportLocationInput.parseLongitude(longitude),
        accuracyMeters = accuracyMeters,
    )

    fun applyLookup(result: AddressLookupResult?, applyLocation: Boolean, revision: Long) {
        loading = false
        if (revision != editRevision) return
        val resolution = result?.resolution
        val updated = addressDraftFromLookup(currentDraft(), result, applyLocation)
        address = updated.address
        latitude = updated.latitude?.toString().orEmpty()
        longitude = updated.longitude?.toString().orEmpty()
        accuracyMeters = updated.accuracyMeters
        lookupFeedback = AddressLookupFeedback(
            candidates = resolution?.candidates.orEmpty(),
            addressLookupFailed = result != null && resolution == null,
        )
        validationError = null
    }

    fun loadCurrentLocation() {
        val revision = editRevision
        loading = true
        viewModel.loadCurrentLocation { applyLookup(it, true, revision) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRefresh++
        val granted = LOCATION_PERMISSIONS.any { result[it] == true }
        if (granted) loadCurrentLocation()
        if (result.isNotEmpty() && !granted && !context.shouldShowLocationPermissionRationale()) {
            permanentlyDenied = true
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRefresh++ }

    LaunchedEffect(report.id, report.primaryPhoto?.id) {
        photoMetadata = report.primaryPhoto?.let { viewModel.readPhotoEditorMetadata(it) }
    }
    LaunchedEffect(report.id) {
        val lat = report.latitude
        val lon = report.longitude
        if (lat != null && lon != null) {
            val revision = editRevision
            loading = true
            viewModel.loadAddressCandidates(
                LocationSnapshot(lat, lon, report.accuracyMeters, report.occurredAtEpochMillis),
                forceRefresh = false,
            ) { applyLookup(it, false, revision) }
        }
    }
    LaunchedEffect(validationError, advanced) {
        when (validationError) {
            ReportLocationInputError.LOCATION -> addressFocusRequester.requestFocus()
            ReportLocationInputError.COORDINATES -> {
                if (advanced) latitudeFocusRequester.requestFocus() else advanced = true
            }
            else -> Unit
        }
    }

    val addressSection: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().testTag("location_section"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PpIcons.Location, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.location_section), style = MaterialTheme.typography.titleMedium)
            }
            PpField(
                value = address,
                onValueChange = {
                    address = it
                    editRevision++
                    validationError = null
                },
                label = stringResource(R.string.address),
                isError = validationError == ReportLocationInputError.LOCATION,
                modifier = Modifier
                    .focusRequester(addressFocusRequester)
                    .semantics {
                        if (validationError == ReportLocationInputError.LOCATION) {
                            error(invalidLocationMessage)
                        }
                    }
                    .testTag("location_address"),
            )
            lookupFeedback.candidates
                .filter { it.type == AddressCandidateType.STREET }
                .take(3)
                .forEachIndexed { index, candidate ->
                    if (index == 0) {
                        Text(stringResource(R.string.nearby_streets), style = MaterialTheme.typography.titleSmall)
                    }
                    AddressCandidateRow(candidate, address == candidate.address, "location_street_candidate_$index") {
                        address = candidate.address
                        editRevision++
                        validationError = null
                    }
                }
            lookupFeedback.candidates
                .filter { it.type == AddressCandidateType.BUILDING }
                .take(3)
                .forEachIndexed { index, candidate ->
                    if (index == 0) {
                        Text(stringResource(R.string.nearby_addresses), style = MaterialTheme.typography.titleSmall)
                    }
                    AddressCandidateRow(candidate, address == candidate.address, "location_building_candidate_$index") {
                        address = candidate.address
                        editRevision++
                        validationError = null
                    }
                }
            if (lookupFeedback.addressLookupFailed) {
                PpCard(Modifier.fillMaxWidth().testTag("location_lookup_error")) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.address_lookup_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PpButton(
                            text = stringResource(R.string.retry),
                            onClick = {
                                val draft = currentDraft()
                                if (draft.latitude != null && draft.longitude != null) {
                                    val revision = editRevision
                                    loading = true
                                    viewModel.loadAddressCandidates(
                                        LocationSnapshot(
                                            draft.latitude,
                                            draft.longitude,
                                            draft.accuracyMeters,
                                            System.currentTimeMillis(),
                                        ),
                                        forceRefresh = true,
                                    ) { applyLookup(it, false, revision) }
                                }
                            },
                            enabled = !loading,
                            style = PpButtonStyle.Secondary,
                            modifier = Modifier.testTag("location_lookup_retry"),
                        )
                    }
                }
            }
            PpButton(
                text = stringResource(R.string.refine_on_map),
                onClick = { showMap = true },
                style = PpButtonStyle.Secondary,
                icon = PpIcons.Location,
                modifier = Modifier.fillMaxWidth().testTag("location_refine_map"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (photoMetadata?.location != null) {
                    PpButton(
                        text = stringResource(if (loading) R.string.locating else R.string.from_photo),
                        onClick = {
                            val location = requireNotNull(photoMetadata?.location)
                            val revision = editRevision
                            loading = true
                            viewModel.loadPhotoLocation(location) { applyLookup(it, true, revision) }
                        },
                        enabled = !loading,
                        style = PpButtonStyle.Secondary,
                        icon = PpIcons.Gallery,
                        modifier = Modifier.weight(1f).testTag("location_from_photo"),
                    )
                }
                PpButton(
                    text = stringResource(if (loading) R.string.locating else R.string.current_location),
                    onClick = {
                        if (locationPermissionGranted) loadCurrentLocation() else showPermissionDialog = true
                    },
                    enabled = !loading,
                    style = PpButtonStyle.Secondary,
                    icon = PpIcons.Location,
                    modifier = Modifier.weight(1f).testTag("location_use_current"),
                )
            }
        }
    }
    val timeAndCoordinatesSection: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PpIcons.Time, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.time_section), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimePickerCard(
                    label = stringResource(R.string.date_label),
                    value = dateFormatter.format(occurredAtDateTime),
                    icon = PpIcons.Calendar,
                    onClick = ::showDatePicker,
                    modifier = Modifier.weight(1f).testTag("location_date"),
                )
                DateTimePickerCard(
                    label = stringResource(R.string.time_label),
                    value = timeFormatter.format(occurredAtDateTime),
                    icon = PpIcons.Time,
                    onClick = ::showTimePicker,
                    modifier = Modifier.weight(1f).testTag("location_time"),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photoMetadata?.capturedAtEpochMillis?.let { photoTime ->
                    PpButton(
                        text = stringResource(R.string.from_photo),
                        onClick = {
                            occurredAtEpochMillis = photoTime
                            validationError = null
                        },
                        style = PpButtonStyle.Secondary,
                        icon = PpIcons.Gallery,
                        modifier = Modifier.weight(1f).testTag("location_time_from_photo"),
                    )
                }
                PpButton(
                    text = stringResource(R.string.time_now),
                    onClick = {
                        occurredAtEpochMillis = System.currentTimeMillis()
                        validationError = null
                    },
                    style = PpButtonStyle.Secondary,
                    icon = PpIcons.Time,
                    modifier = Modifier.weight(1f).testTag("location_time_now"),
                )
            }
            PpDisclosureRow(
                title = stringResource(R.string.coordinates),
                expanded = advanced,
                stateDescription = stringResource(if (advanced) R.string.expanded else R.string.collapsed),
                onClick = { advanced = !advanced },
                leadingIcon = PpIcons.Location,
                supportingText = accuracyMeters?.let { stringResource(R.string.accuracy, it.toInt()) },
                modifier = Modifier.testTag("location_coordinates_toggle"),
            )
            if (advanced) {
                PpField(
                    value = latitude,
                    onValueChange = {
                        latitude = it
                        accuracyMeters = null
                        lookupFeedback = lookupFeedback.clearedForManualCoordinates()
                        editRevision++
                        validationError = null
                    },
                    label = stringResource(R.string.latitude),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationError == ReportLocationInputError.COORDINATES,
                    modifier = Modifier
                        .focusRequester(latitudeFocusRequester)
                        .semantics {
                            if (validationError == ReportLocationInputError.COORDINATES) {
                                error(invalidCoordinatesMessage)
                            }
                        }
                        .testTag("location_latitude"),
                )
                PpField(
                    value = longitude,
                    onValueChange = {
                        longitude = it
                        accuracyMeters = null
                        lookupFeedback = lookupFeedback.clearedForManualCoordinates()
                        editRevision++
                        validationError = null
                    },
                    label = stringResource(R.string.longitude),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationError == ReportLocationInputError.COORDINATES,
                    modifier = Modifier
                        .semantics {
                            if (validationError == ReportLocationInputError.COORDINATES) {
                                error(invalidCoordinatesMessage)
                            }
                        }
                        .testTag("location_longitude"),
                )
            }
            if (validationError == ReportLocationInputError.COORDINATES) {
                Text(
                    invalidCoordinatesMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                        .testTag("location_coordinates_error"),
                )
            }
            if (validationError == ReportLocationInputError.LOCATION) {
                Text(
                    invalidLocationMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                        .testTag("location_address_error"),
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(1f)) { addressSection() }
                        Box(Modifier.weight(1f)) { timeAndCoordinatesSection() }
                    }
                } else {
                    addressSection()
                    timeAndCoordinatesSection()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        WizardBottomButton(
            text = stringResource(R.string.confirm_location),
            enabled = !loading,
            onClick = {
                validationError = viewModel.updateLocation(
                    report.id,
                    address,
                    latitude,
                    longitude,
                    viewModel.formatOccurredAt(occurredAtEpochMillis),
                    accuracyMeters,
                )
                if (validationError == null) onConfirm()
            },
            modifier = Modifier.testTag("location_save"),
        )
    }

    if (showMap) {
        AddressMapPicker(
            initialAddress = address,
            initialLatitude = ReportLocationInput.parseLatitude(latitude),
            initialLongitude = ReportLocationInput.parseLongitude(longitude),
            resolveAddress = viewModel::resolveAddress,
            onDismiss = { showMap = false },
            onUseSelection = { selection ->
                val selected = AddressSelectionPolicy.useMapSelection(currentDraft(), selection)
                address = selected.address
                latitude = selected.latitude?.toString().orEmpty()
                longitude = selected.longitude?.toString().orEmpty()
                accuracyMeters = selected.accuracyMeters
                lookupFeedback = AddressLookupFeedback(
                    candidates = selection.candidates,
                    addressLookupFailed = selection.addressLookupFailed,
                )
                editRevision++
                validationError = null
                showMap = false
            },
        )
    }
    if (showPermissionDialog) {
        PpDialog(
            title = stringResource(R.string.location_permission_title),
            onDismissRequest = { showPermissionDialog = false },
            confirmText = stringResource(R.string.grant_location),
            onConfirm = {
                showPermissionDialog = false
                if (permissionRequested && !context.shouldShowLocationPermissionRationale()) {
                    permanentlyDenied = true
                } else {
                    permissionRequested = true
                    permissionLauncher.launch(LOCATION_PERMISSIONS)
                }
            },
            dismissText = stringResource(R.string.cancel),
            content = { Text(stringResource(R.string.location_permission_message)) },
        )
    }
    if (permanentlyDenied) {
        PpDialog(
            title = stringResource(R.string.location_permission_title),
            onDismissRequest = { permanentlyDenied = false },
            confirmText = stringResource(R.string.open_settings),
            onConfirm = {
                permanentlyDenied = false
                context.openAppSettings()
            },
            dismissText = stringResource(R.string.cancel),
            content = { Text(stringResource(R.string.location_permission_settings_message)) },
        )
    }
}

@Composable
private fun DateTimePickerCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PpCard(modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AddressCandidateRow(
    candidate: AddressCandidate,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    PpChoiceRow(
        title = candidate.address,
        selected = selected,
        onClick = onClick,
        supportingText = stringResource(R.string.address_distance, candidate.distanceMeters),
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
internal fun ProblemWizardStep(
    report: Report,
    templates: List<CustomViolationTemplate>,
    onSelect: (ViolationType, String?) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PpChoiceRow(
            title = stringResource(R.string.cycle_path),
            selected = report.violationType == ViolationType.CYCLE_PATH,
            onClick = { onSelect(ViolationType.CYCLE_PATH, null) },
            leadingIcon = PpIcons.CyclePath,
            supportingText = if (report.suggestedViolationType == ViolationType.CYCLE_PATH) {
                stringResource(R.string.suggested)
            } else null,
            modifier = Modifier.testTag("violation_cycle_path"),
        )
        PpChoiceRow(
            title = stringResource(R.string.pedestrian_path),
            selected = report.violationType == ViolationType.PEDESTRIAN_PATH,
            onClick = { onSelect(ViolationType.PEDESTRIAN_PATH, null) },
            leadingIcon = PpIcons.PedestrianPath,
            supportingText = if (report.suggestedViolationType == ViolationType.PEDESTRIAN_PATH) {
                stringResource(R.string.suggested)
            } else null,
            modifier = Modifier.testTag("violation_pedestrian_path"),
        )
        templates.forEach { template ->
            PpChoiceRow(
                title = template.displayName,
                selected = report.violationType == ViolationType.CUSTOM && report.customTemplateId == template.id,
                onClick = { onSelect(ViolationType.CUSTOM, template.id) },
                leadingIcon = PpIcons.Flag,
                modifier = Modifier.testTag("violation_template_${template.id}"),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun ReportSummaryStep(
    report: Report,
    profile: ReporterProfile,
    templates: List<CustomViolationTemplate>,
    photoStore: PhotoStore,
    busy: Boolean,
    editorsEnabled: Boolean,
    onPhotos: () -> Unit,
    onVehicle: () -> Unit,
    onLocation: () -> Unit,
    onProblem: () -> Unit,
    onRecipient: () -> Unit,
    onSender: () -> Unit,
    onOpenMail: () -> Unit,
) {
    val description = report.violationDescription(templates)
    val ready = report.isReady(profile, description)
    val bestCandidate = remember(report.plate, report.plateObservations) {
        exactPlateCandidate(report.plate, report.plateObservations)
    }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PpBadge(
                        text = stringResource(if (ready) R.string.summary_ready else R.string.summary_not_ready),
                        isComplete = ready,
                        isError = !ready,
                    )
                    if (report.status == ReportStatus.HANDED_OFF_TO_MAIL) {
                        PpBadge(text = stringResource(R.string.status_handed_off), isComplete = true)
                    }
                }
                SummaryPhotoCard(report, photoStore, if (editorsEnabled) onPhotos else null)
                val cards: List<@Composable () -> Unit> = listOf(
                    {
                        SummaryVehicleCard(report, bestCandidate, photoStore, if (editorsEnabled) onVehicle else null)
                    },
                    {
                        SummaryRow(
                            title = stringResource(R.string.location_and_time),
                            value = buildString {
                                append(report.address.ifBlank { report.coordinatesText() })
                                append("\n")
                                append(ReportLocationInput.format(report.occurredAtEpochMillis))
                            },
                            icon = PpIcons.Location,
                            complete = report.locationConfirmed,
                            warning = report.locationNeedsReview,
                            onClick = if (editorsEnabled) onLocation else null,
                            testTag = "summary_location",
                        )
                    },
                    {
                        SummaryRow(
                            title = stringResource(R.string.violation),
                            value = report.violationSummary(templates) ?: stringResource(R.string.missing_violation),
                            icon = PpIcons.Violation,
                            complete = description != null,
                            onClick = if (editorsEnabled) onProblem else null,
                            testTag = "summary_problem",
                        )
                    },
                    {
                        SummaryRow(
                            title = stringResource(R.string.recipient),
                            value = report.recipient.ifBlank { stringResource(R.string.missing_delivery) },
                            icon = PpIcons.Email,
                            complete = report.recipient.isNotBlank(),
                            onClick = if (editorsEnabled) onRecipient else null,
                            testTag = "summary_recipient",
                        )
                    },
                    {
                        SummaryRow(
                            title = stringResource(R.string.sender),
                            value = listOf(profile.name, profile.phone).filter(String::isNotBlank).joinToString(" · ")
                                .ifBlank { stringResource(R.string.not_configured) },
                            icon = PpIcons.Person,
                            complete = profile.name.isNotBlank() && profile.phone.isNotBlank(),
                            onClick = if (editorsEnabled) onSender else null,
                            testTag = "summary_sender",
                        )
                    },
                    {
                        SummaryRow(
                            title = stringResource(R.string.generated_subject),
                            value = report.subject.ifBlank { stringResource(R.string.incomplete) },
                            icon = PpIcons.Email,
                            complete = report.subject.isNotBlank(),
                            onClick = null,
                            testTag = "summary_subject",
                        )
                    },
                )
                if (wide) {
                    cards.chunked(2).forEach { rowCards ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowCards.forEach { card -> Box(Modifier.weight(1f)) { card() } }
                            if (rowCards.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    cards.forEach { it() }
                }
                Text(
                    stringResource(R.string.letter_edit_in_mail_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        WizardBottomButton(
            text = stringResource(R.string.open_mail),
            enabled = ready && !busy,
            icon = PpIcons.Send,
            onClick = onOpenMail,
            modifier = Modifier.testTag("review_primary_action"),
        )
    }
}

@Composable
private fun SummaryPhotoCard(report: Report, photoStore: PhotoStore, onClick: (() -> Unit)?) {
    PpCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("summary_photos")) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            report.primaryPhoto?.let { photo ->
                PhotoThumbnail(
                    photo,
                    photoStore,
                    stringResource(R.string.report_photo),
                    Modifier.size(width = 112.dp, height = 84.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.photos), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.attachments_count, report.photos.size, report.photos.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(PpIcons.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun SummaryVehicleCard(
    report: Report,
    candidate: PlateCandidate?,
    photoStore: PhotoStore,
    onClick: (() -> Unit)?,
) {
    val observation = candidate?.observations?.firstOrNull { it.bounds != null }
    val photo = report.photos.firstOrNull { it.id == observation?.photoId } ?: report.primaryPhoto
    val vehicleStateDescription = stringResource(
        if (report.vehicleConfirmed) R.string.state_complete else R.string.state_incomplete,
    )
    PpCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("summary_vehicle").semantics {
            stateDescription = vehicleStateDescription
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (photo != null) {
                Box(Modifier.width(112.dp)) {
                    EvidenceImage(
                        photo,
                        observation?.bounds,
                        photoStore,
                        stringResource(R.string.plate_evidence),
                        Modifier.fillMaxWidth().height(72.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.vehicle_editor_title), style = MaterialTheme.typography.titleMedium)
                Text(report.plate.ifBlank { stringResource(R.string.missing_plate) })
                listOf(report.vehicleMake, report.vehicleModel).filter(String::isNotBlank).joinToString(" ")
                    .takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
            }
            SummaryStatusIcon(
                complete = report.vehicleConfirmed,
                canEdit = true,
            )
        }
    }
}

@Composable
private fun SummaryRow(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    complete: Boolean,
    onClick: (() -> Unit)?,
    testTag: String,
    warning: Boolean = false,
) {
    val rowStateDescription = stringResource(
        if (complete) R.string.state_complete else R.string.state_incomplete,
    )
    PpCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag).semantics {
            stateDescription = rowStateDescription
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (warning) PpIcons.Warning else icon,
                contentDescription = null,
                tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    value,
                    color = if (complete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SummaryStatusIcon(
                complete = complete,
                warning = warning,
                canEdit = onClick != null,
            )
        }
    }
}

@Composable
private fun SummaryStatusIcon(
    complete: Boolean,
    canEdit: Boolean,
    warning: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (complete && !warning) {
            Icon(
                PpIcons.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (canEdit) {
            Icon(
                PpIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WizardBottomButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PpButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            icon = icon,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun RecipientEditorSheet(
    recipient: String,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(recipient) { mutableStateOf(recipient) }
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.recipient), dismiss)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PpField(
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.recipient),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = value.isBlank(),
                modifier = Modifier.testTag("delivery_recipient"),
            )
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(value.trim()) },
                enabled = value.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("delivery_save"),
            )
        }
    }
}

@Composable
private fun FullPhotoDialog(photo: ReportPhoto, photoStore: PhotoStore, onClose: () -> Unit) {
    PpDialog(
        title = stringResource(R.string.report_photo),
        onDismissRequest = onClose,
        confirmText = stringResource(R.string.close),
        onConfirm = onClose,
        content = {
            PhotoThumbnail(
                photo,
                photoStore,
                stringResource(R.string.report_photo),
                Modifier.fillMaxWidth().height(380.dp),
            )
        },
    )
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
        MailPreparation.Preparing -> Unit
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
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        },
        style = PpButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun wizardTitle(step: ReportWizardStep): String = stringResource(
    when (step) {
        ReportWizardStep.VEHICLE -> R.string.wizard_vehicle_title
        ReportWizardStep.LOCATION -> R.string.wizard_location_title
        ReportWizardStep.PROBLEM -> R.string.wizard_problem_title
        ReportWizardStep.SUMMARY -> R.string.wizard_summary_title
    },
)

@Composable
private fun providerLabel(provider: CloudProvider): String = stringResource(
    if (provider == CloudProvider.WORKERS_AI) R.string.workers_ai else R.string.openai,
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

internal fun addressDraftFromLookup(
    draft: AddressDraft,
    result: AddressLookupResult?,
    applyLocation: Boolean,
): AddressDraft {
    if (result == null) return draft
    val suggestedAddress = result.resolution?.suggested?.address
    return if (applyLocation) {
        draft.copy(
            address = suggestedAddress ?: draft.address,
            latitude = result.location.latitude,
            longitude = result.location.longitude,
            accuracyMeters = result.location.accuracyMeters,
        )
    } else {
        draft.copy(
            address = if (draft.address.isBlank()) suggestedAddress.orEmpty() else draft.address,
        )
    }
}

internal data class AddressLookupFeedback(
    val candidates: List<AddressCandidate> = emptyList(),
    val addressLookupFailed: Boolean = false,
) {
    fun clearedForManualCoordinates() = AddressLookupFeedback()
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.shouldShowLocationPermissionRationale(): Boolean {
    val activity = findActivity() ?: return false
    return LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}
