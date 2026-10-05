package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.FirestoreSignalingManager
import com.example.data.HostPreferences
import com.example.model.ConnectionState
import com.example.model.ControlCommand
import com.example.model.PairingPayload
import com.example.util.CrashProtector
import com.example.util.FileChannelHandler
import com.example.util.SystemTelemetryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class HostStreamingService : Service() {
    private val TAG = "HostStreamingService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var signalingManager: FirestoreSignalingManager? = null
    private var preferences: HostPreferences? = null

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // 4 Distinct DataChannels
    private var controlChannel: DataChannel? = null
    private var fileChannel: DataChannel? = null
    private var notificationChannel: DataChannel? = null
    private var statusChannel: DataChannel? = null

    private var telemetryJob: Job? = null
    private var notificationForwardingJob: Job? = null

    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HostStreamingService onCreate")
        preferences = HostPreferences.getInstance(this)
        signalingManager = FirestoreSignalingManager(this)

        createNotificationChannel()
        startForegroundWithNotification("Initializing Ubaid Host...", "Preparing secure stream")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _isServiceRunning.value = true
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification("Ubaid Host Active", "Waiting for remote controller")

        serviceScope.launch {
            startStreamingPipeline()
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification(title: String, text: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (MediaProjectionHolder.hasValidConsent.value) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
            }
            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startForeground with type $serviceType: ${e.message}", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback startForeground failed: ${e2.message}", e2)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startStreamingPipeline() {
        CrashProtector.safeRun(TAG, Unit) {
            val prefs = preferences ?: return@safeRun
            val signaling = signalingManager ?: return@safeRun
            val pairingId = prefs.pairingId

            val screenInfo = SystemTelemetryHelper.getScreenInfo(this)
            val payload = PairingPayload(
                pairingId = pairingId,
                hostName = prefs.hostName,
                projectId = "salim-x-ubaid",
                apiKey = "AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w",
                storageBucket = "salim-x-ubaid.firebasestorage.app",
                screenWidth = screenInfo.width,
                screenHeight = screenInfo.height,
                densityDpi = screenInfo.densityDpi
            )
            signaling.publishHostProfile(payload)

            // Setup WebRTC Native Stack if not yet initialized
            if (peerConnectionFactory == null) {
                initWebRtc()
            }

            // Setup Screen Capturer if MediaProjection consent is available and capturer not yet created
            if (screenCapturer == null) {
                setupScreenCapture(screenInfo.width, screenInfo.height)
            }

            // Start listening to signaling offers from Salim
            signaling.onRemoteOfferReceived = { sdpOffer ->
                handleRemoteOffer(sdpOffer)
            }

            signaling.onRemoteIceCandidateReceived = { sdpMid, sdpMLineIndex, candidate ->
                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                synchronized(pendingRemoteCandidates) {
                    if (isRemoteDescriptionSet && peerConnection != null) {
                        peerConnection?.addIceCandidate(iceCandidate)
                    } else {
                        pendingRemoteCandidates.add(iceCandidate)
                        Log.d(TAG, "Queued early remote ICE candidate ($sdpMid)")
                    }
                }
            }

            signaling.startSignalingMailbox(pairingId)

            // Start Telemetry and Notification Forwarding
            startStatusLoop()
            startNotificationForwarding()
        }
    }

    private fun initWebRtc() {
        CrashProtector.safeRun(TAG, Unit) {
            eglBase = try {
                EglBase.create(null, EglBase.CONFIG_RECORDABLE)
            } catch (e: Exception) {
                Log.w(TAG, "CONFIG_RECORDABLE fallback: ${e.message}")
                EglBase.create()
            }
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )

            val encoderFactory = DefaultVideoEncoderFactory(
                eglBase?.eglBaseContext,
                true,
                true
            )
            val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            createPeerConnection()
        }
    }

    private fun createPeerConnection() {
        CrashProtector.safeRun(TAG, Unit) {
            val factory = peerConnectionFactory ?: return@safeRun
            synchronized(pendingRemoteCandidates) {
                pendingRemoteCandidates.clear()
                isRemoteDescriptionSet = false
            }

            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.services.mozilla.com:3478").createIceServer()
            )

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                iceTransportsType = PeerConnection.IceTransportsType.ALL
            }

            peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "SignalingState: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "IceConnectionState: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            signalingManager?.updateConnectionState(ConnectionState.CONNECTED)
                            startForegroundWithNotification("Ubaid Connected", "Streaming screen to Salim")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            signalingManager?.updateConnectionState(ConnectionState.RECONNECTING)
                            startForegroundWithNotification("Ubaid Reconnecting", "Attempting ICE restart")
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            signalingManager?.updateConnectionState(ConnectionState.RECONNECTING)
                            handleConnectionFailure()
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            signalingManager?.updateConnectionState(ConnectionState.STANDBY)
                        }
                        else -> {}
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "IceGatheringState: $state")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        preferences?.let { prefs ->
                            signalingManager?.sendIceCandidate(
                                prefs.pairingId,
                                candidate.sdpMid,
                                candidate.sdpMLineIndex,
                                candidate.sdp
                            )
                        }
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: org.webrtc.MediaStream?) {}
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {
                    dataChannel?.let { bindDataChannel(it) }
                }

                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "WebRTC Renegotiation Needed")
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
            })

            // Re-attach video track if already initialized
            videoTrack?.let { track ->
                peerConnection?.addTrack(track, listOf("host_stream"))
                Log.d(TAG, "Attached existing videoTrack to fresh PeerConnection")
            }

            // Host pre-creates DataChannels so they are ready as soon as connected
            setupDataChannels()
        }
    }

    private fun setupDataChannels() {
        val pc = peerConnection ?: return
        val init = DataChannel.Init().apply {
            ordered = true
        }

        controlChannel = pc.createDataChannel("control", init).also { bindDataChannel(it) }
        fileChannel = pc.createDataChannel("file", init).also { bindDataChannel(it) }
        notificationChannel = pc.createDataChannel("notifications", init).also { bindDataChannel(it) }
        statusChannel = pc.createDataChannel("status", init).also { bindDataChannel(it) }
    }

    private fun bindDataChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel '${dc.label()}' state changed: ${dc.state()}")
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) return
                CrashProtector.safeRun(TAG, Unit) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val message = String(bytes, StandardCharsets.UTF_8)

                    when (dc.label()) {
                        "control" -> handleControlMessage(message)
                        "file" -> {
                            FileChannelHandler.handleFileCommand(message) { response ->
                                sendDataChannelMessage(fileChannel, response)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun handleControlMessage(message: String) {
        val command = ControlCommand.fromJson(message) ?: return
        val screenInfo = SystemTelemetryHelper.getScreenInfo(this)
        UbaidAccessibilityService.instance?.dispatchCommand(
            command,
            screenInfo.width,
            screenInfo.height
        )
    }

    private fun sendDataChannelMessage(channel: DataChannel?, text: String) {
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            channel.send(buffer)
        }
    }

    private fun setupScreenCapture(width: Int, height: Int) {
        CrashProtector.safeRun(TAG, Unit) {
            // Guard: If screen capturer is already initialized and active, do not recreate
            if (screenCapturer != null) {
                Log.d(TAG, "Screen capturer is already active, reusing existing capturer.")
                return@safeRun
            }

            val (resultCode, resultData) = MediaProjectionHolder.consumeIntent()

            if (resultCode != 0 && resultData != null) {
                // Ensure foreground service is registered with mediaProjection type before obtaining token
                startForegroundWithNotification("Ubaid Host Active", "Streaming screen to Salim")

                try {
                    // Clean up any stale video pipeline objects before allocating new
                    videoTrack?.dispose()
                    videoTrack = null
                    videoSource?.dispose()
                    videoSource = null
                    surfaceTextureHelper?.dispose()
                    surfaceTextureHelper = null

                    // In Android 14+, ScreenCapturerAndroid consumes resultData to create the single MediaProjection instance.
                    // Never call getMediaProjection beforehand with the same resultData.
                    screenCapturer = ScreenCapturerAndroid(resultData, object : MediaProjection.Callback() {
                        override fun onStop() {
                            super.onStop()
                            Log.w(TAG, "MediaProjection stopped by system")
                            MediaProjectionHolder.markRevoked()
                            startForegroundWithNotification(
                                "Video Paused",
                                "Screen capture revoked by Android. Tap to resume."
                            )
                        }
                    })

                    surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase?.eglBaseContext)
                    videoSource = peerConnectionFactory?.createVideoSource(screenCapturer?.isScreencast == true)
                    screenCapturer?.initialize(surfaceTextureHelper, this, videoSource?.capturerObserver)

                    val scaledWidth = (((width / 2).coerceAtLeast(480)) / 2) * 2
                    val scaledHeight = (((height / 2).coerceAtLeast(800)) / 2) * 2
                    screenCapturer?.startCapture(scaledWidth, scaledHeight, 30)

                    videoTrack = peerConnectionFactory?.createVideoTrack("host_screen_video", videoSource)
                    videoTrack?.setEnabled(true)
                    peerConnection?.addTrack(videoTrack, listOf("host_stream"))
                    Log.d(TAG, "Screen capturer initialized and track added: ${scaledWidth}x$scaledHeight @ 30fps")
                } catch (e: Throwable) {
                    Log.e(TAG, "Screen capturer initialization error: ${e.message}", e)
                    MediaProjectionHolder.markRevoked()
                    try {
                        screenCapturer?.dispose()
                    } catch (_: Throwable) {}
                    screenCapturer = null
                }
            } else {
                Log.d(TAG, "No fresh MediaProjection consent token available; streaming data channels only.")
            }
        }
    }

    private fun handleRemoteOffer(sdpOffer: String) {
        val pc = peerConnection ?: return
        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, sdpOffer)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully. Draining queued candidates...")
                synchronized(pendingRemoteCandidates) {
                    isRemoteDescriptionSet = true
                    for (cand in pendingRemoteCandidates) {
                        pc.addIceCandidate(cand)
                    }
                    pendingRemoteCandidates.clear()
                }

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                }
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (answerDesc != null) {
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Local description set. Sending answer to Firestore...")
                                    preferences?.let { prefs ->
                                        signalingManager?.sendAnswer(prefs.pairingId, answerDesc.description)
                                    }
                                }
                                override fun onCreateFailure(err: String?) {
                                    Log.e(TAG, "Failed to set local description: $err")
                                }
                                override fun onSetFailure(err: String?) {
                                    Log.e(TAG, "Failed to set local description: $err")
                                }
                            }, answerDesc)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {
                        Log.e(TAG, "Failed to create answer: $err")
                    }
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Failed to set description: $err")
                    }
                }, constraints)
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote description: $err")
            }
        }, offerDesc)
    }

    private fun handleConnectionFailure() {
        serviceScope.launch {
            Log.w(TAG, "WebRTC connection failed. Backing off for 2s before resetting peer connection...")
            delay(2000L)
            if (isActive) {
                createPeerConnection()
                preferences?.let { prefs ->
                    signalingManager?.startSignalingMailbox(prefs.pairingId)
                }
            }
        }
    }

    private fun startStatusLoop() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            while (isActive) {
                CrashProtector.safeRun(TAG, Unit) {
                    val battery = SystemTelemetryHelper.getBatteryInfo(this@HostStreamingService)
                    val network = SystemTelemetryHelper.getNetworkInfo(this@HostStreamingService)
                    val screen = SystemTelemetryHelper.getScreenInfo(this@HostStreamingService)

                    val statusJson = JSONObject().apply {
                        put("type", "status_telemetry")
                        put("batteryPercent", battery.levelPercent)
                        put("isCharging", battery.isCharging)
                        put("networkType", network.type)
                        put("networkConnected", network.isConnected)
                        put("isScreenOn", screen.isScreenOn)
                        put("timestamp", System.currentTimeMillis())
                    }

                    sendDataChannelMessage(statusChannel, statusJson.toString())
                }
                delay(5000L)
            }
        }
    }

    private fun startNotificationForwarding() {
        notificationForwardingJob?.cancel()
        notificationForwardingJob = serviceScope.launch {
            UbaidNotificationListenerService.notificationEvents.collect { payload ->
                CrashProtector.safeRun(TAG, Unit) {
                    sendDataChannelMessage(notificationChannel, payload.toJson())
                }
            }
        }
    }

    private fun stopStreaming() {
        telemetryJob?.cancel()
        notificationForwardingJob?.cancel()

        CrashProtector.safeRun(TAG, Unit) {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null

            videoTrack?.dispose()
            videoTrack = null

            videoSource?.dispose()
            videoSource = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            controlChannel?.close()
            fileChannel?.close()
            notificationChannel?.close()
            statusChannel?.close()

            peerConnection?.close()
            peerConnection = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            eglBase?.release()
            eglBase = null

            signalingManager?.stopSignaling()
        }
        _isServiceRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        Log.d(TAG, "HostStreamingService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ubaid Host Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Ubaid connected for peer-to-peer screen mirroring and control."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.action.START_HOST"
        const val ACTION_STOP = "com.example.action.STOP_HOST"
        private const val CHANNEL_ID = "ubaid_host_service_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }
}
