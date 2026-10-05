package com.example.model

import org.json.JSONObject

data class NotificationPayload(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isRemoved: Boolean = false
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", if (isRemoved) "notification_removed" else "notification_posted")
        obj.put("key", key)
        obj.put("packageName", packageName)
        obj.put("appName", appName)
        obj.put("title", title)
        obj.put("text", text)
        obj.put("postTime", postTime)
        return obj.toString()
    }
}
