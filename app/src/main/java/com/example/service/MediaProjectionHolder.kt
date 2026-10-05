package com.example.service

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaProjectionHolder {
    var resultCode: Int = 0
        private set
    var resultData: Intent? = null
        private set

    private val _hasValidConsent = MutableStateFlow(false)
    val hasValidConsent: StateFlow<Boolean> = _hasValidConsent.asStateFlow()

    private val _consentRevoked = MutableStateFlow(false)
    val consentRevoked: StateFlow<Boolean> = _consentRevoked.asStateFlow()

    fun setConsent(code: Int, data: Intent?) {
        resultCode = code
        resultData = data
        _hasValidConsent.value = (code != 0 && data != null)
        if (_hasValidConsent.value) {
            _consentRevoked.value = false
        }
    }

    fun markRevoked() {
        resultCode = 0
        resultData = null
        _hasValidConsent.value = false
        _consentRevoked.value = true
    }

    fun clear() {
        resultCode = 0
        resultData = null
        _hasValidConsent.value = false
        _consentRevoked.value = false
    }
}
