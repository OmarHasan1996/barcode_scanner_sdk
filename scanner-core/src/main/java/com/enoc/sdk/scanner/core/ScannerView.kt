package com.enoc.sdk.scanner.core

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.enoc.sdk.scanner.core.analysis.BarcodeAnalyzer
import com.enoc.sdk.scanner.core.analysis.VehiclePlateAnalyzer
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult
import com.enoc.sdk.scanner.core.utils.Logger
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun ScannerView(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onBarcodeDetected: (BarcodeResult) -> Unit
) {
    val context = LocalContext.current
    
    // Permission State
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        ScannerCameraPreview(modifier, onClose, onBarcodeDetected)
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Camera permission is required to scan barcodes")
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
private fun ScannerCameraPreview(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onBarcodeDetected: (BarcodeResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val scanner = ScannerSdk.getScanner() as ScannerImpl
    val formats = scanner.getEnabledFormats()
    val config = scanner.getConfig()
    
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var currentZoom by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(lifecycleOwner, formats, config) {
        val resWidth = config.getInt(Scanner.SCANNER_RESOLUTION_WIDTH, 1280)
        val resHeight = config.getInt(Scanner.SCANNER_RESOLUTION_HEIGHT, 720)
        Logger.i("[STEP 1 - CAMERA INIT] Binding CameraX lifecycle with formats=$formats, targetResolution=${resWidth}x${resHeight}")

        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                androidx.camera.core.resolutionselector.ResolutionStrategy(
                    android.util.Size(resWidth, resHeight),
                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                )
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()
            .also {
                val continueScan = config.getBoolean(Scanner.SCANNER_CONTINUE_SCAN, false)
                val isPlateScanning = formats.contains(BarcodeFormat.VEHICLE_PLATE)
                val analyzer: ImageAnalysis.Analyzer = if (isPlateScanning) {
                    VehiclePlateAnalyzer(continueScan) { result ->
                        scanner.dispatchResult(Scanner.SCANNER_SUCCESS, result.text)
                        currentOnBarcodeDetected(result)
                    }
                } else {
                    BarcodeAnalyzer(formats, continueScan) { result ->
                        scanner.dispatchResult(Scanner.SCANNER_SUCCESS, result.text)
                        currentOnBarcodeDetected(result)
                    }
                }
                it.setAnalyzer(cameraExecutor, analyzer)
            }

        val isBackCamera = config.getBoolean(Scanner.SCANNER_IS_BACK_CAMERA, true)
        val cameraSelector = if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // INDUSTRIAL OPTIMIZATION: Area-specific Focus & Metering
            // We lock the focus and auto-exposure to the center rectangle (viewfinder)
            val factory = SurfaceOrientedMeteringPointFactory(
                previewView.width.toFloat(),
                previewView.height.toFloat()
            )
            // The viewfinder is 90% wide and 20% high
            val centerPoint = factory.createPoint(0.5f, 0.5f, 0.3f)
            val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(1, TimeUnit.SECONDS)
                .build()

            camera.cameraControl.startFocusAndMetering(action)
            cameraControl = camera.cameraControl

            // Torch control
            val isTorchOn = config.getBoolean(Scanner.SCANNER_IS_TORCH_ON, false)
            camera.cameraControl.enableTorch(isTorchOn)

            camera.cameraControl.setZoomRatio(currentZoom)

        } catch (e: Exception) {
            Logger.e("Camera binding failed", e)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Dark overlay outside the viewfinder
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewfinderWidth = size.width * 0.8f
            val viewfinderHeight = size.height * 0.2f
            val left = (size.width - viewfinderWidth) / 2
            val top = (size.height - viewfinderHeight) / 2

            clipPath(
                path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, left + viewfinderWidth, top + viewfinderHeight),
                            cornerRadius = CornerRadius(12.dp.toPx())
                        )
                    )
                },
                clipOp = ClipOp.Difference
            ) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }
        }

        // Viewfinder box with white border and rounded corners
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.2f)
                .border(
                    width = 2.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
        )

        // Exit button at top right
        onClose?.let { closeAction ->
            IconButton(
                onClick = {
                    scanner.stopScan()
                    closeAction()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Scanner",
                    tint = Color.White
                )
            }
        }

        if(config.getBoolean(Scanner.SCANNER_ZOOM_ENABLE, true)){
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentZoom > 1.0f) {
                            currentZoom -= 0.2f
                            cameraControl?.setZoomRatio(currentZoom)
                        }
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
                }

                Text(
                    text = "${"%.1f".format(currentZoom)}x",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = {
                        if (currentZoom < 3f) {
                            currentZoom += 0.2f
                            cameraControl?.setZoomRatio(currentZoom)
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
                }
            }
        }

    }
}
