package com.example.camerawatch.util

data class MonitoringConfig(
    val debounceOpenMs: Long = 350L,
    val debounceCloseMs: Long = 1000L,
    val minSessionMs: Long = 1200L,
    val pruneMaxAgeMs: Long = 90L * 24L * 60L * 60L * 1000L,
    val pruneMaxEntries: Int = 2000
)
