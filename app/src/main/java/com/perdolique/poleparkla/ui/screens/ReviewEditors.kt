package com.perdolique.poleparkla.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.domain.AddressDraft
import com.perdolique.poleparkla.domain.AddressSelectionPolicy
import com.perdolique.poleparkla.domain.ReportLocationInput
import com.perdolique.poleparkla.domain.ReportLocationInputError
import com.perdolique.poleparkla.model.AddressCandidate
import com.perdolique.poleparkla.model.AddressCandidateType
import com.perdolique.poleparkla.model.AddressResolution
import com.perdolique.poleparkla.model.CloudProvider
import com.perdolique.poleparkla.model.CustomViolationTemplate
import com.perdolique.poleparkla.model.LetterDraft
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ViolationType
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.components.PhotoThumbnail
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpChoiceRow
import com.perdolique.poleparkla.ui.components.PpDisclosureRow
import com.perdolique.poleparkla.ui.components.PpField
import com.perdolique.poleparkla.ui.components.PpIconButton
import com.perdolique.poleparkla.ui.components.PpIcons
import com.perdolique.poleparkla.ui.components.PpSheet
import com.perdolique.poleparkla.ui.components.PpSheetHeader
import java.util.Locale

internal enum class ReportEditor {
    PHOTOS,
    VEHICLE,
    VIOLATION,
    LOCATION,
    DELIVERY,
}

internal data class LocationEditorSuggestion(
    val address: String?,
    val latitude: String,
    val longitude: String,
    val accuracyMeters: Float?,
    val candidates: List<AddressCandidate> = emptyList(),
    val addressLookupFailed: Boolean = false,
    val requestId: Long = 0,
    val applyLocation: Boolean = true,
)

@Composable
internal fun PhotoEditorSheet(
    report: Report,
    photoStore: PhotoStore,
    busy: Boolean,
    onClose: () -> Unit,
    onAddPhotos: () -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (ReportPhoto) -> Unit,
) {
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.photos), dismiss)
        Text(
            stringResource(R.string.photo_recognition_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("photo_editor_list"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            report.photos.forEachIndexed { index, photo ->
                PhotoChoice(
                    photo = photo,
                    photoStore = photoStore,
                    index = index,
                    busy = busy,
                    onRetry = { onRetry(photo.id) },
                    onDelete = { onDelete(photo) },
                )
            }
        }
        if (report.photos.size < 3) {
            PpButton(
                text = stringResource(R.string.add_more_photos),
                onClick = onAddPhotos,
                enabled = !busy,
                icon = PpIcons.Add,
                style = PpButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
        } else {
            Text(
                stringResource(R.string.three_photo_limit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun PhotoChoice(
    photo: ReportPhoto,
    photoStore: PhotoStore,
    index: Int,
    busy: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier.width(184.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .padding(1.dp),
        ) {
            PhotoThumbnail(
                photo = photo,
                photoStore = photoStore,
                contentDescription = stringResource(R.string.photo_description, index + 1),
                modifier = Modifier.fillMaxSize(),
            )
            PpIconButton(
                icon = PpIcons.Delete,
                contentDescription = stringResource(R.string.remove_photo_number, index + 1),
                onClick = onDelete,
                enabled = !busy,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopEnd).testTag("photo_delete_${photo.id}"),
            )
        }
        PpButton(
            text = stringResource(R.string.recognize_again),
            onClick = onRetry,
            enabled = !busy,
            style = PpButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().testTag("photo_retry_${photo.id}"),
        )
    }
}

@Composable
internal fun VehicleEditorSheet(
    report: Report,
    busy: Boolean,
    cloudConfigured: Boolean,
    defaultProvider: CloudProvider,
    onClose: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onRecognize: (CloudProvider) -> Unit,
) {
    var plate by remember(report.id) { mutableStateOf(report.plate) }
    var make by remember(report.id) { mutableStateOf(report.vehicleMake) }
    var model by remember(report.id) { mutableStateOf(report.vehicleModel) }
    var dirty by remember(report.id) { mutableStateOf(false) }
    LaunchedEffect(report.plate, report.vehicleMake, report.vehicleModel) {
        if (!dirty) {
            plate = report.plate
            make = report.vehicleMake
            model = report.vehicleModel
        }
    }

    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.vehicle_editor_title), dismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PpField(
                value = plate,
                onValueChange = {
                    dirty = true
                    plate = it.uppercase(Locale.ROOT)
                },
                label = stringResource(R.string.plate),
                isError = plate.isBlank(),
                modifier = Modifier.testTag("vehicle_plate"),
            )
            if (report.plateSuggestions.isNotEmpty()) {
                Text(stringResource(R.string.recognized_variants), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    report.plateSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            text = "${suggestion.value} · ${sourceLabel(suggestion.source)}",
                            selected = suggestion.value == plate,
                            onClick = {
                                dirty = true
                                plate = suggestion.value
                            },
                            modifier = Modifier.testTag("vehicle_suggestion_${suggestion.value}"),
                        )
                    }
                }
            }
            PpField(
                value = make,
                onValueChange = { dirty = true; make = it },
                label = stringResource(R.string.vehicle_make),
                modifier = Modifier.testTag("vehicle_make"),
            )
            PpField(
                value = model,
                onValueChange = { dirty = true; model = it },
                label = stringResource(R.string.vehicle_model),
                modifier = Modifier.testTag("vehicle_model"),
            )
            if (cloudConfigured) {
                PpButton(
                    text = stringResource(R.string.cloud_default_action, providerLabel(defaultProvider)),
                    onClick = { onRecognize(defaultProvider) },
                    enabled = !busy,
                    icon = PpIcons.Recognition,
                    style = PpButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(R.string.cloud_not_configured),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.recognition_in_progress),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(plate, make, model) },
                enabled = plate.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp).testTag("vehicle_save"),
            )
        }
    }
}

@Composable
internal fun ViolationEditorSheet(
    report: Report,
    templates: List<CustomViolationTemplate>,
    onClose: () -> Unit,
    onSelect: (ViolationType, String?) -> Unit,
) {
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.violation), dismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
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
                    selected = report.violationType == ViolationType.CUSTOM &&
                        report.customTemplateId == template.id,
                    onClick = { onSelect(ViolationType.CUSTOM, template.id) },
                    leadingIcon = PpIcons.Flag,
                    modifier = Modifier.testTag("violation_template_${template.id}"),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun LocationEditorSheet(
    report: Report,
    formattedTime: String,
    photoTime: String?,
    photoLocation: LocationEditorSuggestion?,
    locationLoading: Boolean,
    locationRequestId: Long = 0,
    suggestedLocation: LocationEditorSuggestion?,
    onUsePhotoLocation: () -> Unit,
    onClose: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onRetryAddressLookup: (LocationSnapshot) -> Unit = {},
    resolveAddress: suspend (LocationSnapshot, Boolean) -> AddressResolution? = { _, _ -> null },
    onUseCurrentTime: () -> String,
    onSave: (String, String, String, String, Float?) -> ReportLocationInputError?,
) {
    var address by remember(report.id) { mutableStateOf(report.address) }
    var latitude by remember(report.id) { mutableStateOf(report.latitude?.toString().orEmpty()) }
    var longitude by remember(report.id) { mutableStateOf(report.longitude?.toString().orEmpty()) }
    var accuracyMeters by remember(report.id) { mutableStateOf(report.accuracyMeters) }
    var occurredAt by remember(report.id) { mutableStateOf(formattedTime) }
    var advanced by remember(report.id) { mutableStateOf(false) }
    var candidates by remember(report.id) { mutableStateOf(emptyList<AddressCandidate>()) }
    var addressLookupFailed by remember(report.id) { mutableStateOf(false) }
    var showAddressMap by remember(report.id) { mutableStateOf(false) }
    var editRevision by remember(report.id) { mutableLongStateOf(0L) }
    var requestStartRevision by remember(report.id) { mutableLongStateOf(0L) }
    var validationError by remember(report.id) { mutableStateOf<ReportLocationInputError?>(null) }
    val coordinatesFocusRequester = remember(report.id) { FocusRequester() }
    val timeFocusRequester = remember(report.id) { FocusRequester() }
    val invalidCoordinatesMessage = stringResource(R.string.invalid_coordinates_values)
    val invalidTimeMessage = stringResource(R.string.invalid_time_value)

    fun applySuggestion(suggestion: LocationEditorSuggestion) {
        if (suggestion.applyLocation) {
            suggestion.address?.let { address = it }
            latitude = suggestion.latitude
            longitude = suggestion.longitude
            accuracyMeters = suggestion.accuracyMeters
        }
        candidates = suggestion.candidates
        addressLookupFailed = suggestion.addressLookupFailed
        validationError = null
    }

    fun currentAddressDraft() = AddressDraft(
        address = address,
        latitude = ReportLocationInput.parseLatitude(latitude),
        longitude = ReportLocationInput.parseLongitude(longitude),
        accuracyMeters = accuracyMeters,
    )

    fun selectCandidate(candidate: AddressCandidate) {
        address = candidate.address
        editRevision++
        validationError = null
    }

    LaunchedEffect(locationRequestId) {
        requestStartRevision = editRevision
    }
    LaunchedEffect(suggestedLocation) {
        suggestedLocation?.takeIf {
            it.requestId == locationRequestId && editRevision == requestStartRevision
        }?.let(::applySuggestion)
    }
    LaunchedEffect(validationError, advanced) {
        when (validationError) {
            ReportLocationInputError.COORDINATES -> {
                if (advanced) coordinatesFocusRequester.requestFocus() else advanced = true
            }
            ReportLocationInputError.TIME -> timeFocusRequester.requestFocus()
            null -> Unit
        }
    }
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.location_and_time), dismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (report.locationNeedsReview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PpIcons.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.location_needs_review),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().testTag("location_section"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("location_section_title"),
                ) {
                    Icon(
                        PpIcons.Location,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
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
                    modifier = Modifier.testTag("location_address"),
                )
                val streetCandidates = candidates.filter { it.type == AddressCandidateType.STREET }
                val buildingCandidates = candidates.filter { it.type == AddressCandidateType.BUILDING }
                if (streetCandidates.isNotEmpty()) {
                    Text(
                        stringResource(R.string.nearby_streets),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    streetCandidates.forEachIndexed { index, candidate ->
                        PpChoiceRow(
                            title = candidate.address,
                            selected = address == candidate.address,
                            onClick = { selectCandidate(candidate) },
                            supportingText = stringResource(
                                R.string.address_distance,
                                candidate.distanceMeters,
                            ),
                            modifier = Modifier.testTag("location_street_candidate_$index"),
                        )
                    }
                }
                if (buildingCandidates.isNotEmpty()) {
                    Text(
                        stringResource(R.string.nearby_addresses),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    buildingCandidates.forEachIndexed { index, candidate ->
                        PpChoiceRow(
                            title = candidate.address,
                            selected = address == candidate.address,
                            onClick = { selectCandidate(candidate) },
                            supportingText = stringResource(
                                R.string.address_distance,
                                candidate.distanceMeters,
                            ),
                            modifier = Modifier.testTag("location_building_candidate_$index"),
                        )
                    }
                }
                if (addressLookupFailed) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.shapes.medium,
                            )
                            .padding(12.dp)
                            .testTag("location_lookup_error"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.address_lookup_unavailable),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        PpButton(
                            text = stringResource(R.string.retry),
                            onClick = {
                                val draft = currentAddressDraft()
                                if (draft.latitude != null && draft.longitude != null) {
                                    onRetryAddressLookup(
                                        LocationSnapshot(
                                            latitude = draft.latitude,
                                            longitude = draft.longitude,
                                            accuracyMeters = draft.accuracyMeters,
                                            capturedAtEpochMillis = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            },
                            enabled = !locationLoading,
                            style = PpButtonStyle.Secondary,
                            modifier = Modifier.testTag("location_lookup_retry"),
                        )
                    }
                }
                PpButton(
                    text = stringResource(R.string.refine_on_map),
                    onClick = { showAddressMap = true },
                    style = PpButtonStyle.Secondary,
                    icon = PpIcons.Location,
                    modifier = Modifier.fillMaxWidth().testTag("location_refine_map"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (photoLocation != null) {
                        PpButton(
                            text = stringResource(
                                if (locationLoading) R.string.locating else R.string.from_photo,
                            ),
                            onClick = onUsePhotoLocation,
                            enabled = !locationLoading,
                            style = PpButtonStyle.Secondary,
                            icon = PpIcons.Gallery,
                            modifier = Modifier.weight(1f).testTag("location_from_photo"),
                        )
                    }
                    PpButton(
                        text = stringResource(
                            if (locationLoading) R.string.locating else R.string.current_location,
                        ),
                        onClick = onUseCurrentLocation,
                        enabled = !locationLoading,
                        style = PpButtonStyle.Secondary,
                        icon = PpIcons.Location,
                        modifier = Modifier.weight(1f).testTag("location_use_current"),
                    )
                }
                PpDisclosureRow(
                    title = stringResource(R.string.coordinates),
                    expanded = advanced,
                    stateDescription = stringResource(
                        if (advanced) R.string.expanded else R.string.collapsed,
                    ),
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
                            editRevision++
                            accuracyMeters = null
                            candidates = emptyList()
                            addressLookupFailed = false
                            validationError = null
                        },
                        label = stringResource(R.string.latitude),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = validationError == ReportLocationInputError.COORDINATES,
                        modifier = Modifier
                            .focusRequester(coordinatesFocusRequester)
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
                            editRevision++
                            accuracyMeters = null
                            candidates = emptyList()
                            addressLookupFailed = false
                            validationError = null
                        },
                        label = stringResource(R.string.longitude),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = validationError == ReportLocationInputError.COORDINATES,
                        modifier = Modifier.testTag("location_longitude"),
                    )
                }
                if (validationError == ReportLocationInputError.COORDINATES) {
                    Text(
                        text = invalidCoordinatesMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag("location_coordinates_error"),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().testTag("time_section"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("time_section_title"),
                ) {
                    Icon(
                        PpIcons.Time,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(stringResource(R.string.time_section), style = MaterialTheme.typography.titleMedium)
                }
                PpField(
                    value = occurredAt,
                    onValueChange = { occurredAt = it; validationError = null },
                    label = stringResource(R.string.occurred_at),
                    isError = validationError == ReportLocationInputError.TIME,
                    modifier = Modifier
                        .focusRequester(timeFocusRequester)
                        .semantics {
                            if (validationError == ReportLocationInputError.TIME) {
                                error(invalidTimeMessage)
                            }
                        }
                        .testTag("location_time"),
                )
                if (validationError == ReportLocationInputError.TIME) {
                    Text(
                        text = invalidTimeMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag("location_time_error"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (photoTime != null) {
                        PpButton(
                            text = stringResource(R.string.from_photo),
                            onClick = {
                                occurredAt = photoTime
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
                            occurredAt = onUseCurrentTime()
                            validationError = null
                        },
                        style = PpButtonStyle.Secondary,
                        icon = PpIcons.Time,
                        modifier = Modifier.weight(1f).testTag("location_time_now"),
                    )
                }
            }
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = {
                    validationError = onSave(address, latitude, longitude, occurredAt, accuracyMeters)
                    if (validationError == null) onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 20.dp)
                    .testTag("location_save"),
            )
        }
    }
    if (showAddressMap) {
        AddressMapPicker(
            initialAddress = address,
            initialLatitude = ReportLocationInput.parseLatitude(latitude),
            initialLongitude = ReportLocationInput.parseLongitude(longitude),
            resolveAddress = resolveAddress,
            onDismiss = { showAddressMap = false },
            onUseSelection = { selection ->
                val selected = AddressSelectionPolicy.useMapSelection(
                    currentAddressDraft(),
                    selection,
                )
                address = selected.address
                if (
                    selection.movedPoint ||
                    selected.latitude != ReportLocationInput.parseLatitude(latitude) ||
                    selected.longitude != ReportLocationInput.parseLongitude(longitude)
                ) {
                    latitude = selected.latitude?.toString().orEmpty()
                    longitude = selected.longitude?.toString().orEmpty()
                }
                candidates = selection.candidates
                accuracyMeters = selected.accuracyMeters
                editRevision++
                addressLookupFailed = selection.addressLookupFailed
                validationError = null
                showAddressMap = false
            },
        )
    }
}

@Composable
internal fun DeliveryEditorSheet(
    report: Report,
    onClose: () -> Unit,
    onSaveRecipient: (String) -> Unit,
    onSaveLetter: (String, String) -> Unit,
    onSaveRegeneratedLetter: () -> Unit,
    onRegenerate: () -> LetterDraft?,
) {
    var recipient by remember(report.id) { mutableStateOf(report.recipient) }
    var subject by remember(report.id) { mutableStateOf(report.subject) }
    var body by remember(report.id) { mutableStateOf(report.body) }
    var letterDirty by remember(report.id) { mutableStateOf(false) }
    var regeneratedLetterPending by remember(report.id) { mutableStateOf(false) }
    LaunchedEffect(report.subject, report.body) {
        if (!letterDirty && !regeneratedLetterPending) {
            subject = report.subject
            body = report.body
        }
    }
    PpSheet(onDismissRequest = onClose) { dismiss ->
        PpSheetHeader(stringResource(R.string.letter_and_recipient), dismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PpField(
                value = recipient,
                onValueChange = { recipient = it },
                label = stringResource(R.string.recipient),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = recipient.isBlank(),
                modifier = Modifier.testTag("delivery_recipient"),
            )
            PpField(
                value = subject,
                onValueChange = {
                    letterDirty = true
                    regeneratedLetterPending = false
                    subject = it
                },
                label = stringResource(R.string.subject),
                modifier = Modifier.testTag("delivery_subject"),
            )
            PpField(
                value = body,
                onValueChange = {
                    letterDirty = true
                    regeneratedLetterPending = false
                    body = it
                },
                label = stringResource(R.string.body),
                singleLine = false,
                minLines = 8,
                modifier = Modifier.testTag("delivery_body"),
            )
            PpButton(
                text = stringResource(R.string.regenerate),
                onClick = {
                    onRegenerate()?.let { regenerated ->
                        subject = regenerated.subject
                        body = regenerated.body
                        letterDirty = false
                        regeneratedLetterPending = true
                    }
                },
                icon = PpIcons.Refresh,
                style = PpButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("delivery_regenerate"),
            )
            PpButton(
                text = stringResource(R.string.save_changes),
                onClick = {
                    onSaveRecipient(recipient)
                    when {
                        letterDirty -> onSaveLetter(subject, body)
                        regeneratedLetterPending -> onSaveRegeneratedLetter()
                    }
                    onClose()
                },
                enabled = recipient.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 20.dp)
                    .testTag("delivery_save"),
            )
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
private fun sourceLabel(source: RecognitionSource): String = when (source) {
    RecognitionSource.ML_KIT_OCR -> "OCR"
    RecognitionSource.LOCAL_PLATE_MODEL -> stringResource(R.string.recognition_source_on_device)
    RecognitionSource.WORKERS_AI -> "Workers AI"
    RecognitionSource.OPENAI -> "OpenAI"
}

@Composable
private fun providerLabel(provider: CloudProvider): String = stringResource(
    if (provider == CloudProvider.WORKERS_AI) R.string.workers_ai else R.string.openai,
)
