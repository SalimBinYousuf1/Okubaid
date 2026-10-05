package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.util.CrashProtector
import java.util.UUID

class HostPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pairingId: String
        get() {
            var id = prefs.getString(KEY_PAIRING_ID, null)
            if (id.isNullOrBlank()) {
                val shortUuid = UUID.randomUUID().toString().substring(0, 8).uppercase()
                id = "UBAID-$shortUuid"
                prefs.edit().putString(KEY_PAIRING_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_PAIRING_ID, value).apply()

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var hostName: String
        get() = prefs.getString(KEY_HOST_NAME, android.os.Build.MODEL) ?: android.os.Build.MODEL
        set(value) = prefs.edit().putString(KEY_HOST_NAME, value).apply()

    companion object {
        private const val PREFS_NAME = "ubaid_host_preferences"
        private const val KEY_PAIRING_ID = "pairing_id"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_HOST_NAME = "host_name"

        @Volatile
        private var instance: HostPreferences? = null

        fun getInstance(context: Context): HostPreferences {
            return instance ?: synchronized(this) {
                instance ?: HostPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
