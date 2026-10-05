package com.example.service

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaProjectionHolder {
    @Volatile
    var resultCode: Int = 0
        private set

    @Volatile
    var resultData: Intent? = null
        private set

    private val _hasValidConsent = MutableStateFlow(false)
    val hasValidConsent: StateFlow<Boolean> = _hasValidConsent.asStateFlow()

    private val _consentRevoked = MutableStateFlow(false)
    val consentRevoked: StateFlow<Boolean> = _consentRevoked.asStateFlow()

    @Synchronized
    fun setConsent(code: Int, data: Intent?) {
        resultCode = code
        resultData = data
        _hasValidConsent.value = (code != 0 && data != null)
        if (_hasValidConsent.value) {
            _consentRevoked.value = false
        }
    }

    /**
     * Atomically retrieves the single-use Intent data for MediaProjection creation.
     * Clears resultData so that the token is never re-used to avoid Android 14 SecurityException.
     * Keeps hasValidConsent true while streaming is active.
     */
    @Synchronized
    fun consumeIntent(): Pair<Int, Intent?> {
        val code = resultCode
        val data = resultData
        resultData = null // Token is single-use in Android 14+
        return Pair(code, data)
    }

    @Synchronized
    fun markRevoked() {
        resultCode = 0
        resultData = null
        _hasValidConsent.value = false
        _consentRevoked.value = true
    }

    @Synchronized
    fun clear() {
        resultCode = 0
        resultData = null
        _hasValidConsent.value = false
        _consentRevoked.value = false
    }
}
