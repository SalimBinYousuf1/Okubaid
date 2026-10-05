package com.example.model

enum class ConnectionState {
    STANDBY,
    WAITING_FOR_CONTROLLER,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR;

    val displayLabel: String
        get() = when (this) {
            STANDBY -> "Standby"
            WAITING_FOR_CONTROLLER -> "Waiting for Salim"
            CONNECTING -> "Connecting..."
            CONNECTED -> "Connected & Streaming"
            RECONNECTING -> "Reconnecting (ICE)"
            ERROR -> "Action Required"
        }
}

data class BatteryInfo(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 25f
)

data class NetworkInfo(
    val type: String = "WiFi",
    val isConnected: Boolean = true,
    val details: String = "Connected"
)

data class ScreenInfo(
    val isScreenOn: Boolean = true,
    val width: Int = 1080,
    val height: Int = 2400,
    val densityDpi: Int = 420
)

data class PermissionsState(
    val mediaProjectionGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val storageGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
) {
    val allRequiredGranted: Boolean
        get() = mediaProjectionGranted &&
                accessibilityGranted &&
                notificationListenerGranted &&
                storageGranted &&
                batteryOptimizationIgnored

    val grantedCount: Int
        get() = listOf(
            mediaProjectionGranted,
            accessibilityGranted,
            notificationListenerGranted,
            storageGranted,
            batteryOptimizationIgnored
        ).count { it }
}

sealed class FirebaseConfigStatus {
    data class Configured(val projectId: String) : FirebaseConfigStatus()
    data class Missing(val reason: String) : FirebaseConfigStatus()
}
