package com.codecrafters.leaktrace.ui.calls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import com.codecrafters.leaktrace.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** On a device, catches the foreground-service deadline crash without making a call or recording. */
class CallCaptureStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun invalidStartPromotesOrStopsWithoutProcessCrash() {
        val context = compose.activity
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED)
        compose.runOnUiThread {
            ContextCompat.startForegroundService(context,
                Intent(context, CallCaptureService::class.java).setAction(CallCaptureService.START))
        }
        Thread.sleep(7_000) // Android's foreground-service deadline is shorter than this.
        assertTrue(CallCaptureBus.state.value is CallCaptureState.Error)
    }
}
