package dev.framelock.core.domain.system

/** Mirrors Android's thermal status levels, from no throttling to imminent shutdown. */
enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }

enum class MemoryPressure { NORMAL, MODERATE, CRITICAL }

data class DeviceSnapshot(
    val model: String,
    val osVersion: String,
    val batteryPct: Int,
    val batteryTempC: Float,
    val thermalLevel: ThermalLevel,
)
