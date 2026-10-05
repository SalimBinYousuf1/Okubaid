package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.ControlCommand
import com.example.util.CrashProtector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UbaidAccessibilityService : AccessibilityService() {
    private val TAG = "UbaidAccessibility"

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.d(TAG, "Ubaid Accessibility Service connected and ready for remote gestures.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events received if needed for context
    }

    override fun onInterrupt() {
        Log.w(TAG, "Ubaid Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceActive.value = false
        }
    }

    fun dispatchCommand(command: ControlCommand, screenWidth: Int, screenHeight: Int): Boolean {
        return CrashProtector.safeRun(TAG, false) {
            when (command) {
                is ControlCommand.Tap -> {
                    val px = (command.xPercent.coerceIn(0f, 1f) * screenWidth)
                    val py = (command.yPercent.coerceIn(0f, 1f) * screenHeight)
                    dispatchTap(px, py)
                }
                is ControlCommand.Swipe -> {
                    val startX = (command.startXPercent.coerceIn(0f, 1f) * screenWidth)
                    val startY = (command.startYPercent.coerceIn(0f, 1f) * screenHeight)
                    val endX = (command.endXPercent.coerceIn(0f, 1f) * screenWidth)
                    val endY = (command.endYPercent.coerceIn(0f, 1f) * screenHeight)
                    dispatchSwipe(startX, startY, endX, endY, command.durationMs)
                }
                is ControlCommand.GlobalAction -> {
                    dispatchGlobal(command.actionName)
                }
                is ControlCommand.TextInjection -> {
                    injectText(command.text)
                }
            }
        }
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap executed successfully at ($x, $y)")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap gesture was cancelled at ($x, $y)")
            }
        }, null)
    }

    private fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val duration = durationMs.coerceIn(50L, 2000L)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed from ($startX, $startY) to ($endX, $endY)")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled")
            }
        }, null)
    }

    private fun dispatchGlobal(action: String): Boolean {
        val globalActionCode = when (action.uppercase()) {
            "BACK" -> GLOBAL_ACTION_BACK
            "HOME" -> GLOBAL_ACTION_HOME
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            "LOCK" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                GLOBAL_ACTION_LOCK_SCREEN
            } else {
                GLOBAL_ACTION_BACK
            }
            else -> GLOBAL_ACTION_BACK
        }
        return performGlobalAction(globalActionCode)
    }

    private fun injectText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: rootNode
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (focusedNode != rootNode) {
            focusedNode.recycle()
        }
        rootNode.recycle()
        return result
    }

    companion object {
        @Volatile
        var instance: UbaidAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()
    }
}
