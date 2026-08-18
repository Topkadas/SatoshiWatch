package com.satoshiwatch.ui.screens

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.satoshiwatch.R

/**
 * Celoobrazovkový dialog se skenerem QR kódů (CameraX + ZXing).
 * ZXing dekóduje čistě offline – žádná telemetrie, žádná síťová volání.
 * Volající musí zajistit oprávnění CAMERA před zobrazením.
 */
@Composable
fun QrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                QrCameraPreview(
                    onQrDetected = onResult,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Text(
                    stringResource(R.string.scanner_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(onQrDetected: (String) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    // Sdílený stav mezi asynchronním bindem kamery a úklidem composable:
    // provider se může vyřešit až PO zavření dialogu – pak se bind přeskočí.
    val session = remember { CameraSession() }
    // Chrání proti vícenásobnému vyhodnocení téhož kódu ze streamu snímků
    val delivered = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { session.dispose() }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context)
            val future = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            future.addListener({
                val provider = future.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(
                    mainExecutor,
                    ZxingQrAnalyzer { value ->
                        if (!delivered.value) {
                            delivered.value = true
                            onQrDetected(value)
                        }
                    }
                )

                if (!session.bind(provider)) return@addListener
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }, mainExecutor)
            previewView
        }
    )
}

/** Drží vazbu na kameru a řeší závod mezi asynchronním bindem a zánikem composable. */
private class CameraSession {
    private var provider: ProcessCameraProvider? = null
    private var disposed = false

    /** Vrací false, pokud už composable zanikl – kamera se pak vůbec neváže. */
    @Synchronized
    fun bind(cameraProvider: ProcessCameraProvider): Boolean {
        if (disposed) return false
        provider = cameraProvider
        return true
    }

    @Synchronized
    fun dispose() {
        disposed = true
        provider?.unbindAll()
        provider = null
    }
}

/**
 * ZXing analyzátor QR kódů nad Y (luminance) rovinou YUV_420_888 snímku.
 * Snímky zavírá vždy; rowStride předává jako dataWidth (může být > width).
 */
private class ZxingQrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val rowStride = plane.rowStride
            // Poslední řádek nemusí být v bufferu celý – výška se dopočítá bezpečně
            val height = minOf(imageProxy.height, data.size / rowStride)
            if (height < 1) return

            val source = PlanarYUVLuminanceSource(
                data,
                rowStride,
                height,
                0,
                0,
                minOf(imageProxy.width, rowStride),
                height,
                false
            )
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            result.text?.takeIf { it.isNotBlank() }?.let(onQr)
        } catch (_: NotFoundException) {
            // v tomto snímku není QR kód – běžný stav
        } catch (_: Exception) {
            // poškozený snímek nesmí shodit skener
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }
}
