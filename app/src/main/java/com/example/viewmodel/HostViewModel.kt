package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirestoreSignalingManager
import com.example.data.HostPreferences
import com.example.model.BatteryInfo
import com.example.model.ConnectionState
import com.example.model.FirebaseConfigStatus
import com.example.model.NetworkInfo
import com.example.model.PairingPayload
import com.example.model.PermissionsState
import com.example.model.ScreenInfo
import com.example.service.HostStreamingService
import com.example.service.MediaProjectionHolder
import com.example.service.WatchdogWorker
import com.example.util.CrashProtector
import com.example.util.PermissionHelper
import com.example.util.SystemTelemetryHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HostViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "HostViewModel"
    private val prefs = HostPreferences.getInstance(application)
    private val signalingManager = FirestoreSignalingManager(application)

    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(0)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private val _isSetupCompleted = MutableStateFlow(prefs.isSetupCompleted)
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    val isBroadcasting: StateFlow<Boolean> = HostStreamingService.isServiceRunning
    val connectionState: StateFlow<ConnectionState> = signalingManager.connectionState
    val firebaseStatus: StateFlow<FirebaseConfigStatus> = signalingManager.firebaseStatus

    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()

    private val _screenInfo = MutableStateFlow(ScreenInfo())
    val screenInfo: StateFlow<ScreenInfo> = _screenInfo.asStateFlow()

    private val _pairingPayload = MutableStateFlow<PairingPayload?>(null)
    val pairingPayload: StateFlow<PairingPayload?> = _pairingPayload.asStateFlow()

    private val _showPairingDialog = MutableStateFlow(false)
    val showPairingDialog: StateFlow<Boolean> = _showPairingDialog.asStateFlow()

    private val _showDiagnosticsDialog = MutableStateFlow(false)
    val showDiagnosticsDialog: StateFlow<Boolean> = _showDiagnosticsDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshAll(application)
        startTelemetryMonitoring()
    }

    fun refreshAll(context: Context) {
        CrashProtector.safeRun(TAG, Unit) {
            val mediaProj = MediaProjectionHolder.hasValidConsent.value
            val access = PermissionHelper.isAccessibilityServiceEnabled(context)
            val notif = PermissionHelper.isNotificationListenerEnabled(context)
            val storage = PermissionHelper.isStorageAccessGranted(context)
            val batteryOpt = PermissionHelper.isBatteryOptimizationIgnored(context)

            _permissionsState.value = PermissionsState(
                mediaProjectionGranted = mediaProj,
                accessibilityGranted = access,
                notificationListenerGranted = notif,
                storageGranted = storage,
                batteryOptimizationIgnored = batteryOpt
            )

            _batteryInfo.value = SystemTelemetryHelper.getBatteryInfo(context)
            _networkInfo.value = SystemTelemetryHelper.getNetworkInfo(context)
            _screenInfo.value = SystemTelemetryHelper.getScreenInfo(context)

            val screen = _screenInfo.value
            _pairingPayload.value = PairingPayload(
                pairingId = prefs.pairingId,
                hostName = prefs.hostName,
                projectId = "salim-x-ubaid",
                apiKey = "AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w",
                storageBucket = "salim-x-ubaid.firebasestorage.app",
                screenWidth = screen.width,
                screenHeight = screen.height,
                densityDpi = screen.densityDpi
            )
        }
    }

    fun setWizardStep(step: Int) {
        _currentWizardStep.value = step.coerceIn(0, 5)
    }

    fun nextWizardStep() {
        _currentWizardStep.value = (_currentWizardStep.value + 1).coerceAtMost(5)
    }

    fun previousWizardStep() {
        _currentWizardStep.value = (_currentWizardStep.value - 1).coerceAtLeast(0)
    }

    fun setMediaProjectionConsent(code: Int, data: Intent?, context: Context) {
        CrashProtector.safeRun(TAG, Unit) {
            MediaProjectionHolder.setConsent(code, data)
            refreshAll(context)
        }
    }

    fun completeSetup(context: Context) {
        CrashProtector.safeRun(TAG, Unit) {
            prefs.isSetupCompleted = true
            _isSetupCompleted.value = true
            WatchdogWorker.schedulePeriodicWatchdog(context)
            startBroadcast(context)
        }
    }

    fun resetSetup() {
        prefs.isSetupCompleted = false
        _isSetupCompleted.value = false
        _currentWizardStep.value = 0
    }

    fun startBroadcast(context: Context) {
        CrashProtector.safeRun(TAG, Unit) {
            val intent = Intent(context, HostStreamingService::class.java).apply {
                action = HostStreamingService.ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
                signalingManager.startSignalingMailbox(prefs.pairingId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start broadcast service: ${e.message}", e)
                _errorMessage.value = "Failed to start background stream: ${e.message}"
            }
        }
    }

    fun stopBroadcast(context: Context) {
        CrashProtector.safeRun(TAG, Unit) {
            val intent = Intent(context, HostStreamingService::class.java).apply {
                action = HostStreamingService.ACTION_STOP
            }
            try {
                context.startService(intent)
                signalingManager.stopSignaling()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop broadcast service: ${e.message}", e)
            }
        }
    }

    fun setPairingDialogVisible(visible: Boolean) {
        _showPairingDialog.value = visible
    }

    fun setDiagnosticsDialogVisible(visible: Boolean) {
        _showDiagnosticsDialog.value = visible
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun startTelemetryMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                val app = getApplication<Application>()
                _batteryInfo.value = SystemTelemetryHelper.getBatteryInfo(app)
                _networkInfo.value = SystemTelemetryHelper.getNetworkInfo(app)
                delay(4000L)
            }
        }
    }
}
