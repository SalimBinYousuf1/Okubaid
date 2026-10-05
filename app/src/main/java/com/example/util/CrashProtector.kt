package com.example.util

import android.os.Looper
import android.util.Log
import com.example.service.MediaProjectionHolder

object CrashProtector {
    const val TAG = "UbaidCrashProtector"
    var lastError: String? = null

    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val errorMsg = "Uncaught exception on thread ${thread.name}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
            Log.e(TAG, errorMsg)
            lastError = throwable.message ?: "Unexpected error"

            val isMainThread = (Looper.myLooper() == Looper.getMainLooper()) || thread.name == "main"
            val isRecoverableBackgroundThread = thread.name.contains("ScreenCapture", ignoreCase = true) ||
                    thread.name.contains("webrtc", ignoreCase = true) ||
                    thread.name.contains("SurfaceTexture", ignoreCase = true) ||
                    thread.name.contains("DefaultDispatcher", ignoreCase = true) ||
                    thread.name.contains("EGL", ignoreCase = true) ||
                    throwable is SecurityException ||
                    throwable is IllegalStateException

            // Prevent app autoclose on background capture, WebRTC, or transient security issues
            if (!isMainThread || isRecoverableBackgroundThread) {
                Log.w(TAG, "Gracefully suppressed uncaught background exception on thread '${thread.name}' to prevent app auto-close: ${throwable.message}")
                if (thread.name.contains("ScreenCapture", ignoreCase = true) || throwable is SecurityException) {
                    MediaProjectionHolder.markRevoked()
                }
                return@setDefaultUncaughtExceptionHandler
            }

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
