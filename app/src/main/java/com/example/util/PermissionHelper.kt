package com.example.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.service.UbaidAccessibilityService
import com.example.service.UbaidNotificationListenerService

object PermissionHelper {
    private const val TAG = "PermissionHelper"

    /**
     * Checks if Ubaid's Accessibility Service is currently active in Android Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return CrashProtector.safeRun(TAG, false) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (service in enabledServices) {
                    val component = service.resolveInfo?.serviceInfo?.let {
                        ComponentName(it.packageName, it.name)
                    }
                    if (component?.packageName == context.packageName &&
                        component.className.contains("UbaidAccessibilityService")
                    ) {
                        return@safeRun true
                    }
                }
            }

            // Secondary check via Settings.Secure
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val expectedComponent = ComponentName(context, UbaidAccessibilityService::class.java).flattenToString()
            enabledServicesSetting.contains(expectedComponent) ||
                    enabledServicesSetting.contains("${context.packageName}/")
        }
    }

    /**
     * Checks if Ubaid has permission to read notifications via NotificationListenerService.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        return CrashProtector.safeRun(TAG, false) {
            val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (packageNames.contains(context.packageName)) {
                return@safeRun true
            }

            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            val expectedComponent = ComponentName(context, UbaidNotificationListenerService::class.java).flattenToString()
            flat.contains(expectedComponent) || flat.contains(context.packageName)
        }
    }

    /**
     * Checks if All Files Access (MANAGE_EXTERNAL_STORAGE) or standard storage is granted.
     */
    fun isStorageAccessGranted(context: Context): Boolean {
        return CrashProtector.safeRun(TAG, false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Checks if Battery Optimization exemption is active for background reliability.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return CrashProtector.safeRun(TAG, false) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
    }

    /**
     * Intent to open Accessibility settings to enable UbaidAccessibilityService.
     */
    fun createAccessibilitySettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Intent to open Notification Listener settings.
     */
    fun createNotificationListenerSettingsIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, UbaidNotificationListenerService::class.java).flattenToString()
                )
            }
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        return intent.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    }

    /**
     * Intent to open All-Files access settings.
     */
    fun createStorageSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    /**
     * Intent to request ignoring battery optimization.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent {
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    /**
     * Safely launch an intent with fallback to general app details if action not found.
     */
    fun launchIntentSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch intent: ${e.message}", e)
            try {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed fallback intent: ${e2.message}", e2)
                false
            }
        }
    }
}
