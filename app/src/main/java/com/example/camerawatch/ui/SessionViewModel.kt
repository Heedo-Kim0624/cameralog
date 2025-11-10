package com.example.camerawatch.ui

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.camerawatch.data.CameraSession
import com.example.camerawatch.data.CameraSessionRepository
import com.example.camerawatch.service.CameraMonitorService
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: CameraSessionRepository
) : ViewModel() {

    private val _collectionState = MutableStateFlow(CollectionState())
    val collectionState: StateFlow<CollectionState> = _collectionState.asStateFlow()

    private val _visibleSessions = MutableStateFlow<List<CameraSession>>(emptyList())
    val visibleSessions: StateFlow<List<CameraSession>> = _visibleSessions.asStateFlow()

    private val _historyFiles = MutableStateFlow<List<File>>(emptyList())
    val historyFiles: StateFlow<List<File>> = _historyFiles.asStateFlow()

    private val _events = MutableSharedFlow<CollectionEvent>()
    val events = _events.asSharedFlow()

    private var sessionCollectionJob: Job? = null

    fun startCollection(context: Context) {
        val current = _collectionState.value
        if (current.collecting) {
            emitMessage("이미 수집이 진행 중입니다.")
            return
        }
        val startTimestamp = System.currentTimeMillis()
        _collectionState.value = CollectionState(collecting = true, collectionStartTime = startTimestamp)
        val appContext = context.applicationContext
        CameraMonitorService.start(appContext)
        CameraMonitorService.resume(appContext)
        beginCollectingVisibleSessions(startTimestamp)
        emitMessage("카메라 로그 수집을 시작합니다.")
    }

    fun stopCollection(context: Context) {
        val current = _collectionState.value
        val startTimestamp = current.collectionStartTime
        if (!current.collecting || startTimestamp == null) {
            emitMessage("수집 중이 아닙니다.")
            return
        }
        _collectionState.value = CollectionState(collecting = false, collectionStartTime = null)
        val appContext = context.applicationContext
        CameraMonitorService.flush(appContext)
        CameraMonitorService.pause(appContext)
        sessionCollectionJob?.cancel()
        _visibleSessions.value = emptyList()
        viewModelScope.launch {
            // Allow service to persist any pending session updates.
            delay(250L)
            val sessionsSinceStart = repository.getSessionsFrom(startTimestamp)
            if (sessionsSinceStart.isEmpty()) {
                emitMessage("수집된 카메라 로그가 없습니다.")
            } else {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault())
                val fileNameBase = formatter.format(Instant.ofEpochMilli(startTimestamp))
                runCatching {
                    repository.exportSessionsToCsv(appContext, sessionsSinceStart, fileNameBase)
                }.onSuccess { file ->
                    emitMessage("CSV 파일이 생성되었습니다.")
                    refreshHistory(appContext)
                    _events.emit(CollectionEvent.ShareCsv(file))
                }.onFailure { throwable ->
                    emitMessage("CSV 생성에 실패했습니다: ${throwable.message ?: "Unknown error"}")
                }
            }
        }
    }

    private fun beginCollectingVisibleSessions(startTimestamp: Long) {
        sessionCollectionJob?.cancel()
        sessionCollectionJob = viewModelScope.launch {
            repository.sessions.collectLatest { allSessions ->
                val filtered = allSessions.filter { it.startTimestamp >= startTimestamp }
                _visibleSessions.value = filtered
            }
        }
    }

    fun shareHistoryFile(file: File) {
        viewModelScope.launch {
            _events.emit(CollectionEvent.ShareCsv(file))
        }
    }

    fun refreshHistory(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _historyFiles.value = listHistoryFiles(appContext)
        }
    }

    private fun listHistoryFiles(context: Context): List<File> {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(CollectionEvent.ShowMessage(message))
        }
    }
}

data class CollectionState(
    val collecting: Boolean = false,
    val collectionStartTime: Long? = null
)

sealed class CollectionEvent {
    data class ShowMessage(val message: String) : CollectionEvent()
    data class ShareCsv(val file: File) : CollectionEvent()
}
