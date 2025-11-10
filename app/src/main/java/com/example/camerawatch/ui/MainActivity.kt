package com.example.camerawatch.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.camerawatch.R
import com.example.camerawatch.SingletonHolder
import com.example.camerawatch.ui.theme.CameraWatchTheme
import kotlinx.coroutines.flow.collect
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels {
        viewModelFactory {
            initializer {
                SessionViewModel(SingletonHolder.repository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraWatchTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val sessions by viewModel.visibleSessions.collectAsStateWithLifecycle(emptyList())
                val collectionState by viewModel.collectionState.collectAsStateWithLifecycle(CollectionState())
                val historyFiles by viewModel.historyFiles.collectAsState()
                var showHistory by remember { mutableStateOf(false) }
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    viewModel.refreshHistory(context)
                }

                LaunchedEffect(showHistory) {
                    if (showHistory) {
                        viewModel.refreshHistory(context)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CollectionEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                            is CollectionEvent.ShareCsv -> shareCsv(context, event.file)
                        }
                    }
                }

                SessionListScreen(
                    sessions = sessions,
                    collecting = collectionState.collecting,
                    onStartCollection = { viewModel.startCollection(context) },
                    onStopCollection = { viewModel.stopCollection(context) },
                    onHistoryClick = { showHistory = true },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                )

                if (showHistory) {
                    HistoryDialog(
                        files = historyFiles,
                        onShare = {
                            viewModel.shareHistoryFile(it)
                            showHistory = false
                        },
                        onDismiss = { showHistory = false }
                    )
                }

                NotificationPermissionRequest(snackbarHostState)
            }
        }
    }
}

@Composable
private fun NotificationPermissionRequest(snackbarHostState: SnackbarHostState) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val permissionResult = remember { mutableStateOf<Boolean?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionResult.value = granted
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(permissionResult.value) {
        if (permissionResult.value == false) {
            snackbarHostState.showSnackbar("Notification permission denied; foreground service may be limited")
        }
    }
}

@Composable
private fun HistoryDialog(
    files: List<File>,
    onShare: (File) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.history)) },
        text = {
            if (files.isEmpty()) {
                Text(text = stringResource(id = R.string.history_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    files.forEach { file ->
                        HistoryFileRow(file = file, onShare = onShare)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.close))
            }
        }
    )
}

@Composable
private fun HistoryFileRow(
    file: File,
    onShare: (File) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(text = file.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatTimestamp(file.lastModified()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { onShare(file) }) {
            Text(text = stringResource(id = R.string.share))
        }
    }
}

private fun formatTimestamp(lastModified: Long): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(java.time.Instant.ofEpochMilli(lastModified))
}

private fun shareCsv(context: android.content.Context, file: File) {
    val authority = "${context.packageName}.provider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
    }
    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_csv_title))
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.share_error_no_app), Toast.LENGTH_SHORT).show()
    }
}
