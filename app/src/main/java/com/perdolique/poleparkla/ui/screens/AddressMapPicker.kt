package com.perdolique.poleparkla.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.AddressMapSelection
import com.perdolique.poleparkla.model.AddressResolution
import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpTopBar
import java.util.Locale
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position

internal data class AddressMapPoint(
    val latitude: Double,
    val longitude: Double,
) {
    fun toLocationSnapshot(capturedAtEpochMillis: Long): LocationSnapshot = LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = null,
        capturedAtEpochMillis = capturedAtEpochMillis,
    )
}

internal fun Position.toAddressMapPoint(): AddressMapPoint = AddressMapPoint(
    latitude = latitude,
    longitude = longitude,
)

internal fun addressMapSelection(
    point: AddressMapPoint,
    initialAddress: String,
    resolution: AddressResolution?,
): AddressMapSelection? {
    if (
        resolution != null &&
        (resolution.location.latitude != point.latitude ||
            resolution.location.longitude != point.longitude)
    ) {
        return null
    }
    val suggestedAddress = resolution?.suggested?.address
    return AddressMapSelection(
        address = suggestedAddress ?: initialAddress,
        latitude = point.latitude,
        longitude = point.longitude,
        movedPoint = true,
        candidates = resolution?.candidates.orEmpty(),
        addressLookupFailed = suggestedAddress == null,
    )
}

internal sealed interface AddressMapLookupState {
    data object Idle : AddressMapLookupState
    data object Loading : AddressMapLookupState
    data class Ready(val selection: AddressMapSelection) : AddressMapLookupState
    data class Unavailable(val selection: AddressMapSelection) : AddressMapLookupState
}

private enum class AddressMapLoadState {
    LOADING,
    READY,
    FAILED,
}

@Composable
internal fun AddressMapPicker(
    initialAddress: String,
    initialLatitude: Double?,
    initialLongitude: Double?,
    resolveAddress: suspend (LocationSnapshot, Boolean) -> AddressResolution?,
    onDismiss: () -> Unit,
    onUseSelection: (AddressMapSelection) -> Unit,
) {
    val initialPoint = remember(initialLatitude, initialLongitude) {
        if (
            initialLatitude != null &&
            initialLongitude != null &&
            initialLatitude in -90.0..90.0 &&
            initialLongitude in -180.0..180.0
        ) {
            AddressMapPoint(initialLatitude, initialLongitude)
        } else {
            null
        }
    }
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                initialPoint?.longitude ?: ESTONIA_CENTER_LONGITUDE,
                initialPoint?.latitude ?: ESTONIA_CENTER_LATITUDE,
            ),
            zoom = if (initialPoint == null) ESTONIA_ZOOM else ADDRESS_ZOOM,
        ),
    )
    var selectedPoint by remember { mutableStateOf<AddressMapPoint?>(null) }
    var lookupState by remember { mutableStateOf<AddressMapLookupState>(AddressMapLookupState.Idle) }
    var lookupAttempt by remember { mutableIntStateOf(0) }
    var mapAttempt by remember { mutableIntStateOf(0) }
    var mapLoadState by remember { mutableStateOf(AddressMapLoadState.LOADING) }

    LaunchedEffect(selectedPoint, lookupAttempt) {
        val point = selectedPoint ?: run {
            lookupState = AddressMapLookupState.Idle
            return@LaunchedEffect
        }
        lookupState = AddressMapLookupState.Loading
        val resolution = resolveAddress(
            point.toLocationSnapshot(System.currentTimeMillis()),
            lookupAttempt > 0,
        )
        val selection = addressMapSelection(point, initialAddress, resolution)
            ?: requireNotNull(addressMapSelection(point, initialAddress, resolution = null))
        lookupState = if (selection.addressLookupFailed) {
            AddressMapLookupState.Unavailable(selection)
        } else {
            AddressMapLookupState.Ready(selection)
        }
    }
    LaunchedEffect(mapAttempt) {
        mapLoadState = AddressMapLoadState.LOADING
        delay(MAP_LOAD_TIMEOUT_MILLIS)
        if (mapLoadState == AddressMapLoadState.LOADING) {
            mapLoadState = AddressMapLoadState.FAILED
        }
    }

    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            PpTopBar(
                title = stringResource(R.string.refine_on_map),
                onBack = onDismiss,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                key(mapAttempt) {
                    AddressMapCanvas(
                        cameraState = cameraState,
                        initialPoint = initialPoint,
                        selectedPoint = selectedPoint,
                        onPointSelected = { point ->
                            selectedPoint = point
                            lookupAttempt = 0
                        },
                        onLoaded = { mapLoadState = AddressMapLoadState.READY },
                        onFailed = { mapLoadState = AddressMapLoadState.FAILED },
                    )
                }
                when (mapLoadState) {
                    AddressMapLoadState.LOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).testTag("address_map_loading"),
                        )
                    }
                    AddressMapLoadState.FAILED -> {
                        AddressMapError(
                            onRetry = { mapAttempt++ },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    AddressMapLoadState.READY -> Unit
                }
            }
            AddressMapPickerActions(
                lookupState = lookupState,
                onRetry = { lookupAttempt++ },
                onUseSelection = onUseSelection,
            )
        }
    }
}

@Composable
private fun AddressMapCanvas(
    cameraState: CameraState,
    initialPoint: AddressMapPoint?,
    selectedPoint: AddressMapPoint?,
    onPointSelected: (AddressMapPoint) -> Unit,
    onLoaded: () -> Unit,
    onFailed: () -> Unit,
) {
    val originalColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.primaryContainer
    val selectedStrokeColor = MaterialTheme.colorScheme.onPrimaryContainer
    val loadingColor = MaterialTheme.colorScheme.surface
    val mapDescription = stringResource(R.string.map_canvas_description)
    val mapOptions = remember(loadingColor) {
        MapOptions(
            renderOptions = RenderOptions(
                renderMode = RenderOptions.RenderMode.TextureView,
                foregroundLoadColor = loadingColor,
                maximumFps = 60,
            ),
            gestureOptions = GestureOptions.RotationLocked,
            ornamentOptions = OrnamentOptions(
                padding = PaddingValues(8.dp),
                isScaleBarEnabled = false,
            ),
        )
    }

    MaplibreMap(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = mapDescription }
            .testTag("address_map_canvas"),
        baseStyle = BaseStyle.Uri(OPEN_FREE_MAP_POSITRON_STYLE),
        cameraState = cameraState,
        pitchRange = 0f..0f,
        options = mapOptions,
        onMapClick = { position, _ ->
            onPointSelected(position.toAddressMapPoint())
            ClickResult.Consume
        },
        onMapLoadFinished = onLoaded,
        onMapLoadFailed = { onFailed() },
    ) {
        AddressMapMarkers(
            initialPoint = initialPoint,
            selectedPoint = selectedPoint,
            originalColor = originalColor,
            selectedColor = selectedColor,
            selectedStrokeColor = selectedStrokeColor,
        )
    }
}

@Composable
@MaplibreComposable
private fun AddressMapMarkers(
    initialPoint: AddressMapPoint?,
    selectedPoint: AddressMapPoint?,
    originalColor: Color,
    selectedColor: Color,
    selectedStrokeColor: Color,
) {
    if (initialPoint != null) {
        val originalSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(initialPoint.toGeoJson()),
            options = GeoJsonOptions(synchronousUpdate = true),
        )
        CircleLayer(
            id = "original-location",
            source = originalSource,
            color = const(Color.Transparent),
            radius = const(8.dp),
            strokeColor = const(originalColor),
            strokeWidth = const(3.dp),
        )
    }
    if (selectedPoint != null) {
        val selectedSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(selectedPoint.toGeoJson()),
            options = GeoJsonOptions(synchronousUpdate = true),
        )
        CircleLayer(
            id = "selected-location",
            source = selectedSource,
            color = const(selectedColor),
            radius = const(9.dp),
            strokeColor = const(selectedStrokeColor),
            strokeWidth = const(2.dp),
        )
    }
}

@Composable
internal fun AddressMapPickerActions(
    lookupState: AddressMapLookupState,
    onRetry: () -> Unit,
    onUseSelection: (AddressMapSelection) -> Unit,
) {
    val selection = when (lookupState) {
        AddressMapLookupState.Idle,
        AddressMapLookupState.Loading -> null
        is AddressMapLookupState.Ready -> lookupState.selection
        is AddressMapLookupState.Unavailable -> lookupState.selection
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (lookupState) {
            AddressMapLookupState.Idle -> Text(
                text = stringResource(R.string.map_select_address_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("address_map_selection"),
            )
            AddressMapLookupState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("address_map_lookup_loading"),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.map_address_lookup_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is AddressMapLookupState.Ready -> Text(
                text = lookupState.selection.address,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("address_map_selection"),
            )
            is AddressMapLookupState.Unavailable -> {
                Text(
                    text = stringResource(R.string.map_address_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("address_map_selection"),
                )
                PpButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    style = PpButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth().testTag("address_map_lookup_retry"),
                )
            }
        }
        PpButton(
            text = stringResource(
                if (lookupState is AddressMapLookupState.Unavailable) {
                    R.string.use_this_point
                } else {
                    R.string.use_this_address
                },
            ),
            onClick = { selection?.let(onUseSelection) },
            enabled = selection != null,
            modifier = Modifier.fillMaxWidth().testTag("address_map_use"),
        )
    }
}

@Composable
private fun AddressMapError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
            .testTag("address_map_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.map_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PpButton(
            text = stringResource(R.string.retry),
            onClick = onRetry,
            style = PpButtonStyle.Secondary,
            modifier = Modifier.padding(top = 16.dp).testTag("address_map_retry"),
        )
    }
}

private fun AddressMapPoint.toGeoJson(): String = String.format(
    Locale.US,
    """{"type":"Point","coordinates":[%.8f,%.8f]}""",
    longitude,
    latitude,
)

private const val OPEN_FREE_MAP_POSITRON_STYLE =
    "https://tiles.openfreemap.org/styles/positron"
private const val ESTONIA_CENTER_LATITUDE = 58.6
private const val ESTONIA_CENTER_LONGITUDE = 25.0
private const val ESTONIA_ZOOM = 7.0
private const val ADDRESS_ZOOM = 17.0
private const val MAP_LOAD_TIMEOUT_MILLIS = 15_000L
