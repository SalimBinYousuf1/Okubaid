package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.ConnectionState
import com.example.model.FirebaseConfigStatus
import com.example.service.MediaProjectionHolder
import com.example.ui.theme.AccentContainer
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.GlassSubtleSurface
import com.example.ui.theme.StatusConnectedBg
import com.example.ui.theme.StatusConnectedBorder
import com.example.ui.theme.StatusConnectedDot
import com.example.ui.theme.StatusConnectedText
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusErrorBorder
import com.example.ui.theme.StatusErrorDot
import com.example.ui.theme.StatusErrorText
import com.example.ui.theme.StatusReconnectingBg
import com.example.ui.theme.StatusReconnectingBorder
import com.example.ui.theme.StatusReconnectingDot
import com.example.ui.theme.StatusReconnectingText
import com.example.ui.theme.StatusStandbyBg
import com.example.ui.theme.StatusStandbyBorder
import com.example.ui.theme.StatusStandbyDot
import com.example.ui.theme.StatusStandbyText
import com.example.ui.theme.StatusWaitingBg
import com.example.ui.theme.StatusWaitingBorder
import com.example.ui.theme.StatusWaitingDot
import com.example.ui.theme.StatusWaitingText
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.HostViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostMainScreen(
    viewModel: HostViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val connectionState by viewModel.connectionState.collectAsState()
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val permissions by viewModel.permissionsState.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val networkInfo by viewModel.networkInfo.collectAsState()
    val screenInfo by viewModel.screenInfo.collectAsState()
    val pairingPayload by viewModel.pairingPayload.collectAsState()
    val firebaseStatus by viewModel.firebaseStatus.collectAsState()
    val showPairingDialog by viewModel.showPairingDialog.collectAsState()
    val showDiagnosticsDialog by viewModel.showDiagnosticsDialog.collectAsState()
    val consentRevoked by MediaProjectionHolder.consentRevoked.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAll(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.setMediaProjectionConsent(result.resultCode, result.data, context)
            viewModel.startBroadcast(context)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("host_main_screen"),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenShare,
                                contentDescription = "Ubaid Logo",
                                tint = AccentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ubaid Host",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.4).sp,
                                color = TextPrimary
                            )
                            pairingPayload?.let {
                                Text(
                                    text = "ID: ${it.pairingId}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setDiagnosticsDialogVisible(true) },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("open_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "System Diagnostics",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Single-Tap Video Resume Banner
            AnimatedVisibility(
                visible = isBroadcasting && (consentRevoked || !permissions.mediaProjectionGranted),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LiquidGlassCard(
                    backgroundColor = StatusWaitingBg,
                    borderColor = StatusWaitingBorder,
                    modifier = Modifier.testTag("resume_video_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, StatusWaitingBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusWaitingDot,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Screen Mirroring Paused",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusWaitingText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Screen stream paused. Tap Resume to re-enable video streaming.",
                                fontSize = 12.sp,
                                color = StatusWaitingText,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                try {
                                    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                    mpManager?.let {
                                        mediaProjectionLauncher.launch(it.createScreenCaptureIntent())
                                    }
                                } catch (e: Exception) {
                                    viewModel.startBroadcast(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text("Resume", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // 2. Firebase Configuration Notice (If missing, clean non-crashing alert)
            if (firebaseStatus is FirebaseConfigStatus.Missing) {
                LiquidGlassCard(
                    backgroundColor = StatusErrorBg,
                    borderColor = StatusErrorBorder,
                    modifier = Modifier.testTag("firebase_warning_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = StatusErrorDot,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Firebase Signaling Mailbox Notice",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusErrorText
                            )
                            Text(
                                text = (firebaseStatus as FirebaseConfigStatus.Missing).reason,
                                fontSize = 12.sp,
                                color = StatusErrorText
                            )
                        }
                    }
                }
            }

            // 3. Apple-Grade Status Capsule (Dynamic Island Inspiration)
            AppleStatusCapsule(
                connectionState = if (isBroadcasting) connectionState else ConnectionState.STANDBY,
                isBroadcasting = isBroadcasting
            )

            // 4. Primary Pairing Action Card (Apple Blue Tactile Card)
            Button(
                onClick = { viewModel.setPairingDialogVisible(true) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("show_pairing_qr_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Display Pairing QR Code",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = Color.White
                )
            }

            // 5. Broadcast Control Card (Liquid Glass Surface)
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Broadcast Service",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBroadcasting) "Active in background & listening" else "Service is currently stopped",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        if (isBroadcasting) {
                            OutlinedButton(
                                onClick = { viewModel.stopBroadcast(context) },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusErrorBorder),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("stop_broadcast_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = StatusErrorDot,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Stop",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusErrorText
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (!permissions.mediaProjectionGranted) {
                                        try {
                                            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                            mpManager?.let {
                                                mediaProjectionLauncher.launch(it.createScreenCaptureIntent())
                                            } ?: viewModel.startBroadcast(context)
                                        } catch (e: Exception) {
                                            viewModel.startBroadcast(context)
                                        }
                                    } else {
                                        viewModel.startBroadcast(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("start_broadcast_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Start",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 6. Live Telemetry Capsule (Subtle Frosted Grid)
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().testTag("telemetry_card")) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "LIVE TELEMETRY (STREAMED OVER WebRTC)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppleTelemetryPill(
                            icon = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            label = "Battery",
                            value = "${batteryInfo.levelPercent}%${if (batteryInfo.isCharging) " ⚡" else ""}",
                            modifier = Modifier.weight(1f)
                        )

                        AppleTelemetryPill(
                            icon = Icons.Default.Wifi,
                            label = "Network",
                            value = networkInfo.type,
                            modifier = Modifier.weight(1f)
                        )

                        AppleTelemetryPill(
                            icon = Icons.Default.ScreenShare,
                            label = "Display",
                            value = "${screenInfo.width}x${screenInfo.height}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Accessibility Gestures", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (permissions.accessibilityGranted) "Listening & Active" else "Disabled in Settings",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (permissions.accessibilityGranted) StatusConnectedDot else StatusErrorDot
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Notification Feed", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (permissions.notificationListenerGranted) "Forwarding Live" else "Disabled in Settings",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (permissions.notificationListenerGranted) StatusConnectedDot else StatusErrorDot
                            )
                        }
                    }
                }
            }

            // 7. Secondary Action: Run Setup Wizard Again (Settings Row Style)
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.resetSetup() }
                    .testTag("run_setup_again_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Run Setup Wizard Again",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Reconfigure permissions or verify checklist",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showPairingDialog && pairingPayload != null) {
        PairingQRDialog(
            payload = pairingPayload!!,
            onDismiss = { viewModel.setPairingDialogVisible(false) }
        )
    }

    if (showDiagnosticsDialog) {
        DiagnosticsDialog(
            permissionsState = permissions,
            firebaseStatus = firebaseStatus,
            isBroadcasting = isBroadcasting,
            onRefresh = { viewModel.refreshAll(context) },
            onDismiss = { viewModel.setDiagnosticsDialogVisible(false) }
        )
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = GlassCardSurface,
    borderColor: Color = GlassCardBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0x10000000),
                ambientColor = Color(0x08000000)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun AppleStatusCapsule(
    connectionState: ConnectionState,
    isBroadcasting: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (bgColor, borderColor, textColor, dotColor, label, subtext) = when {
        !isBroadcasting -> {
            Tuple6(
                StatusStandbyBg,
                StatusStandbyBorder,
                StatusStandbyText,
                StatusStandbyDot,
                "STANDBY (IDLE)",
                "Host broadcast is paused. Tap Start Broadcast to accept controller connection."
            )
        }
        connectionState == ConnectionState.CONNECTED -> {
            Tuple6(
                StatusConnectedBg,
                StatusConnectedBorder,
                StatusConnectedText,
                StatusConnectedDot,
                "CONNECTED & STREAMING",
                "Direct peer-to-peer WebRTC connection established with Salim. Video & Control active."
            )
        }
        connectionState == ConnectionState.RECONNECTING -> {
            Tuple6(
                StatusReconnectingBg,
                StatusReconnectingBorder,
                StatusReconnectingText,
                StatusReconnectingDot,
                "RECONNECTING (ICE)",
                "Connection interrupted. Running automated ICE restarts and renegotiation backoff."
            )
        }
        connectionState == ConnectionState.WAITING_FOR_CONTROLLER -> {
            Tuple6(
                StatusWaitingBg,
                StatusWaitingBorder,
                StatusWaitingText,
                StatusWaitingDot,
                "WAITING FOR SALIM",
                "Signaling mailbox active on Firestore. Ready for connection from anywhere."
            )
        }
        else -> {
            Tuple6(
                StatusErrorBg,
                StatusErrorBorder,
                StatusErrorText,
                StatusErrorDot,
                "ACTION REQUIRED",
                "Check device permissions or signaling credentials in Diagnostics."
            )
        }
    }

    LiquidGlassCard(
        backgroundColor = bgColor,
        borderColor = borderColor,
        modifier = Modifier.fillMaxWidth().testTag("connection_status_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Glowing Apple-style Pulsing Status Dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isBroadcasting && (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.WAITING_FOR_CONTROLLER)) {
                                    dotColor.copy(alpha = pulseAlpha)
                                } else {
                                    dotColor
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        color = textColor
                    )
                }

                // Apple Status Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .border(0.5.dp, borderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isBroadcasting) "LIVE" else "PAUSED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = dotColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtext,
                fontSize = 13.sp,
                color = textColor,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AppleTelemetryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextTertiary,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

private data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)
