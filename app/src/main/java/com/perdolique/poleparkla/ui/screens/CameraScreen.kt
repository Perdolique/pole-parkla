package com.perdolique.poleparkla.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.domain.LocationFreshness
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.PoleParklaColors
import com.perdolique.poleparkla.ui.PoleParklaViewModel
import com.perdolique.poleparkla.ui.findActivity
import com.perdolique.poleparkla.ui.openAppSettings
import com.perdolique.poleparkla.ui.components.PhotoThumbnail
import com.perdolique.poleparkla.ui.components.PoleParklaSystemBars
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpDialog
import com.perdolique.poleparkla.ui.components.PpIconButton
import com.perdolique.poleparkla.ui.components.PpIcons
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.delay

private enum class PermissionRequest {
    CAMERA,
    LOCATION,
}

@Composable
fun CameraScreen(
    viewModel: PoleParklaViewModel,
    photoStore: PhotoStore,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onReview: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reportId by viewModel.cameraReportId.collectAsStateWithLifecycle()
    val reportFlow = remember(reportId) { viewModel.report(reportId) }
    val report by reportFlow.collectAsStateWithLifecycle(initialValue = null)
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var showLocationInfo by remember { mutableStateOf(false) }
    var requestLocationAfterInfo by remember { mutableStateOf(false) }
    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var permanentlyDeniedPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val cameraPermissionGranted = remember(permissionRefresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val cameraHardwareAvailable = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var cameraUsable by remember(permissionRefresh, cameraHardwareAvailable) {
        mutableStateOf(cameraHardwareAvailable)
    }
    val cameraPresent = cameraHardwareAvailable && cameraUsable
    val cameraAvailable = cameraPermissionGranted && cameraPresent
    val locationGranted = remember(permissionRefresh) {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val request = pendingPermissionRequest
        pendingPermissionRequest = null
        permissionRefresh++
        val granted = request
            ?.permissions()
            ?.any { result[it] == true }
            ?: false
        if (request != null && result.isNotEmpty() && !granted && !context.shouldShowRationale(request)) {
            permanentlyDeniedPermission = request
        }
    }
    val launchPermissionRequest: (PermissionRequest) -> Unit = { request ->
        pendingPermissionRequest = request
        when (request) {
            PermissionRequest.CAMERA -> cameraPermissionRequested = true
            PermissionRequest.LOCATION -> locationPermissionRequested = true
        }
        permissionLauncher.launch(request.permissions())
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        viewModel.addGalleryPhotos(uris)
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val freshLocation = latestLocation?.takeIf { LocationFreshness.isFresh(it) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionRefresh++
    }

    DisposableEffect(lifecycleOwner, viewModel, permissionRefresh) {
        var locationStarted = false
        val startLocation = {
            if (!locationStarted) {
                locationStarted = true
                viewModel.startLocationUpdates()
            }
        }
        val stopLocation = {
            if (locationStarted) {
                locationStarted = false
                viewModel.stopLocationUpdates()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startLocation()
                Lifecycle.Event.ON_STOP -> stopLocation()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startLocation()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopLocation()
        }
    }

    PoleParklaSystemBars(lightIcons = true)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (cameraAvailable) {
            CameraPreview(
                imageCapture = imageCapture,
                onAvailabilityChanged = { cameraUsable = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CameraUnavailable(
                cameraPresent = cameraPresent,
                onGrantCamera = {
                    if (cameraPermissionRequested && !context.shouldShowRationale(PermissionRequest.CAMERA)) {
                        permanentlyDeniedPermission = PermissionRequest.CAMERA
                    } else {
                        launchPermissionRequest(PermissionRequest.CAMERA)
                    }
                },
                onGallery = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }

        CameraTopControls(
            locationGranted = locationGranted,
            freshAccuracy = freshLocation?.accuracyMeters?.toInt(),
            onHistory = onHistory,
            onSettings = onSettings,
            onLocation = {
                if (locationGranted) {
                    requestLocationAfterInfo = false
                    showLocationInfo = true
                } else {
                    requestLocationAfterInfo = true
                    showLocationInfo = true
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CameraTray(
            photos = report?.photos.orEmpty(),
            photoStore = photoStore,
            cameraAvailable = cameraAvailable,
            busy = busy,
            onGallery = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCapture = {
                val target = viewModel.createCameraTarget()
                capturePhoto(
                    imageCapture = imageCapture,
                    target = target.file,
                    executor = ContextCompat.getMainExecutor(context),
                    onSaved = { viewModel.onCameraPhotoCaptured(target) },
                    onError = { viewModel.onCameraCaptureFailed(target) },
                )
            },
            onReview = { onReview(reportId) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showLocationInfo) {
        PpDialog(
            title = stringResource(
                if (requestLocationAfterInfo) R.string.location_permission_title else R.string.location_and_time,
            ),
            onDismissRequest = {
                showLocationInfo = false
                requestLocationAfterInfo = false
            },
            confirmText = stringResource(
                if (requestLocationAfterInfo) R.string.grant_location else R.string.confirm,
            ),
            onConfirm = {
                showLocationInfo = false
                if (requestLocationAfterInfo) {
                    requestLocationAfterInfo = false
                    if (locationPermissionRequested && !context.shouldShowRationale(PermissionRequest.LOCATION)) {
                        permanentlyDeniedPermission = PermissionRequest.LOCATION
                    } else {
                        launchPermissionRequest(PermissionRequest.LOCATION)
                    }
                }
            },
            dismissText = if (requestLocationAfterInfo) stringResource(R.string.cancel) else null,
            content = {
                Text(
                    stringResource(R.string.location_permission_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    permanentlyDeniedPermission?.let { permission ->
        PpDialog(
            title = stringResource(
                if (permission == PermissionRequest.CAMERA) {
                    R.string.camera_permission_title
                } else {
                    R.string.location_permission_title
                },
            ),
            onDismissRequest = { permanentlyDeniedPermission = null },
            confirmText = stringResource(R.string.open_settings),
            onConfirm = {
                permanentlyDeniedPermission = null
                context.openAppSettings()
            },
            dismissText = stringResource(R.string.cancel),
            content = {
                Text(
                    stringResource(
                        if (permission == PermissionRequest.CAMERA) {
                            R.string.camera_permission_settings_message
                        } else {
                            R.string.location_permission_settings_message
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun CameraTopControls(
    locationGranted: Boolean,
    freshAccuracy: Int?,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PpIconButton(
            icon = PpIcons.History,
            contentDescription = stringResource(R.string.history),
            onClick = onHistory,
            containerColor = PoleParklaColors.CameraScrim,
            contentColor = Color.White,
        )
        Surface(
            onClick = onLocation,
            shape = RoundedCornerShape(18.dp),
            color = PoleParklaColors.CameraScrim,
            contentColor = Color.White,
            modifier = Modifier.weight(1f).testTag("camera_location"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(
                            if (freshAccuracy != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.error,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        freshAccuracy != null -> stringResource(R.string.camera_location_available, freshAccuracy)
                        locationGranted -> stringResource(R.string.camera_location_unavailable)
                        else -> stringResource(R.string.grant_location)
                    },
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        PpIconButton(
            icon = PpIcons.Settings,
            contentDescription = stringResource(R.string.settings),
            onClick = onSettings,
            containerColor = PoleParklaColors.CameraScrim,
            contentColor = Color.White,
            modifier = Modifier.testTag("camera_settings"),
        )
    }
}

@Composable
internal fun CameraUnavailable(
    cameraPresent: Boolean,
    onGrantCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PpIcons.Camera, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Text(
            stringResource(
                if (cameraPresent) R.string.camera_permission_title else R.string.camera_unavailable_title,
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            stringResource(
                if (cameraPresent) R.string.camera_permission_text else R.string.camera_unavailable_text,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
        if (cameraPresent) {
            PpButton(
                text = stringResource(R.string.camera_permission_action),
                onClick = onGrantCamera,
                icon = PpIcons.Camera,
                modifier = Modifier.fillMaxWidth().testTag("camera_permission"),
            )
            Spacer(Modifier.height(10.dp))
        }
        PpButton(
            text = stringResource(R.string.add_from_gallery),
            onClick = onGallery,
            icon = PpIcons.Gallery,
            style = PpButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().testTag("camera_gallery_empty"),
        )
    }
}

@Composable
internal fun CameraTray(
    photos: List<ReportPhoto>,
    photoStore: PhotoStore,
    cameraAvailable: Boolean,
    busy: Boolean,
    onGallery: () -> Unit,
    onCapture: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoCount = photos.size
    val captureDescription = stringResource(R.string.take_photo)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PoleParklaColors.CameraScrim, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(R.string.camera_title),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) { index ->
                PhotoSlot(
                    photo = photos.getOrNull(index),
                    photoStore = photoStore,
                    index = index,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            stringResource(R.string.photo_counter, photoCount),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(76.dp)
                .testTag("camera_controls"),
        ) {
            PpIconButton(
                icon = PpIcons.Gallery,
                contentDescription = stringResource(R.string.add_from_gallery),
                onClick = onGallery,
                enabled = photoCount < 3 && !busy,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.CenterStart).testTag("camera_gallery"),
            )
            if (cameraAvailable) {
                Surface(
                    onClick = onCapture,
                    enabled = photoCount < 3 && !busy,
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(4.dp, Color.White.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(76.dp)
                        .semantics { contentDescription = captureDescription }
                        .testTag("camera_capture"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.align(Alignment.Center).size(76.dp))
            }
            if (photoCount > 0) {
                PpButton(
                    text = stringResource(R.string.review_short),
                    onClick = onReview,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterEnd).width(132.dp).testTag("camera_review"),
                )
            }
        }
    }
}

@Composable
private fun PhotoSlot(
    photo: ReportPhoto?,
    photoStore: PhotoStore,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    if (photo != null) {
        PhotoThumbnail(
            photo = photo,
            photoStore = photoStore,
            contentDescription = stringResource(R.string.photo_description, index + 1),
            modifier = modifier.height(72.dp).border(2.dp, MaterialTheme.colorScheme.primaryContainer, shape),
        )
    } else {
        Box(
            modifier = modifier
                .height(72.dp)
                .background(Color.Black.copy(alpha = 0.3f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.35f), shape),
        )
    }
}

@Composable
internal fun CameraPreview(
    imageCapture: ImageCapture,
    onAvailabilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onZoomStateChanged: (ZoomState) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val availabilityCallback by rememberUpdatedState(onAvailabilityChanged)
    val zoomStateCallback by rememberUpdatedState(onZoomStateChanged)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomState by remember { mutableStateOf<ZoomState?>(null) }

    CameraZoomOverlay(
        currentZoomRatio = zoomState?.zoomRatio ?: DEFAULT_CAMERA_ZOOM_RATIO,
        minZoomRatio = zoomState?.minZoomRatio ?: DEFAULT_CAMERA_ZOOM_RATIO,
        maxZoomRatio = zoomState?.maxZoomRatio ?: DEFAULT_CAMERA_ZOOM_RATIO,
        onZoomRatioChange = { ratio -> camera?.cameraControl?.setZoomRatio(ratio) },
        modifier = modifier,
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
    DisposableEffect(lifecycleOwner, imageCapture) {
        val executor = ContextCompat.getMainExecutor(context)
        var observedCamera: Camera? = null
        var zoomObserver: Observer<ZoomState>? = null
        var disposed = false
        providerFuture.addListener(
            {
                if (disposed) return@addListener
                val provider = runCatching { providerFuture.get() }.getOrElse {
                    availabilityCallback(false)
                    return@addListener
                }
                val selector = when {
                    runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> {
                        availabilityCallback(false)
                        return@addListener
                    }
                }
                val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }.onSuccess { boundCamera ->
                    camera = boundCamera
                    observedCamera = boundCamera
                    zoomState = boundCamera.cameraInfo.zoomState.value
                    Observer<ZoomState> { state ->
                        zoomState = state
                        zoomStateCallback(state)
                    }.also { observer ->
                        zoomObserver = observer
                        boundCamera.cameraInfo.zoomState.observe(lifecycleOwner, observer)
                    }
                    availabilityCallback(true)
                }.onFailure {
                    camera = null
                    zoomState = null
                    availabilityCallback(false)
                }
            },
            executor,
        )
        onDispose {
            disposed = true
            zoomObserver?.let { observer -> observedCamera?.cameraInfo?.zoomState?.removeObserver(observer) }
            camera = null
            zoomState = null
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }
}

@Composable
internal fun CameraZoomOverlay(
    currentZoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val zoomSupported = maxZoomRatio > minZoomRatio
    val zoomCallback by rememberUpdatedState(onZoomRatioChange)
    val latestCurrentZoomRatio by rememberUpdatedState(currentZoomRatio)
    var requestedZoomRatio by remember { mutableFloatStateOf(currentZoomRatio) }
    var zoomChange by remember { mutableIntStateOf(0) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    val zoomControlDescription = stringResource(R.string.camera_zoom_control)
    val zoomInLabel = stringResource(R.string.camera_zoom_in)
    val zoomOutLabel = stringResource(R.string.camera_zoom_out)
    val formattedZoomRatio = String.format(
        LocalLocale.current.platformLocale,
        "%.1f×",
        requestedZoomRatio,
    )
    val zoomStateDescription = stringResource(R.string.camera_zoom_state, formattedZoomRatio)

    fun requestZoomRatio(targetRatio: Float): Boolean {
        if (targetRatio == requestedZoomRatio) return false
        requestedZoomRatio = targetRatio
        showZoomIndicator = true
        zoomCallback(targetRatio)
        zoomChange++
        return true
    }

    LaunchedEffect(currentZoomRatio, minZoomRatio, maxZoomRatio) {
        if (!showZoomIndicator) {
            requestedZoomRatio = currentZoomRatio.coerceIn(minZoomRatio, maxZoomRatio)
        }
    }
    LaunchedEffect(zoomSupported) {
        if (!zoomSupported) showZoomIndicator = false
    }
    LaunchedEffect(zoomChange) {
        if (zoomChange == 0) return@LaunchedEffect
        delay(ZOOM_INDICATOR_DURATION_MILLIS)
        showZoomIndicator = false
        requestedZoomRatio = latestCurrentZoomRatio.coerceIn(minZoomRatio, maxZoomRatio)
    }

    Box(
        modifier = modifier
            .pointerInput(zoomSupported, minZoomRatio, maxZoomRatio) {
                if (!zoomSupported) return@pointerInput
                detectTransformGestures { _, _, zoomFactor, _ ->
                    requestZoomRatio(
                        calculateCameraZoomRatio(
                            currentZoomRatio = requestedZoomRatio,
                            zoomFactor = zoomFactor,
                            minZoomRatio = minZoomRatio,
                            maxZoomRatio = maxZoomRatio,
                        ),
                    )
                }
            }
            .semantics {
                contentDescription = zoomControlDescription
                stateDescription = zoomStateDescription
                if (zoomSupported) {
                    customActions = buildList {
                        if (requestedZoomRatio < maxZoomRatio) {
                            add(
                                CustomAccessibilityAction(zoomInLabel) {
                                    requestZoomRatio(
                                        calculateCameraZoomRatio(
                                            currentZoomRatio = requestedZoomRatio,
                                            zoomFactor = CAMERA_ZOOM_ACCESSIBILITY_FACTOR,
                                            minZoomRatio = minZoomRatio,
                                            maxZoomRatio = maxZoomRatio,
                                        ),
                                    )
                                },
                            )
                        }
                        if (requestedZoomRatio > minZoomRatio) {
                            add(
                                CustomAccessibilityAction(zoomOutLabel) {
                                    requestZoomRatio(
                                        calculateCameraZoomRatio(
                                            currentZoomRatio = requestedZoomRatio,
                                            zoomFactor = 1f / CAMERA_ZOOM_ACCESSIBILITY_FACTOR,
                                            minZoomRatio = minZoomRatio,
                                            maxZoomRatio = maxZoomRatio,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            },
    ) {
        content()
        if (showZoomIndicator) {
            CameraZoomIndicator(
                zoomRatio = requestedZoomRatio,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun CameraZoomIndicator(
    zoomRatio: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(PoleParklaColors.CameraScrim, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("camera_zoom_indicator"),
    ) {
        Text(
            text = String.format(LocalLocale.current.platformLocale, "%.1f×", zoomRatio),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal fun calculateCameraZoomRatio(
    currentZoomRatio: Float,
    zoomFactor: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
): Float {
    if (minZoomRatio >= maxZoomRatio) return minZoomRatio
    return (currentZoomRatio * zoomFactor).coerceIn(minZoomRatio, maxZoomRatio)
}

private const val DEFAULT_CAMERA_ZOOM_RATIO = 1f
private const val CAMERA_ZOOM_ACCESSIBILITY_FACTOR = 1.25f
private const val ZOOM_INDICATOR_DURATION_MILLIS = 800L

private fun capturePhoto(
    imageCapture: ImageCapture,
    target: File,
    executor: Executor,
    onSaved: () -> Unit,
    onError: () -> Unit,
) {
    val options = ImageCapture.OutputFileOptions.Builder(target).build()
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSaved()
            override fun onError(exception: ImageCaptureException) = onError()
        },
    )
}

private fun PermissionRequest.permissions(): Array<String> = when (this) {
    PermissionRequest.CAMERA -> arrayOf(Manifest.permission.CAMERA)
    PermissionRequest.LOCATION -> arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
}

private fun Context.shouldShowRationale(request: PermissionRequest): Boolean {
    val activity = findActivity() ?: return false
    return request.permissions().any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}
