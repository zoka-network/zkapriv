package com.zoka.zkapriv.ui.qrscanner

import android.Manifest
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.StrokeCap
import com.zoka.zkapriv.ui.components.MeterBar
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.util.concurrent.Executors

/**
 * Lector QR de ZKAPriv 3: cámara a pantalla completa, marco con esquinas
 * rosas y, para una dirección completa, el avance de sus partes.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var flashEnabled by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    // Las direcciones completas llegan en varios QR (MultiPartQr): se juntan
    // acá y recién con todas las partes se entrega el texto completo.
    val collector = remember { MultiPartQrCollector() }
    var partsProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val c = Zk.colors

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            Text(
                "Scan",
                style = MaterialTheme.typography.titleLarge,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
            if (cameraPermissionState.status.isGranted) {
                IconButton(onClick = { flashEnabled = !flashEnabled }) {
                    Icon(
                        if (flashEnabled) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                        contentDescription = "Flashlight",
                        tint = if (flashEnabled) c.accent else c.text
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreview(
                    onQrCodeScanned = { qrCode ->
                        if (!hasScanned) {
                            if (MultiPartQr.isPart(qrCode)) {
                                when (val result = collector.accept(qrCode)) {
                                    is MultiPartQrCollector.Result.Complete -> {
                                        hasScanned = true
                                        onQrCodeScanned(result.text)
                                    }
                                    is MultiPartQrCollector.Result.Partial ->
                                        partsProgress = result.have to result.total
                                    MultiPartQrCollector.Result.Ignored -> Unit
                                }
                            } else {
                                hasScanned = true
                                onQrCodeScanned(qrCode)
                            }
                        }
                    },
                    flashEnabled = flashEnabled
                )
                ScanFrame(Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val progress = partsProgress
                    if (progress != null) {
                        Pill("Part ${progress.first} of ${progress.second}", tone = ZkTone.Accent)
                        MeterBar(progress.first.toFloat() / progress.second)
                        Text(
                            "Keep the camera on the code: the parts come in any order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.text
                        )
                    } else {
                        Text(
                            "Point at a Zoka address or payment QR",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text
                        )
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Notice(
                        if (cameraPermissionState.status.shouldShowRationale)
                            "ZKAPriv uses the camera only to read QR codes, on this phone."
                        else "Allow the camera to scan QR codes.",
                        title = "Camera",
                        tone = ZkTone.Info
                    )
                    Spacer(Modifier.height(16.dp))
                    ZButton(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ZButtonVariant.Primary
                    ) { ZButtonText("Allow camera") }
                }
            }
        }
    }
}

/** Oscurece alrededor del recuadro y dibuja sus esquinas en rosa. */
@Composable
private fun ScanFrame(modifier: Modifier) {
    val accent = Zk.colors.accent
    val shade = Zk.colors.background.copy(alpha = 0.55f)
    Canvas(modifier) {
        val side = size.minDimension * 0.7f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        drawRect(shade, size = GeoSize(size.width, top))
        drawRect(shade, topLeft = Offset(0f, top + side), size = GeoSize(size.width, size.height - top - side))
        drawRect(shade, topLeft = Offset(0f, top), size = GeoSize(left, side))
        drawRect(shade, topLeft = Offset(left + side, top), size = GeoSize(size.width - left - side, side))
        val arm = side * 0.14f
        val stroke = 4.dp.toPx()
        listOf(
            Offset(left, top) to Offset(1f, 1f),
            Offset(left + side, top) to Offset(-1f, 1f),
            Offset(left, top + side) to Offset(1f, -1f),
            Offset(left + side, top + side) to Offset(-1f, -1f),
        ).forEach { (corner, dir) ->
            drawLine(accent, corner, Offset(corner.x + arm * dir.x, corner.y), stroke, StrokeCap.Round)
            drawLine(accent, corner, Offset(corner.x, corner.y + arm * dir.y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun CameraPreview(
    onQrCodeScanned: (String) -> Unit,
    flashEnabled: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    DisposableEffect(flashEnabled) {
        camera?.cameraControl?.enableTorch(flashEnabled)
        onDispose { }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = Executors.newSingleThreadExecutor()

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // CameraX analiza a 640x480 si no se le pide otra cosa, y a esa
                // resolución un QR denso no se lee desde una pantalla. 1280x720
                // alcanza de sobra para las partes de una zkq1 y para un QR común.
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer { qrCode ->
                            onQrCodeScanned(qrCode)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                    camera?.cameraControl?.enableTorch(flashEnabled)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
