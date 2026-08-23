package com.perdolique.poleparkla.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.components.PhotoThumbnail
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpIconButton
import com.perdolique.poleparkla.ui.components.PpIcons
import com.perdolique.poleparkla.ui.components.PpSheet
import com.perdolique.poleparkla.ui.components.PpSheetHeader

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
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
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
        Spacer(Modifier.height(2.dp))
    }
}
