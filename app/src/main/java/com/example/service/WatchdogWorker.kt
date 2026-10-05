package com.example.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.HostPreferences
import com.example.util.CrashProtector
import java.util.concurrent.TimeUnit

class WatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return CrashProtector.safeRun(TAG, Result.success()) {
            val prefs = HostPreferences.getInstance(applicationContext)
            if (!prefs.isSetupCompleted) {
                Log.d(TAG, "Setup not yet completed, skipping watchdog check.")
                return@safeRun Result.success()
            }

            if (!HostStreamingService.isServiceRunning.value) {
                Log.w(TAG, "Watchdog noticed HostStreamingService is NOT running! Restarting...")
                val intent = Intent(applicationContext, HostStreamingService::class.java).apply {
                    action = HostStreamingService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(applicationContext, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Watchdog restart failed: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Watchdog check: HostStreamingService is healthy and active.")
            }

            Result.success()
        }
    }

    companion object {
        private const val TAG = "WatchdogWorker"
        private const val WORK_NAME = "ubaid_host_watchdog_work"

        fun schedulePeriodicWatchdog(context: Context) {
            CrashProtector.safeRun(TAG, Unit) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<WatchdogWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
                Log.d(TAG, "Periodic watchdog enqueued successfully.")
            }
        }
    }
}
