package com.example.model

import org.json.JSONObject

data class PairingPayload(
    val pairingId: String,
    val hostName: String,
    val projectId: String,
    val apiKey: String,
    val storageBucket: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val version: Int = 1
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("pairingId", pairingId)
        obj.put("hostName", hostName)
        obj.put("projectId", projectId)
        obj.put("apiKey", apiKey)
        obj.put("storageBucket", storageBucket)
        obj.put("screenWidth", screenWidth)
        obj.put("screenHeight", screenHeight)
        obj.put("densityDpi", densityDpi)
        obj.put("createdAt", createdAt)
        obj.put("version", version)
        return obj.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): PairingPayload? {
            return try {
                val obj = JSONObject(jsonStr)
                PairingPayload(
                    pairingId = obj.optString("pairingId", ""),
                    hostName = obj.optString("hostName", "Ubaid Host"),
                    projectId = obj.optString("projectId", ""),
                    apiKey = obj.optString("apiKey", ""),
                    storageBucket = obj.optString("storageBucket", ""),
                    screenWidth = obj.optInt("screenWidth", 1080),
                    screenHeight = obj.optInt("screenHeight", 2400),
                    densityDpi = obj.optInt("densityDpi", 420),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    version = obj.optInt("version", 1)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
