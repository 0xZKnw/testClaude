package com.zknw.unoduo.ui.components

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera preview that reports the first QR code it decodes, exactly once.
 *
 * Three things decide whether this feels instant or sluggish, and all three are handled
 * here rather than left to defaults:
 *
 *  - **How much image is examined.** Only the square the viewfinder draws is decoded,
 *    not the whole frame. Fewer pixels to threshold, and the code fills more of what is
 *    examined, so it is both faster and more likely to succeed.
 *  - **How often.** The frame buffer is reused instead of allocated per frame, and the
 *    inverted second pass — which used to run on every frame that failed, meaning nearly
 *    all of them — now runs once in a while. That roughly doubles the frames actually
 *    looked at.
 *  - **Focus.** A phone screen held close is exactly where continuous autofocus hunts.
 *    The centre is metered on start, and tapping re-focuses wherever you touch.
 */
@Composable
fun QrScanner(modifier: Modifier = Modifier, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onResult)
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val decoder = remember { QrDecoder() }
    val camera = remember { arrayOfNulls<Camera>(1) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 1280x720 rather than the 640x480 default: a QR photographed off another
            // phone's screen is small in frame, and the extra pixels are what let it
            // resolve. The crop below keeps the cost down anyway.
            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolution)
                .build()

            analysis.setAnalyzer(executor) { image ->
                try {
                    if (!delivered.get()) {
                        val text = decoder.decode(image)
                        if (text != null && delivered.compareAndSet(false, true)) {
                            ContextCompat.getMainExecutor(context).execute {
                                callback.value(text)
                            }
                        }
                    }
                } finally {
                    image.close()
                }
            }

            runCatching {
                cameraProvider.unbindAll()
                val bound = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                camera[0] = bound
                focusOnCentre(bound, previewView)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val control = camera[0]?.cameraControl ?: return@detectTapGestures
                val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                runCatching {
                    control.startFocusAndMetering(
                        FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                    )
                }
            }
        }
    )
}

/** Nudges autofocus onto the middle of the frame, which is where the viewfinder is. */
private fun focusOnCentre(camera: Camera, previewView: PreviewView) {
    val point = previewView.meteringPointFactory.createPoint(
        previewView.width / 2f,
        previewView.height / 2f
    )
    runCatching {
        camera.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
        )
    }
}
