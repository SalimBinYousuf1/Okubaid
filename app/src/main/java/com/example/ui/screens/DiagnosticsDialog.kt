package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FirebaseConfigStatus
import com.example.model.PermissionsState
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.BorderSubtle
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

@Composable
fun DiagnosticsDialog(
    permissionsState: PermissionsState,
    firebaseStatus: FirebaseConfigStatus,
    isBroadcasting: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 440.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = Color(0x18000000)
                )
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                .testTag("diagnostics_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DIAGNOSTICS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPrimary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "System Subsystems",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .testTag("close_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Diagnostics",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppleDiagnosticItem(
                        title = "Screen Mirroring (Consent)",
                        status = if (permissionsState.mediaProjectionGranted) "Active" else "Revoked / Not Granted",
                        isOk = permissionsState.mediaProjectionGranted,
                        onFix = null
                    )

                    AppleDiagnosticItem(
                        title = "Accessibility Gesture Service",
                        status = if (permissionsState.accessibilityGranted) "Enabled" else "Disabled in Settings",
                        isOk = permissionsState.accessibilityGranted,
                        onFix = {
                            PermissionHelper.launchIntentSafely(
                                context,
                                PermissionHelper.createAccessibilitySettingsIntent(context)
                            )
                        }
                    )

                    AppleDiagnosticItem(
                        title = "Notification Listener",
                        status = if (permissionsState.notificationListenerGranted) "Active" else "Not Authorized",
                        isOk = permissionsState.notificationListenerGranted,
                        onFix = {
                            PermissionHelper.launchIntentSafely(
                                context,
                                PermissionHelper.createNotificationListenerSettingsIntent(context)
                            )
                        }
                    )

                    AppleDiagnosticItem(
                        title = "All Files Access",
                        status = if (permissionsState.storageGranted) "Full Access" else "Restricted",
                        isOk = permissionsState.storageGranted,
                        onFix = {
                            PermissionHelper.launchIntentSafely(
                                context,
                                PermissionHelper.createStorageSettingsIntent(context)
                            )
                        }
                    )

                    AppleDiagnosticItem(
                        title = "Battery Optimization",
                        status = if (permissionsState.batteryOptimizationIgnored) "Exempt (Reliable)" else "Optimized (Risk of kill)",
                        isOk = permissionsState.batteryOptimizationIgnored,
                        onFix = {
                            PermissionHelper.launchIntentSafely(
                                context,
                                PermissionHelper.createBatteryOptimizationIntent(context)
                            )
                        }
                    )

                    AppleDiagnosticItem(
                        title = "Firebase Signaling Mailbox",
                        status = when (firebaseStatus) {
                            is FirebaseConfigStatus.Configured -> "Ready (${firebaseStatus.projectId})"
                            is FirebaseConfigStatus.Missing -> "Notice: ${firebaseStatus.reason}"
                        },
                        isOk = firebaseStatus is FirebaseConfigStatus.Configured,
                        onFix = null
                    )

                    AppleDiagnosticItem(
                        title = "Host Broadcast Service",
                        status = if (isBroadcasting) "Running (Foreground)" else "Stopped / Standby",
                        isOk = isBroadcasting,
                        onFix = null
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("refresh_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Re-check Subsystems", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleDiagnosticItem(
    title: String,
    status: String,
    isOk: Boolean,
    onFix: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isOk) StatusConnectedDot else StatusErrorDot)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = status,
                    fontSize = 12.sp,
                    color = if (isOk) StatusConnectedText else StatusErrorText,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (onFix != null && !isOk) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onFix,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(text = "Configure", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open Settings",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
