package com.example

import android.app.Application
import android.util.Log
import com.example.data.HostPreferences
import com.example.service.WatchdogWorker
import com.example.util.CrashProtector
import com.google.firebase.FirebaseApp

class UbaidApplication : Application() {
    private val TAG = "UbaidApplication"

    override fun onCreate() {
        super.onCreate()
        CrashProtector.install()
        Log.d(TAG, "UbaidApplication launched with crash protector installed.")

        CrashProtector.safeRun(TAG, Unit) {
            try {
                if (FirebaseApp.getApps(this).isEmpty()) {
                    FirebaseApp.initializeApp(this)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase initial check caught: ${e.message}")
            }

            val prefs = HostPreferences.getInstance(this)
            if (prefs.isSetupCompleted) {
                WatchdogWorker.schedulePeriodicWatchdog(this)
            }
        }
    }
}
