package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.HostPreferences
import com.example.model.ConnectionState
import com.example.model.ControlCommand
import com.example.model.FileItem
import com.example.model.NotificationPayload
import com.example.model.PairingPayload
import com.example.model.PermissionsState
import com.example.util.CrashProtector
import com.example.util.FileChannelHandler
import com.example.util.PermissionHelper
import com.example.util.QRCodeGenerator
import com.example.util.SystemTelemetryHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies Ubaid app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ubaid", appName)
    }

    @Test
    fun `pairing payload json serialization roundtrip`() {
        val payload = PairingPayload(
            pairingId = "UBAID-TEST-1234",
            hostName = "Test Host Phone",
            projectId = "salim-x-ubaid",
            apiKey = "AIzaSyTestKey1234",
            storageBucket = "salim-x-ubaid.firebasestorage.app",
            screenWidth = 1080,
            screenHeight = 2400,
            densityDpi = 420
        )
        val json = payload.toJson()
        val restored = PairingPayload.fromJson(json)

        assertNotNull(restored)
        assertEquals(payload.pairingId, restored?.pairingId)
        assertEquals(payload.hostName, restored?.hostName)
        assertEquals(payload.projectId, restored?.projectId)
        assertEquals(payload.screenWidth, restored?.screenWidth)
        assertEquals(payload.screenHeight, restored?.screenHeight)
    }

    @Test
    fun `control command tap parser`() {
        val json = """{"type":"tap","x":0.45,"y":0.75}"""
        val cmd = ControlCommand.fromJson(json)
        assertTrue(cmd is ControlCommand.Tap)
        val tap = cmd as ControlCommand.Tap
        assertEquals(0.45f, tap.xPercent, 0.001f)
        assertEquals(0.75f, tap.yPercent, 0.001f)
    }

    @Test
    fun `control command swipe parser`() {
        val json = """{"type":"swipe","startX":0.5,"startY":0.8,"endX":0.5,"endY":0.2,"durationMs":250}"""
        val cmd = ControlCommand.fromJson(json)
        assertTrue(cmd is ControlCommand.Swipe)
        val swipe = cmd as ControlCommand.Swipe
        assertEquals(0.5f, swipe.startXPercent, 0.001f)
        assertEquals(0.8f, swipe.startYPercent, 0.001f)
        assertEquals(250L, swipe.durationMs)
    }

    @Test
    fun `control command global action parser`() {
        val json = """{"type":"global","action":"BACK"}"""
        val cmd = ControlCommand.fromJson(json)
        assertTrue(cmd is ControlCommand.GlobalAction)
        val global = cmd as ControlCommand.GlobalAction
        assertEquals("BACK", global.actionName)
    }

    @Test
    fun `control command text injection parser`() {
        val json = """{"type":"text","text":"Remote typing test"}"""
        val cmd = ControlCommand.fromJson(json)
        assertTrue(cmd is ControlCommand.TextInjection)
        val text = cmd as ControlCommand.TextInjection
        assertEquals("Remote typing test", text.text)
    }

    @Test
    fun `notification payload serialization`() {
        val notif = NotificationPayload(
            key = "msg_001",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Salim",
            text = "Hello Ubaid, connection active",
            postTime = 123456789L,
            isRemoved = false
        )
        val jsonStr = notif.toJson()
        assertTrue(jsonStr.contains("notification_posted"))
        assertTrue(jsonStr.contains("Messages"))
        assertTrue(jsonStr.contains("Salim"))
    }

    @Test
    fun `permissions state evaluation`() {
        val incomplete = PermissionsState(
            mediaProjectionGranted = true,
            accessibilityGranted = false,
            notificationListenerGranted = true,
            storageGranted = true,
            batteryOptimizationIgnored = false
        )
        assertFalse(incomplete.allRequiredGranted)
        assertEquals(3, incomplete.grantedCount)

        val complete = PermissionsState(
            mediaProjectionGranted = true,
            accessibilityGranted = true,
            notificationListenerGranted = true,
            storageGranted = true,
            batteryOptimizationIgnored = true
        )
        assertTrue(complete.allRequiredGranted)
        assertEquals(5, complete.grantedCount)
    }

    @Test
    fun `connection state labels`() {
        assertEquals("Connected & Streaming", ConnectionState.CONNECTED.displayLabel)
        assertEquals("Waiting for Salim", ConnectionState.WAITING_FOR_CONTROLLER.displayLabel)
        assertEquals("Reconnecting (ICE)", ConnectionState.RECONNECTING.displayLabel)
    }

    @Test
    fun `telemetry helper queries battery and network without throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val battery = SystemTelemetryHelper.getBatteryInfo(context)
        assertNotNull(battery)
        assertTrue(battery.levelPercent in 0..100)

        val network = SystemTelemetryHelper.getNetworkInfo(context)
        assertNotNull(network)

        val screen = SystemTelemetryHelper.getScreenInfo(context)
        assertNotNull(screen)
        assertTrue(screen.width > 0)
    }

    @Test
    fun `qr code generator succeeds without crashing`() {
        val bitmap = QRCodeGenerator.generateQRCodeBitmap("UBAID-TEST-PAIRING", 128, 128)
        assertNotNull(bitmap)
    }

    @Test
    fun `crash protector safeRun returns fallback on exception`() {
        val result = CrashProtector.safeRun("TestTag", fallback = "FALLBACK") {
            throw RuntimeException("Simulated error")
        }
        assertEquals("FALLBACK", result)
    }

    @Test
    fun `file channel handler handles list command`() {
        var responseReceived = ""
        FileChannelHandler.handleFileCommand("""{"action":"list","path":"/"}""") { resp ->
            responseReceived = resp
        }
        assertTrue(responseReceived.contains("file_list_response"))
    }

    @Test
    fun `file channel handler handles invalid file read gracefully`() {
        var responseReceived = ""
        FileChannelHandler.handleFileCommand("""{"action":"read","path":"/non_existent_folder/missing.txt"}""") { resp ->
            responseReceived = resp
        }
        assertTrue(responseReceived.contains("file_error"))
    }

    @Test
    fun `host preferences persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = HostPreferences.getInstance(context)
        val id1 = prefs.pairingId
        assertNotNull(id1)
        assertTrue(id1.startsWith("UBAID-"))

        val id2 = prefs.pairingId
        assertEquals(id1, id2) // verify persistent same ID
    }
}
