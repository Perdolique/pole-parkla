package com.perdolique.poleparkla.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.service.PhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

@Composable
fun LanguageSelector(selected: String, onSelected: (String) -> Unit) {
    val options = listOf(
        "" to R.string.language_system,
        "en" to R.string.language_english,
        "ru" to R.string.language_russian,
        "et" to R.string.language_estonian,
    )
    Column {
        options.chunked(2).forEach { rowOptions ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                rowOptions.forEach { (tag, label) ->
                    val testTag = if (tag.isBlank()) "language_system" else "language_$tag"
                    PpCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(testTag),
                        onClick = { onSelected(tag) },
                        selected = selected == tag,
                        role = Role.RadioButton,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected == tag) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoThumbnail(
    photo: ReportPhoto,
    photoStore: PhotoStore,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by rememberManagedBitmap(photo.filePath) { photoStore.decodeForModel(photo, 480) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Owns a decoded bitmap until its keyed composition leaves. */
@Composable
internal fun rememberManagedBitmap(
    bitmapKey: Any?,
    load: suspend () -> Bitmap?,
): State<Bitmap?> {
    val bitmapState = remember(bitmapKey) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bitmapKey) {
        val decoded = try {
            load()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        bitmapState.value = decoded
        try {
            awaitCancellation()
        } finally {
            if (bitmapState.value === decoded) bitmapState.value = null
            decoded?.recycle()
        }
    }
    return bitmapState
}
