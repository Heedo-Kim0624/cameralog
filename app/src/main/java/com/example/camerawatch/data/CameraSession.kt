package com.example.camerawatch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "camera_session")
data class CameraSession(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "start_ts")
    val startTimestamp: Long,
    @ColumnInfo(name = "end_ts")
    val endTimestamp: Long?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    @ColumnInfo(name = "camera_ids")
    val cameraIds: Set<String>,
    @ColumnInfo(name = "front_rear_hint")
    val frontRearHint: FrontRearHint,
    @ColumnInfo(name = "source")
    val source: SessionSource,
    @ColumnInfo(name = "torch_overlap")
    val torchOverlap: Boolean,
    @ColumnInfo(name = "device_model")
    val deviceModel: String,
    @ColumnInfo(name = "api_level")
    val apiLevel: Int
) {
    enum class FrontRearHint { FRONT, REAR, UNKNOWN }

    enum class SessionSource { AVAILABILITY, HEURISTIC }
}
