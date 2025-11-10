package com.example.camerawatch.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraSessionDao {
    @Upsert
    suspend fun upsert(session: CameraSession)

    @Query("""
        UPDATE camera_session
        SET end_ts = :endTimestamp,
            duration_ms = :duration,
            torch_overlap = :torchOverlap,
            camera_ids = :cameraIds,
            front_rear_hint = :frontHint
        WHERE session_id = :sessionId
    """)
    suspend fun finalizeSession(
        sessionId: String,
        endTimestamp: Long,
        duration: Long,
        torchOverlap: Boolean,
        cameraIds: String,
        frontHint: String
    )

    @Query("SELECT * FROM camera_session ORDER BY start_ts DESC")
    fun observeSessions(): Flow<List<CameraSession>>

    @Query("SELECT * FROM camera_session ORDER BY start_ts DESC LIMIT 1")
    suspend fun latestSession(): CameraSession?

    @Query("DELETE FROM camera_session WHERE start_ts < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("SELECT COUNT(*) FROM camera_session")
    suspend fun countSessions(): Int

    @Query("DELETE FROM camera_session WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM camera_session WHERE start_ts >= :startTimestamp ORDER BY start_ts ASC")
    suspend fun sessionsFrom(startTimestamp: Long): List<CameraSession>

    @Query(
        "DELETE FROM camera_session WHERE session_id IN (" +
            "SELECT session_id FROM camera_session ORDER BY start_ts ASC LIMIT :count" +
            ")"
    )
    suspend fun deleteOldest(count: Int)
}
