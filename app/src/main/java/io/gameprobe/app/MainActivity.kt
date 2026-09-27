package io.gameprobe.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.gameprobe.app.controller.ControllerProbe
import io.gameprobe.app.ui.ControllerProbeScreen
import io.gameprobe.app.ui.GameProbeTheme

class MainActivity : ComponentActivity() {

    private lateinit var probe: ControllerProbe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probe = ControllerProbe(applicationContext)
        enableEdgeToEdge()
        setContent {
            GameProbeTheme {
                ControllerProbeScreen(probe)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        probe.start()
    }

    override fun onStop() {
        probe.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) probe.releaseAllInputs()
    }

    /*
     * Controller input is captured at the Activity, before the view hierarchy. This does
     * not depend on which Compose node has focus, and it stops controller keys from being
     * turned into focus navigation or fallback keys (for example BUTTON_B -> BACK).
     * Events from non-controller devices (touchscreen, phone navigation) pass through.
     */

    // androidx.core marks ComponentActivity.dispatchKeyEvent @RestrictTo because it routes
    // through KeyEventDispatcher internally. Overriding it and delegating to super keeps that
    // routing intact; it is the only hook that sees keys before Compose focus handling.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        probe.onKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        probe.onMotionEvent(event) || super.dispatchGenericMotionEvent(event)
}
