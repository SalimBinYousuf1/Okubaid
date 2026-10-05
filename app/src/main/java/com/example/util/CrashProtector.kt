package com.example.util

import android.util.Log

object CrashProtector {
    const val TAG = "UbaidCrashProtector"
    var lastError: String? = null

    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val errorMsg = "Uncaught exception on thread ${thread.name}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
            Log.e(TAG, errorMsg)
            lastError = errorMsg
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun <T> safeRun(tag: String = TAG, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            Log.e(tag, "Caught exception in safeRun: ${e.message}", e)
            lastError = e.message
            fallback
        }
    }
}
