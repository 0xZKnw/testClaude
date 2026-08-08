package com.zknw.unoduo.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Turns the screen up while a QR code is on it, and puts it back on the way out.
 *
 * A camera reads a code by contrast, and a phone screen at automatic brightness in a
 * dim room is barely brighter than the paper around it. This is the difference between
 * locking on at once and hunting for several seconds, and it costs nothing: the screen
 * only stays bright while the code is actually shown.
 */
@Composable
fun BrightScreen() {
    val window = LocalContext.current.findActivity()?.window

    DisposableEffect(window) {
        val previous = window?.attributes?.screenBrightness
        window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
        }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    // BRIGHTNESS_OVERRIDE_NONE hands control back to the system setting.
                    screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
