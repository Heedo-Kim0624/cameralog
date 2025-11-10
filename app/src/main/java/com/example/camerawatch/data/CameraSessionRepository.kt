package com.example.camerawatch.data

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CameraSessionRepository(private val dao: CameraSessionDao) {

    val sessions: Flow<List<CameraSession>> = dao.observeSessions()

    suspend fun startSession(
        cameraIds: Set<String>,
        source: CameraSession.SessionSource,
        torchOverlap: Boolean
    ): CameraSession {
        val session = CameraSession(
            startTimestamp = System.currentTimeMillis(),
            endTimestamp = null,
            durationMs = null,
            cameraIds = cameraIds,
            frontRearHint = determineHint(cameraIds),
            source = source,
            torchOverlap = torchOverlap,
            deviceModel = Build.MODEL.orUnknown(),
            apiLevel = Build.VERSION.SDK_INT
        )
        dao.upsert(session)
        return session
    }

    suspend fun finalizeSession(
        session: CameraSession,
        cameraIds: Set<String>,
        endTimestamp: Long,
        torchOverlap: Boolean
    ) {
        val duration = (endTimestamp - session.startTimestamp).coerceAtLeast(0)
        dao.finalizeSession(
            sessionId = session.sessionId,
            endTimestamp = endTimestamp,
            duration = duration,
            torchOverlap = torchOverlap,
            cameraIds = cameraIds.joinToString(","),
            frontHint = determineHint(cameraIds).name
        )
    }

    suspend fun prune(maxAgeMillis: Long, maxEntries: Int) {
        val threshold = System.currentTimeMillis() - maxAgeMillis
        dao.deleteOlderThan(threshold)
        val count = dao.countSessions()
        if (count <= maxEntries) return
        val overflow = count - maxEntries
        if (overflow > 0) {
            dao.deleteOldest(overflow)
        }
    }

    suspend fun getSessionsFrom(startTimestamp: Long): List<CameraSession> {
        return dao.sessionsFrom(startTimestamp)
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun exportToCsv(context: Context): File = withContext(Dispatchers.IO) {
        val sessionsSnapshot = sessions.first()
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "exports"
        )
        if (!dir.exists()) dir.mkdirs()
        val timestamp = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val file = File(dir, "camera_sessions_$timestamp.csv")
        file.bufferedWriter().use { writer ->
            writer.appendLine("start_ts,end_ts,duration_ms,camera_ids,front_rear_hint,torch_overlap,device_model,api")
            sessionsSnapshot.forEach { session ->
                writer.appendLine(
                    listOf(
                        session.startTimestamp,
                        session.endTimestamp ?: "",
                        session.durationMs ?: "",
                        session.cameraIds.joinToString(prefix = "[", postfix = "]"),
                        session.frontRearHint.name.lowercase(),
                        session.torchOverlap,
                        session.deviceModel.replace(',', ' '),
                        session.apiLevel
                    ).joinToString(separator = ",")
                )
            }
        }
        file
    }

    suspend fun exportSessionsToCsv(
        context: Context,
        sessions: List<CameraSession>,
        fileNameBase: String
    ): File = withContext(Dispatchers.IO) {
        require(fileNameBase.isNotBlank())
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "exports"
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${fileNameBase}.csv")
        val timestampFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)
        file.bufferedWriter().use { writer ->
            writer.appendLine("start_time,end_time,duration_ms,device")
            sessions.forEach { session ->
                val start = timestampFormatter.format(Instant.ofEpochMilli(session.startTimestamp))
                val end = session.endTimestamp?.let {
                    timestampFormatter.format(Instant.ofEpochMilli(it))
                } ?: ""
                val duration = session.durationMs ?: ""
                val device = session.deviceModel.replace(',', ' ')
                writer.appendLine(
                    listOf(start, end, duration, device).joinToString(separator = ",")
                )
            }
        }
        file
    }

    private fun determineHint(ids: Set<String>): CameraSession.FrontRearHint {
        if (ids.isEmpty()) return CameraSession.FrontRearHint.UNKNOWN
        if (ids.any { it.contains("front", ignoreCase = true) || it.contains("1") }) {
            return CameraSession.FrontRearHint.FRONT
        }
        if (ids.any { it.contains("back", ignoreCase = true) || it.contains("0") }) {
            return CameraSession.FrontRearHint.REAR
        }
        return CameraSession.FrontRearHint.UNKNOWN
    }

    private fun String?.orUnknown(): String = this ?: "unknown"
}
