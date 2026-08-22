package com.perdolique.poleparkla.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.perdolique.poleparkla.R
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.service.PhotoStore
import com.perdolique.poleparkla.ui.components.PhotoThumbnail
import com.perdolique.poleparkla.ui.components.PoleParklaSystemBars
import com.perdolique.poleparkla.ui.components.PpBadge
import com.perdolique.poleparkla.ui.components.PpButton
import com.perdolique.poleparkla.ui.components.PpButtonStyle
import com.perdolique.poleparkla.ui.components.PpCard
import com.perdolique.poleparkla.ui.components.PpDialog
import com.perdolique.poleparkla.ui.components.PpIconButton
import com.perdolique.poleparkla.ui.components.PpIcons
import com.perdolique.poleparkla.ui.components.PpSheet
import com.perdolique.poleparkla.ui.components.PpSheetHeader
import com.perdolique.poleparkla.ui.components.PpTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TALLINN_TIME_ZONE = ZoneId.of("Europe/Tallinn")
private val HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

@Composable
fun HistoryScreen(
    reports: List<Report>,
    photoStore: PhotoStore,
    onBack: () -> Unit,
    onNewReport: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var menuReport by remember { mutableStateOf<Report?>(null) }
    var pendingDelete by remember { mutableStateOf<Report?>(null) }
    PoleParklaSystemBars()
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        PpTopBar(title = stringResource(R.string.history_title), onBack = onBack)
        if (reports.isEmpty()) {
            EmptyHistory(onNewReport, Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
            ) {
                items(reports, key = Report::id) { report ->
                    HistoryCard(
                        report = report,
                        photoStore = photoStore,
                        onOpen = { onOpen(report.id) },
                        onMore = { menuReport = report },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PpButton(
                text = stringResource(R.string.new_report),
                onClick = onNewReport,
                icon = PpIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    menuReport?.let { report ->
        PpSheet(onDismissRequest = { menuReport = null }) { dismiss ->
            PpSheetHeader(
                report.plate.ifBlank { stringResource(R.string.unknown_plate) },
                onClose = dismiss,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PpButton(
                    text = stringResource(R.string.open_report),
                    onClick = {
                        menuReport = null
                        onOpen(report.id)
                    },
                    style = PpButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth().testTag("history_open_${report.id}"),
                )
                PpButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        menuReport = null
                        pendingDelete = report
                    },
                    icon = PpIcons.Delete,
                    style = PpButtonStyle.Danger,
                    modifier = Modifier.fillMaxWidth().testTag("history_delete_${report.id}"),
                )
                Spacer(Modifier.size(12.dp))
            }
        }
    }
    pendingDelete?.let { report ->
        PpDialog(
            title = stringResource(R.string.delete_report_title),
            onDismissRequest = { pendingDelete = null },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                onDelete(report.id)
                pendingDelete = null
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
            confirmTestTag = "history_confirm_delete",
        )
    }
}

@Composable
private fun HistoryCard(
    report: Report,
    photoStore: PhotoStore,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    PpCard(
        modifier = Modifier.fillMaxWidth().testTag("history_report_${report.id}"),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            report.primaryPhoto?.let { photo ->
                PhotoThumbnail(
                    photo = photo,
                    photoStore = photoStore,
                    contentDescription = stringResource(R.string.report_photo),
                    modifier = Modifier.size(104.dp),
                )
            } ?: Box(
                modifier = Modifier
                    .size(104.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PpIcons.Camera, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    report.plate.ifBlank { stringResource(R.string.unknown_plate) },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    report.address.ifBlank { report.coordinatesText() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Text(
                    report.occurredAtText(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                PpBadge(
                    text = statusLabel(report.status),
                    isComplete = report.status != ReportStatus.DRAFT,
                )
            }
            PpIconButton(
                icon = PpIcons.More,
                contentDescription = stringResource(R.string.report_actions),
                onClick = onMore,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("history_more_${report.id}"),
            )
        }
    }
}

@Composable
private fun EmptyHistory(onNewReport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PpIcons.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Text(
            stringResource(R.string.no_reports),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 20.dp),
        )
        PpButton(
            text = stringResource(R.string.new_report),
            onClick = onNewReport,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun statusLabel(status: ReportStatus): String = stringResource(
    when (status) {
        ReportStatus.DRAFT -> R.string.status_draft
        ReportStatus.READY -> R.string.status_ready
        ReportStatus.HANDED_OFF_TO_MAIL -> R.string.status_handed_off
    },
)

private fun Report.occurredAtText(): String = Instant.ofEpochMilli(occurredAtEpochMillis)
    .atZone(TALLINN_TIME_ZONE)
    .format(HISTORY_TIME_FORMATTER)

private fun Report.coordinatesText(): String =
    if (latitude != null && longitude != null) "%.6f, %.6f".format(latitude, longitude) else ""
