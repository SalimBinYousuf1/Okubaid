package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.HostPreferences
import com.example.util.CrashProtector

class BootCompletedReceiver : BroadcastReceiver() {
    private val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        CrashProtector.safeRun(TAG, Unit) {
            val action = intent.action
            Log.d(TAG, "Received broadcast action: $action")

            if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                val prefs = HostPreferences.getInstance(context)
                if (prefs.isSetupCompleted) {
                    Log.d(TAG, "Setup completed; scheduling watchdog and launching host background service.")
                    WatchdogWorker.schedulePeriodicWatchdog(context)

                    val startIntent = Intent(context, HostStreamingService::class.java).apply {
                        this.action = HostStreamingService.ACTION_START
                    }
                    try {
                        ContextCompat.startForegroundService(context, startIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to start foreground service on boot: ${e.message}", e)
                    }
                }
            }
        }
    }
}
