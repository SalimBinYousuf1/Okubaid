package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.PairingPayload
import com.example.model.PermissionsState
import com.example.ui.theme.AccentContainer
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.GlassCardBorder
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.StatusConnectedBg
import com.example.ui.theme.StatusConnectedBorder
import com.example.ui.theme.StatusConnectedDot
import com.example.ui.theme.StatusConnectedText
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusErrorBorder
import com.example.ui.theme.StatusErrorDot
import com.example.ui.theme.StatusErrorText
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.PermissionHelper
import com.example.util.QRCodeGenerator
import com.example.viewmodel.HostViewModel

@Composable
fun SetupWizardScreen(
    viewModel: HostViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentStep by viewModel.currentWizardStep.collectAsState()
    val permissions by viewModel.permissionsState.collectAsState()
    val pairingPayload by viewModel.pairingPayload.collectAsState()

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
            if (currentStep == 5) {
                viewModel.completeSetup(context)
            }
        } else {
            viewModel.setMediaProjectionConsent(0, null, context)
            if (currentStep == 5) {
                viewModel.completeSetup(context)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .testTag("setup_wizard_screen"),
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HOST PROVISIONING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Step ${currentStep + 1} of 6",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (permissions.grantedCount == 5) StatusConnectedBg else Color(0xFFF1F5F9))
                        .border(
                            1.dp,
                            if (permissions.grantedCount == 5) StatusConnectedBorder else BorderSubtle,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${permissions.grantedCount} / 5 Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (permissions.grantedCount == 5) StatusConnectedText else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (currentStep + 1) / 6f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentPrimary,
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step Content Box with smooth cross-fade animation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "wizard_step_transition"
                ) { step ->
                    when (step) {
                        0 -> AccessibilityStep(
                            isGranted = permissions.accessibilityGranted,
                            onOpenSettings = {
                                PermissionHelper.launchIntentSafely(
                                    context,
                                    PermissionHelper.createAccessibilitySettingsIntent(context)
                                )
                            },
                            onVerify = { viewModel.refreshAll(context) }
                        )
                        1 -> NotificationListenerStep(
                            isGranted = permissions.notificationListenerGranted,
                            onOpenSettings = {
                                PermissionHelper.launchIntentSafely(
                                    context,
                                    PermissionHelper.createNotificationListenerSettingsIntent(context)
                                )
                            },
                            onVerify = { viewModel.refreshAll(context) }
                        )
                        2 -> StorageStep(
                            isGranted = permissions.storageGranted,
                            onOpenSettings = {
                                PermissionHelper.launchIntentSafely(
                                    context,
                                    PermissionHelper.createStorageSettingsIntent(context)
                                )
                            },
                            onVerify = { viewModel.refreshAll(context) }
                        )
                        3 -> BatteryOptimizationStep(
                            isGranted = permissions.batteryOptimizationIgnored,
                            onOpenSettings = {
                                PermissionHelper.launchIntentSafely(
                                    context,
                                    PermissionHelper.createBatteryOptimizationIntent(context)
                                )
                            },
                            onVerify = { viewModel.refreshAll(context) }
                        )
                        4 -> PairingStep(
                            payload = pairingPayload,
                            onFinish = { viewModel.nextWizardStep() }
                        )
                        5 -> MediaProjectionStep(
                            isGranted = permissions.mediaProjectionGranted,
                            onGrantClick = {
                                try {
                                    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                    mpManager?.let {
                                        mediaProjectionLauncher.launch(it.createScreenCaptureIntent())
                                    }
                                } catch (e: Exception) {
                                    viewModel.completeSetup(context)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { viewModel.previousWizardStep() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("wizard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Back", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(50.dp))
                }

                if (currentStep < 5) {
                    Button(
                        onClick = { viewModel.nextWizardStep() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("wizard_next_button")
                    ) {
                        Text(text = "Next", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (permissions.mediaProjectionGranted) {
                                viewModel.completeSetup(context)
                            } else {
                                try {
                                    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                    mpManager?.let {
                                        mediaProjectionLauncher.launch(it.createScreenCaptureIntent())
                                    } ?: viewModel.completeSetup(context)
                                } catch (e: Exception) {
                                    viewModel.completeSetup(context)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("wizard_finish_button")
                    ) {
                        Text(
                            text = "Finish & Start Stream",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepContainer(
    icon: ImageVector,
    title: String,
    badgeText: String,
    isGranted: Boolean,
    description: String,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    LiquidGlassCard(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = AccentPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isGranted) StatusConnectedBg else StatusErrorBg)
                            .border(
                                1.dp,
                                if (isGranted) StatusConnectedBorder else StatusErrorBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isGranted) StatusConnectedDot else StatusErrorDot)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGranted) StatusConnectedText else StatusErrorText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = title,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            content()
        }
    }
}

@Composable
private fun MediaProjectionStep(
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    StepContainer(
        icon = Icons.Default.ScreenShare,
        title = "One-Time Screen Share Setup",
        badgeText = if (isGranted) "Screen Share Ready" else "Ready to Activate",
        isGranted = isGranted,
        description = "Authorize screen mirroring once to complete setup and start broadcasting live to Salim. After this one-time setup, Ubaid maintains persistent background readiness without asking again."
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grant_media_projection_button")
                ) {
                    Text(
                        text = "Grant Screen Capture Consent",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusConnectedBg)
                        .border(1.dp, StatusConnectedBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = StatusConnectedDot,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Screen capture token active. Video track ready for WebRTC stream.",
                        fontSize = 13.sp,
                        color = StatusConnectedText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibilityStep(
    isGranted: Boolean,
    onOpenSettings: () -> Unit,
    onVerify: () -> Unit
) {
    StepContainer(
        icon = Icons.Default.TouchApp,
        title = "Remote Gestures & Input",
        badgeText = if (isGranted) "Service Active" else "Setup Needed",
        isGranted = isGranted,
        description = "Allows Salim to simulate precise taps, swipes, and system buttons (Back, Home, Recents) on this device."
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(text = "1. Tap 'Open Accessibility Settings' below.", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "2. Open 'Installed / Downloaded apps'.", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "3. Tap 'Ubaid Remote Service' and switch toggle ON.", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_accessibility_settings_button")
            ) {
                Text(text = "Open Accessibility Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }

            OutlinedButton(
                onClick = onVerify,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("verify_accessibility_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Verify Status", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NotificationListenerStep(
    isGranted: Boolean,
    onOpenSettings: () -> Unit,
    onVerify: () -> Unit
) {
    StepContainer(
        icon = Icons.Default.Notifications,
        title = "Live Notification Feed",
        badgeText = if (isGranted) "Feed Active" else "Authorization Needed",
        isGranted = isGranted,
        description = "Streams incoming notifications live to your controller device over the WebRTC data channel."
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_notifications_settings_button")
            ) {
                Text(text = "Authorize Notification Access", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }

            OutlinedButton(
                onClick = onVerify,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Verify Status", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StorageStep(
    isGranted: Boolean,
    onOpenSettings: () -> Unit,
    onVerify: () -> Unit
) {
    StepContainer(
        icon = Icons.Default.Folder,
        title = "All Files Access",
        badgeText = if (isGranted) "Full Access" else "Permission Required",
        isGranted = isGranted,
        description = "Allows Salim to browse your directories and transfer files via chunked data channels."
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_storage_settings_button")
            ) {
                Text(text = "Grant All-Files Access", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }

            OutlinedButton(
                onClick = onVerify,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Verify Status", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BatteryOptimizationStep(
    isGranted: Boolean,
    onOpenSettings: () -> Unit,
    onVerify: () -> Unit
) {
    StepContainer(
        icon = Icons.Default.BatterySaver,
        title = "Background Reliability",
        badgeText = if (isGranted) "Exempt" else "Action Needed",
        isGranted = isGranted,
        description = "Exempts Ubaid from Android's background task killers so your host phone stays online when unattended."
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("request_battery_opt_button")
            ) {
                Text(text = "Request Exemption", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }

            OutlinedButton(
                onClick = onVerify,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Verify Status", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PairingStep(
    payload: PairingPayload?,
    onFinish: () -> Unit
) {
    val jsonStr = remember(payload) { payload?.toJson() ?: "" }
    val qrBitmap = remember(jsonStr) {
        if (jsonStr.isNotBlank()) QRCodeGenerator.generateQRCodeBitmap(jsonStr, 360, 360) else null
    }

    StepContainer(
        icon = Icons.Default.QrCode,
        title = "Pairing with Salim",
        badgeText = "One-Time Setup",
        isGranted = true,
        description = "Scan this QR code with Salim Controller once. After initial pairing, reconnection happens automatically whenever Salim connects."
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, BorderStrong, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "Setup QR Code",
                        modifier = Modifier.size(164.dp)
                    )
                } else {
                    Text(text = "Generating...", fontSize = 12.sp, color = TextSecondary)
                }
            }

            payload?.let {
                Text(
                    text = it.pairingId,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
        }
    }
}
