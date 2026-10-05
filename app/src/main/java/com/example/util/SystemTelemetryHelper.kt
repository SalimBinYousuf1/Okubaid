package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.model.BatteryInfo
import com.example.model.NetworkInfo
import com.example.model.ScreenInfo

object SystemTelemetryHelper {
    private const val TAG = "SystemTelemetryHelper"

    fun getBatteryInfo(context: Context): BatteryInfo {
        return CrashProtector.safeRun(TAG, BatteryInfo()) {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250
            val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 100

            BatteryInfo(
                levelPercent = percent,
                isCharging = isCharging,
                temperatureCelsius = temperature / 10f
            )
        }
    }

    fun getNetworkInfo(context: Context): NetworkInfo {
        return CrashProtector.safeRun(TAG, NetworkInfo()) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@safeRun NetworkInfo(type = "Unknown", isConnected = false, details = "No Service")

            val activeNetwork = cm.activeNetwork
                ?: return@safeRun NetworkInfo(type = "None", isConnected = false, details = "Disconnected")

            val caps = cm.getNetworkCapabilities(activeNetwork)
                ?: return@safeRun NetworkInfo(type = "None", isConnected = false, details = "Disconnected")

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    NetworkInfo(type = "WiFi", isConnected = true, details = "High-Speed WLAN")
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    NetworkInfo(type = "Cellular", isConnected = true, details = "Mobile Network")
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    NetworkInfo(type = "Ethernet", isConnected = true, details = "Wired LAN")
                }
                else -> {
                    NetworkInfo(type = "Other", isConnected = true, details = "Connected")
                }
            }
        }
    }

    fun getScreenInfo(context: Context): ScreenInfo {
        return CrashProtector.safeRun(TAG, ScreenInfo()) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isScreenOn = pm?.isInteractive ?: true

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(metrics)

            val width = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
            val height = if (metrics.heightPixels > 0) metrics.heightPixels else 2400
            val density = if (metrics.densityDpi > 0) metrics.densityDpi else 420

            ScreenInfo(
                isScreenOn = isScreenOn,
                width = width,
                height = height,
                densityDpi = density
            )
        }
    }

    fun getDeviceModelName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }
}
