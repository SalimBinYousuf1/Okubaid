package com.example.model

import org.json.JSONObject

sealed class ControlCommand {
    data class Tap(val xPercent: Float, val yPercent: Float) : ControlCommand()
    data class Swipe(
        val startXPercent: Float,
        val startYPercent: Float,
        val endXPercent: Float,
        val endYPercent: Float,
        val durationMs: Long = 300L
    ) : ControlCommand()
    data class GlobalAction(val actionName: String) : ControlCommand() // "BACK", "HOME", "RECENTS", "NOTIFICATIONS"
    data class TextInjection(val text: String) : ControlCommand()

    companion object {
        fun fromJson(jsonStr: String): ControlCommand? {
            return try {
                val obj = JSONObject(jsonStr)
                when (obj.optString("type").lowercase()) {
                    "tap" -> Tap(
                        xPercent = obj.optDouble("x", 0.5).toFloat(),
                        yPercent = obj.optDouble("y", 0.5).toFloat()
                    )
                    "swipe" -> Swipe(
                        startXPercent = obj.optDouble("startX", 0.5).toFloat(),
                        startYPercent = obj.optDouble("startY", 0.5).toFloat(),
                        endXPercent = obj.optDouble("endX", 0.5).toFloat(),
                        endYPercent = obj.optDouble("endY", 0.5).toFloat(),
                        durationMs = obj.optLong("durationMs", 300L)
                    )
                    "global" -> GlobalAction(
                        actionName = obj.optString("action", "BACK").uppercase()
                    )
                    "text" -> TextInjection(
                        text = obj.optString("text", "")
                    )
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
