package com.perdolique.poleparkla.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.ui.findActivity
import kotlinx.coroutines.launch

object PpIcons {
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Close = Icons.Outlined.Close
    val History = Icons.Outlined.History
    val Settings = Icons.Outlined.Settings
    val Gallery = Icons.Outlined.PhotoLibrary
    val Camera = Icons.Outlined.CameraAlt
    val CheckCircle = Icons.Outlined.CheckCircle
    val ChevronRight = Icons.Outlined.ChevronRight
    val Edit = Icons.Outlined.Edit
    val Warning = Icons.Outlined.WarningAmber
    val Car = Icons.Outlined.DirectionsCar
    val Violation = Icons.Outlined.ReportProblem
    val CyclePath = Icons.AutoMirrored.Outlined.DirectionsBike
    val PedestrianPath = Icons.AutoMirrored.Outlined.DirectionsWalk
    val Location = Icons.Outlined.LocationOn
    val Email = Icons.Outlined.Email
    val More = Icons.Outlined.MoreVert
    val Add = Icons.Outlined.Add
    val Delete = Icons.Outlined.DeleteOutline
    val Person = Icons.Outlined.Person
    val Language = Icons.Outlined.Language
    val Recognition = Icons.Outlined.AutoAwesome
    val Flag = Icons.Outlined.Flag
    val Privacy = Icons.Outlined.PrivacyTip
    val Expand = Icons.Outlined.ExpandMore
    val Refresh = Icons.Outlined.Refresh
    val Lock = Icons.Outlined.Lock
    val Send = Icons.AutoMirrored.Outlined.Send
    val Calendar = Icons.Outlined.CalendarMonth
    val Time = Icons.Outlined.Schedule
}

enum class PpButtonStyle {
    Primary,
    Secondary,
    Ghost,
    Danger,
}

@Composable
fun PpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PpButtonStyle = PpButtonStyle.Primary,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (style) {
        PpButtonStyle.Primary -> scheme.primaryContainer
        PpButtonStyle.Secondary -> scheme.surface
        PpButtonStyle.Ghost -> Color.Transparent
        PpButtonStyle.Danger -> scheme.errorContainer
    }
    val content = when (style) {
        PpButtonStyle.Primary -> scheme.onPrimaryContainer
        PpButtonStyle.Secondary -> scheme.primary
        PpButtonStyle.Ghost -> scheme.primary
        PpButtonStyle.Danger -> scheme.onErrorContainer
    }
    val border = when (style) {
        PpButtonStyle.Secondary -> BorderStroke(1.dp, scheme.outline)
        else -> null
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = scheme.surfaceVariant,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PpTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PpIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun PpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    role: Role = Role.Button,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val isSelected = selected == true
    val border = BorderStroke(
        if (isSelected) 2.dp else 1.dp,
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    )
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = 0.dp,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = 48.dp)
                .semantics {
                    this.role = role
                    selected?.let { this.selected = it }
                },
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

@Composable
fun PpField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { value -> { Text(value) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PpTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            PpIconButton(
                icon = PpIcons.Back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                containerColor = Color.Transparent,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun PpBadge(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    isComplete: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        isError -> scheme.errorContainer
        isComplete -> scheme.primaryContainer
        else -> scheme.surfaceVariant
    }
    val content = when {
        isError -> scheme.onErrorContainer
        isComplete -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isComplete) Icon(PpIcons.CheckCircle, null, Modifier.size(16.dp))
        if (isError) Icon(PpIcons.Warning, null, Modifier.size(16.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PpChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
) {
    PpCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        selected = selected,
        role = Role.RadioButton,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supportingText != null) {
                    Text(
                        supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(
                if (selected) PpIcons.CheckCircle else PpIcons.ChevronRight,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PpDisclosureRow(
    title: String,
    expanded: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
) {
    PpCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.stateDescription = stateDescription },
        onClick = onClick,
        role = Role.Button,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supportingText != null) {
                    Text(
                        supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(
                PpIcons.Expand,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PpSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    var dismissalCompleted by remember { mutableStateOf(false) }
    val completeDismissal = {
        if (!dismissalCompleted) {
            dismissalCompleted = true
            onDismissRequest()
        }
    }
    val dismiss = {
        if (!dismissing && !dismissalCompleted) {
            dismissing = true
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (sheetState.isVisible) dismissing = false
                else completeDismissal()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = completeDismissal,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 48.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding(),
        ) {
            content(dismiss)
        }
    }
}

@Composable
fun PpDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = content,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = if (confirmTestTag != null) Modifier.testTag(confirmTestTag) else Modifier,
            ) {
                Text(
                    confirmText,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = dismissText?.let { label ->
            { TextButton(onClick = onDismissRequest) { Text(label) } }
        },
    )
}

@Composable
fun PpSheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        PpIconButton(
            icon = PpIcons.Close,
            contentDescription = stringResource(R.string.close),
            onClick = onClose,
            modifier = Modifier.testTag("sheet_close"),
            containerColor = Color.Transparent,
        )
    }
}

@Composable
fun PoleParklaSystemBars(lightIcons: Boolean? = null) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    val resolvedLightIcons = lightIcons ?: isSystemInDarkTheme()
    SideEffect {
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !resolvedLightIcons
                isAppearanceLightNavigationBars = !resolvedLightIcons
            }
        }
    }
}
