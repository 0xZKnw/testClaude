package com.zknw.unoduo.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera preview that reports the first QR code it decodes, exactly once.
 * The decoding runs on a dedicated single-thread executor.
 */
@Composable
fun QrScanner(modifier: Modifier = Modifier, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onResult)
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }

        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    if (!delivered.get()) {
                        val text = decode(reader, image)
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
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Pulls the luma plane out of the frame and hands it to ZXing. */
private fun decode(reader: MultiFormatReader, image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride

    val luma = ByteArray(width * height)
    buffer.rewind()
    if (rowStride == width) {
        buffer.get(luma, 0, minOf(luma.size, buffer.remaining()))
    } else {
        val row = ByteArray(rowStride)
        var offset = 0
        for (y in 0 until height) {
            if (buffer.remaining() < rowStride) break
            buffer.get(row, 0, rowStride)
            System.arraycopy(row, 0, luma, offset, width)
            offset += width
        }
    }

    val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        try {
            // A rotated / inverted frame sometimes decodes only on the second pass.
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text
        } catch (_: Exception) {
            null
        }
    } finally {
        reader.reset()
    }
}
