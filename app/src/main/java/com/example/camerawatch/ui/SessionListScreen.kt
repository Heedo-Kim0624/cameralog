package com.example.camerawatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.camerawatch.R
import com.example.camerawatch.data.CameraSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    sessions: List<CameraSession>,
    collecting: Boolean,
    onStartCollection: () -> Unit,
    onStopCollection: () -> Unit,
    onHistoryClick: () -> Unit,
    snackbarHost: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { snackbarHost() }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            ControlRow(
                collecting = collecting,
                onStart = onStartCollection,
                onStop = onStopCollection,
                onHistory = onHistoryClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (sessions.isEmpty()) {
                EmptyState()
            } else {
                SessionList(
                    modifier = Modifier.fillMaxSize(),
                    sessions = sessions
                )
            }
        }
    }
}

@Composable
private fun ControlRow(
    collecting: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onStart,
            enabled = !collecting,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(id = R.string.start_collection))
        }
        Button(
            onClick = onStop,
            enabled = collecting,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(id = R.string.stop_collection))
        }
        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(id = R.string.history))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.no_sessions),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SessionList(
    modifier: Modifier = Modifier,
    sessions: List<CameraSession>
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions, key = { it.sessionId }) { session ->
            SessionCard(session = session)
        }
    }
}

@Composable
private fun SessionCard(session: CameraSession) {
    val formatter = rememberDateFormatter()
    val start = formatter.format(Instant.ofEpochMilli(session.startTimestamp))
    val end = session.endTimestamp?.let { formatter.format(Instant.ofEpochMilli(it)) } ?: "--"
    val duration = session.durationMs?.let { formatDuration(it) } ?: "--"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = start, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = "End: $end", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Duration: $duration", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Cameras: ${session.cameraIds.joinToString()}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Hint: ${session.frontRearHint}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Torch overlap: ${session.torchOverlap}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Device: ${session.deviceModel} (API ${session.apiLevel})", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun rememberDateFormatter(): DateTimeFormatter {
    val zone = ZoneId.systemDefault()
    return remember(zone) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return buildString {
        if (hours > 0) {
            append(hours).append("h ")
        }
        if (minutes > 0 || hours > 0) {
            append(minutes).append("m ")
        }
        append(seconds).append("s")
    }
}
