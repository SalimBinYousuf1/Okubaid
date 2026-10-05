package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.ConnectionState
import com.example.model.FirebaseConfigStatus
import com.example.model.PairingPayload
import com.example.util.CrashProtector
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class FirestoreSignalingManager(private val context: Context) {
    private val TAG = "FirestoreSignaling"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.STANDBY)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _firebaseStatus = MutableStateFlow<FirebaseConfigStatus>(
        FirebaseConfigStatus.Missing("Not initialized")
    )
    val firebaseStatus: StateFlow<FirebaseConfigStatus> = _firebaseStatus.asStateFlow()

    private var firestore: FirebaseFirestore? = null
    private var sessionListener: ListenerRegistration? = null
    private var offerListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null

    // Callback invoked when a remote offer arrives from Salim
    var onRemoteOfferReceived: ((sdp: String) -> Unit)? = null
    var onRemoteIceCandidateReceived: ((sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit)? = null

    init {
        initFirebase()
    }

    fun initFirebase(): FirebaseConfigStatus {
        return CrashProtector.safeRun(TAG, FirebaseConfigStatus.Missing("Unknown initialization error")) {
            try {
                val app = if (FirebaseApp.getApps(context).isEmpty()) {
                    // Try to initialize using default options
                    try {
                        FirebaseApp.initializeApp(context)
                    } catch (e: Exception) {
                        // If default options failed, initialize using provided project credentials
                        val options = FirebaseOptions.Builder()
                            .setApplicationId("1:428293373821:android:703a5f2f83dbcfe6629614")
                            .setApiKey("AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w")
                            .setProjectId("salim-x-ubaid")
                            .setStorageBucket("salim-x-ubaid.firebasestorage.app")
                            .build()
                        FirebaseApp.initializeApp(context, options)
                    }
                } else {
                    FirebaseApp.getInstance()
                }

                if (app != null) {
                    firestore = FirebaseFirestore.getInstance(app)
                    val status = FirebaseConfigStatus.Configured(app.options.projectId ?: "salim-x-ubaid")
                    _firebaseStatus.value = status
                    status
                } else {
                    val status = FirebaseConfigStatus.Missing("FirebaseApp is null")
                    _firebaseStatus.value = status
                    status
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase initialization failed: ${e.message}", e)
                val status = FirebaseConfigStatus.Missing(e.message ?: "Configuration error")
                _firebaseStatus.value = status
                status
            }
        }
    }

    /**
     * Publishes this Host device profile to the 'host_profiles' collection
     * and sets up initial document for 'project_tasks' for diagnostics and telemetry.
     */
    fun publishHostProfile(payload: PairingPayload) {
        val db = firestore ?: return
        scope.launch {
            CrashProtector.safeRun(TAG, Unit) {
                val profileMap = hashMapOf(
                    "pairingId" to payload.pairingId,
                    "hostName" to payload.hostName,
                    "screenWidth" to payload.screenWidth,
                    "screenHeight" to payload.screenHeight,
                    "densityDpi" to payload.densityDpi,
                    "version" to payload.version,
                    "status" to "online",
                    "capabilities" to listOf(
                        "screen_mirroring",
                        "accessibility_control",
                        "file_browser",
                        "notifications",
                        "telemetry"
                    ),
                    "lastSeen" to FieldValue.serverTimestamp()
                )
                db.collection("host_profiles").document(payload.pairingId)
                    .set(profileMap, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Host profile synced with Firestore: ${payload.pairingId}")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to sync host profile: ${e.message}")
                    }

                // Initialize project tasks tracking collection
                val taskDoc = db.collection("project_tasks").document(payload.pairingId)
                taskDoc.set(
                    hashMapOf(
                        "hostId" to payload.pairingId,
                        "taskName" to "Host Standby Telemetry",
                        "status" to "READY",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
        }
    }

    /**
     * Starts listening for signaling offers from the controller (Salim) for the given pairingId.
     */
    fun startSignalingMailbox(pairingId: String) {
        val db = firestore ?: run {
            _connectionState.value = ConnectionState.WAITING_FOR_CONTROLLER
            return
        }

        _connectionState.value = ConnectionState.WAITING_FOR_CONTROLLER

        CrashProtector.safeRun(TAG, Unit) {
            val sessionDoc = db.collection("sessions").document(pairingId)

            // Reset or create session document with host waiting status
            sessionDoc.set(
                hashMapOf(
                    "hostStatus" to "WAITING",
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            // Listen for remote offer from Salim
            offerListener?.remove()
            offerListener = sessionDoc.collection("signaling").document("offer")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Offer listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val sdp = snapshot.getString("sdp")
                        val type = snapshot.getString("type")
                        if (!sdp.isNullOrBlank() && type == "offer") {
                            Log.d(TAG, "Received WebRTC offer from Salim!")
                            _connectionState.value = ConnectionState.CONNECTING
                            onRemoteOfferReceived?.invoke(sdp)
                        }
                    }
                }

            // Listen for remote ICE candidates from Salim
            candidateListener?.remove()
            candidateListener = sessionDoc.collection("controller_candidates")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    for (docChange in snapshots.documentChanges) {
                        val data = docChange.document.data
                        val sdpMid = data["sdpMid"] as? String ?: ""
                        val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                        val candidate = data["candidate"] as? String ?: ""
                        if (candidate.isNotBlank()) {
                            onRemoteIceCandidateReceived?.invoke(sdpMid, sdpMLineIndex, candidate)
                        }
                    }
                }
        }
    }

    /**
     * Sends the WebRTC answer back to Salim via the Firestore signaling mailbox.
     */
    fun sendAnswer(pairingId: String, sdpAnswer: String) {
        val db = firestore ?: return
        CrashProtector.safeRun(TAG, Unit) {
            val answerMap = hashMapOf(
                "type" to "answer",
                "sdp" to sdpAnswer,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("sessions").document(pairingId)
                .collection("signaling").document("answer")
                .set(answerMap)
                .addOnSuccessListener {
                    Log.d(TAG, "WebRTC answer published to Firestore")
                }
        }
    }

    /**
     * Publishes a local host ICE candidate to Firestore for Salim to discover.
     */
    fun sendIceCandidate(pairingId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val db = firestore ?: return
        CrashProtector.safeRun(TAG, Unit) {
            val candidateMap = hashMapOf(
                "sdpMid" to sdpMid,
                "sdpMLineIndex" to sdpMLineIndex,
                "candidate" to candidate,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("sessions").document(pairingId)
                .collection("host_candidates")
                .add(candidateMap)
        }
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun stopSignaling() {
        offerListener?.remove()
        offerListener = null
        candidateListener?.remove()
        candidateListener = null
        sessionListener?.remove()
        sessionListener = null
        _connectionState.value = ConnectionState.STANDBY
    }
}
