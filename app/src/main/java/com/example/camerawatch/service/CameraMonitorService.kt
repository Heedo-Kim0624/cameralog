package com.example.camerawatch.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.camerawatch.SingletonHolder
import com.example.camerawatch.data.CameraSession
import com.example.camerawatch.data.CameraSessionRepository
import com.example.camerawatch.util.MonitoringConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CameraMonitorService : Service() {

    private val cameraManager by lazy { getSystemService(CameraManager::class.java) }
    private val notificationHelper by lazy { NotificationHelper(this) }
    private val repository: CameraSessionRepository get() = SingletonHolder.repository
    private val config = MonitoringConfig()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val stateLock = Any()
    private val activeCameraIds = mutableSetOf<String>()
    private val sessionCameraIds = mutableSetOf<String>()
    private var torchOn: Boolean = false
    private var torchOverlapFlag: Boolean = false
    private var currentSession: CameraSession? = null
    private var pendingOpenJob: Job? = null
    private var pendingCloseJob: Job? = null
    private var monitoringPaused: Boolean = true

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            if (monitoringPaused) return
            synchronized(stateLock) {
                activeCameraIds += cameraId
                sessionCameraIds += cameraId
            }
            scheduleOpenIfNeeded()
        }

        override fun onCameraAvailable(cameraId: String) {
            if (monitoringPaused) return
            val shouldScheduleClose = synchronized(stateLock) {
                activeCameraIds -= cameraId
                activeCameraIds.isEmpty()
            }
            if (shouldScheduleClose) {
                scheduleCloseIfNeeded()
            }
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchOn = enabled
            if (enabled && currentSession != null) {
                torchOverlapFlag = true
            }
            if (!enabled) {
                scheduleCloseIfNeeded()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        startForeground(NotificationHelper.NOTIFICATION_ID, notificationHelper.build(isPaused()))
        registerCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseMonitoring()
            ACTION_RESUME -> resumeMonitoring()
            ACTION_FLUSH -> flushActiveSession(force = false)
            ACTION_STOP -> stopSelfSafely()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterCallbacks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerCallbacks() {
        val executor = ContextCompat.getMainExecutor(this)
        cameraManager.registerAvailabilityCallback(executor, availabilityCallback)
        cameraManager.registerTorchCallback(executor, torchCallback)
    }

    private fun unregisterCallbacks() {
        runCatching { cameraManager.unregisterAvailabilityCallback(availabilityCallback) }
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
    }

    private fun scheduleOpenIfNeeded() {
        pendingCloseJob?.cancel()
        if (currentSession != null || torchOn) return
        pendingOpenJob?.cancel()
        pendingOpenJob = serviceScope.launch {
            delay(config.debounceOpenMs)
            if (monitoringPaused) return@launch
            val ids = synchronized(stateLock) { activeCameraIds.toSet() }
            if (ids.isNotEmpty() && !torchOn && currentSession == null) {
                beginSession(ids)
            }
        }
    }

    private fun scheduleCloseIfNeeded() {
        if (torchOn) return
        pendingCloseJob?.cancel()
        pendingCloseJob = serviceScope.launch {
            delay(config.debounceCloseMs)
            if (monitoringPaused) return@launch
            val ids = synchronized(stateLock) { activeCameraIds.toSet() }
            if (ids.isEmpty()) {
                endSession()
            }
        }
    }

    private suspend fun ensurePruned() {
        repository.prune(config.pruneMaxAgeMs, config.pruneMaxEntries)
    }

    private fun beginSession(ids: Set<String>) {
        pendingOpenJob?.cancel()
        serviceScope.launch {
            val session = repository.startSession(
                cameraIds = ids,
                source = CameraSession.SessionSource.AVAILABILITY,
                torchOverlap = torchOn
            )
            currentSession = session
            synchronized(stateLock) {
                sessionCameraIds.clear()
                sessionCameraIds.addAll(ids)
            }
            torchOverlapFlag = torchOn
            updateNotification()
        }
    }

    private fun endSession() {
        pendingCloseJob?.cancel()
        val session = currentSession ?: return
        serviceScope.launch {
            finalizeCurrentSession(force = false, session = session)
            updateNotification()
        }
    }

    private suspend fun finalizeCurrentSession(force: Boolean, session: CameraSession? = currentSession) {
        val activeSession = session ?: return
        val endTime = System.currentTimeMillis()
        val duration = endTime - activeSession.startTimestamp
        val idsSnapshot = synchronized(stateLock) { sessionCameraIds.toSet() }
        if (!force && duration < config.minSessionMs) {
            repository.deleteSession(activeSession.sessionId)
        } else {
            repository.finalizeSession(
                session = activeSession,
                cameraIds = idsSnapshot,
                endTimestamp = endTime,
                torchOverlap = torchOverlapFlag
            )
        }
        ensurePruned()
        resetSessionState()
    }

    private fun flushActiveSession(force: Boolean) {
        val session = currentSession ?: return
        runBlocking {
            finalizeCurrentSession(force = force, session = session)
        }
        updateNotification()
    }

    private fun resetSessionState() {
        currentSession = null
        torchOverlapFlag = false
        synchronized(stateLock) {
            sessionCameraIds.clear()
            activeCameraIds.clear()
        }
    }

    private fun pauseMonitoring() {
        if (monitoringPaused) return
        monitoringPaused = true
        cancelPendingJobs()
        flushActiveSession(force = false)
        updateNotification()
    }

    private fun resumeMonitoring() {
        if (!monitoringPaused) return
        monitoringPaused = false
        updateNotification()
    }

    private fun cancelPendingJobs() {
        pendingOpenJob?.cancel()
        pendingCloseJob?.cancel()
    }

    private fun isPaused(): Boolean = monitoringPaused

    private fun updateNotification() {
        val notification = notificationHelper.build(isPaused())
        notificationHelper.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_PAUSE = "com.example.camerawatch.action.PAUSE"
        const val ACTION_RESUME = "com.example.camerawatch.action.RESUME"
        const val ACTION_FLUSH = "com.example.camerawatch.action.FLUSH"
        const val ACTION_STOP = "com.example.camerawatch.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, CameraMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun resume(context: Context) {
            sendAction(context, ACTION_RESUME)
        }

        fun pause(context: Context) {
            sendAction(context, ACTION_PAUSE)
        }

        fun flush(context: Context) {
            sendAction(context, ACTION_FLUSH)
        }

        private fun sendAction(context: Context, action: String) {
            val intent = Intent(context, CameraMonitorService::class.java).apply {
                this.action = action
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
