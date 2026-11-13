package com.zoka.zkapriv.ui.qrscanner

import android.Manifest
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.zoka.zkapriv.ui.components.MeterBar
import com.zoka.zkapriv.ui.components.GlassIconButton
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lector QR de ZKAPriv: cámara a pantalla completa con un recuadro de enfoque
 * (esquinas rosas, una línea que lo recorre y fuera de él, oscuro), tocar para
 * enfocar, linterna y, para una dirección completa, el avance de sus partes.
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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    val c = Zk.colors

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    LaunchedEffect(focus) {
        if (focus != null) {
            delay(1_200)
            focus = null
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
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.Outlined.ArrowBack, "Back", onNavigateBack)
            Spacer(Modifier.width(12.dp))
            Text(
                "Scan to pay",
                style = MaterialTheme.typography.titleLarge,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // Tocar enfoca ese punto (el autoenfoque sigue andando solo).
                    detectTapGestures { offset ->
                        val view = previewView ?: return@detectTapGestures
                        val cam = camera ?: return@detectTapGestures
                        val point = view.meteringPointFactory.createPoint(offset.x, offset.y)
                        cam.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(point)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                        )
                        focus = offset
                    }
                }
        ) {
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
                    flashEnabled = flashEnabled,
                    onReady = { cam, view ->
                        camera = cam
                        previewView = view
                    }
                )
                ScanFrame(Modifier.fillMaxSize(), focus)
                ScannerHints(Modifier.align(Alignment.BottomCenter), partsProgress, flashEnabled) { flashEnabled = !flashEnabled }
            } else {
                Column(
                    Modifier.fillMaxSize().background(c.background).padding(24.dp),
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

/** Las indicaciones bajo el recuadro y la linterna. */
@Composable
private fun ScannerHints(modifier: Modifier, progress: Pair<Int, Int>?, flashEnabled: Boolean, onTorch: () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (progress != null) {
            Pill("Part ${progress.first} of ${progress.second}", tone = ZkTone.Accent)
            MeterBar(progress.first.toFloat() / progress.second)
            Text(
                "Hold steady on the code. Parts can come in any order.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Center the QR code in the frame",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                "ZKA addresses and payment links are read automatically. Tap to focus.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        TorchButton(flashEnabled, onTorch)
    }
}

/** Solo para la vista de diseño: el recuadro y los textos, sin abrir la cámara. */
@Composable
fun ScannerOverlayPreview() {
    Box(Modifier.fillMaxSize().background(Color.DarkGray).navigationBarsPadding()) {
        ScanFrame(Modifier.fillMaxSize(), focus = Offset(540f, 760f))
        ScannerHints(Modifier.align(Alignment.BottomCenter), null, false) {}
    }
}

/** La linterna, abajo y a mano. */
@Composable
private fun TorchButton(on: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GlassIconButton(
            if (on) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
            if (on) "Turn the light off" else "Turn the light on",
            onToggle, modifier = Modifier.size(52.dp), selected = on,
        )
        Text(if (on) "Light on" else "Light", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

/**
 * Oscurece alrededor del recuadro, dibuja sus esquinas en rosa y una línea que
 * lo recorre mientras busca; donde se tocó, un cuadro de enfoque.
 */
@Composable
private fun ScanFrame(modifier: Modifier, focus: Offset?) {
    val accent = Zk.colors.accent
    val shade = Color.Black.copy(alpha = 0.6f)
    val sweep = if (Zk.animations) {
        val transition = rememberInfiniteTransition(label = "scan")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse),
            label = "scan-line",
        )
        value
    } else 0.5f
    Canvas(modifier) {
        val side = size.minDimension * 0.72f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f - size.height * 0.06f
        val radius = 20.dp.toPx()
        // Oscuro alrededor, con un hueco de esquinas redondeadas como el marco.
        val hole = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(Rect(Offset(left, top), GeoSize(side, side)), CornerRadius(radius)))
        }
        drawPath(hole, shade)
        drawRoundRect(
            Color.White.copy(alpha = 0.18f),
            topLeft = Offset(left, top),
            size = GeoSize(side, side),
            cornerRadius = CornerRadius(radius),
            style = Stroke(1.dp.toPx()),
        )
        // Las esquinas, con la misma curva que el recuadro.
        val arm = side * 0.2f
        val stroke = 5.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val path = Path().apply {
                moveTo(x, y + dy * arm)
                lineTo(x, y + dy * radius)
                quadraticBezierTo(x, y, x + dx * radius, y)
                lineTo(x + dx * arm, y)
            }
            drawPath(path, accent, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        corner(left, top, 1f, 1f)
        corner(left + side, top, -1f, 1f)
        corner(left, top + side, 1f, -1f)
        corner(left + side, top + side, -1f, -1f)
        // La línea que recorre el recuadro.
        val y = top + radius + (side - 2 * radius) * sweep
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, accent, Color.Transparent),
                startX = left,
                endX = left + side,
            ),
            start = Offset(left + 14.dp.toPx(), y),
            end = Offset(left + side - 14.dp.toPx(), y),
            strokeWidth = 2.dp.toPx(),
        )
        focus?.let { f ->
            val box = 64.dp.toPx()
            drawRoundRect(
                accent,
                topLeft = Offset(f.x - box / 2f, f.y - box / 2f),
                size = GeoSize(box, box),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

@Composable
fun CameraPreview(
    onQrCodeScanned: (String) -> Unit,
    flashEnabled: Boolean,
    onReady: (Camera, PreviewView) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(flashEnabled) {
        camera?.cameraControl?.enableTorch(flashEnabled)
        onDispose { }
    }

    AndroidView(
        factory = { ctx ->
            // TextureView en vez de SurfaceView: así el recuadro dibujado
            // encima se ve siempre, también en los teléfonos que ponían la
            // superficie de la cámara por arriba de la interfaz.
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
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
                    camera?.let { onReady(it, previewView) }
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
