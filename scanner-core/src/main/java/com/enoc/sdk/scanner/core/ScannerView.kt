//package com.enoc.sdk.scanner.core
//
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.camera.core.resolutionselector.ResolutionSelector
//import androidx.camera.core.resolutionselector.ResolutionStrategy
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.remember
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.rememberUpdatedState
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.lifecycle.compose.LocalLifecycleOwner
//import com.enoc.sdk.scanner.core.analysis.BarcodeAnalyzer
//import java.util.concurrent.Executors
//
//@Composable
//fun ScannerView(
//    modifier: Modifier = Modifier,
//    onBarcodeDetected: (String) -> Unit
//) {
//    val context = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
//
//    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
//    val previewView = remember { PreviewView(context) }
//    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
//
//    LaunchedEffect(lifecycleOwner) {
//        val cameraProvider = cameraProviderFuture.get()
//        val preview = Preview.Builder().build().also {
//            it.surfaceProvider = previewView.surfaceProvider
//        }
//
//        val resolutionSelector = ResolutionSelector.Builder()
//            .setResolutionStrategy(
//                androidx.camera.core.resolutionselector.ResolutionStrategy(
//                    android.util.Size(1280, 720),
//                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
//                )
//            )
//            .build()
//
//        val imageAnalysis = ImageAnalysis.Builder()
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//            .setResolutionSelector(resolutionSelector)
//            .build()
//            .also {
//                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
//                    currentOnBarcodeDetected(barcode)
//                })
//            }
//
//        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//        try {
//            cameraProvider.unbindAll()
//            val camera = cameraProvider.bindToLifecycle(
//                lifecycleOwner,
//                cameraSelector,
//                preview,
//                imageAnalysis
//            )
//
//            // Apply zoom
//            camera.cameraControl.setZoomRatio(2.0f)
//
//            // Initial focus
//            val factory = previewView.meteringPointFactory
//            val centerPoint = factory.createPoint(0.5f, 0.5f)
//            val action = androidx.camera.core.FocusMeteringAction.Builder(centerPoint)
//                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
//                .build()
//            camera.cameraControl.startFocusAndMetering(action)
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    Box(modifier = modifier.fillMaxSize()) {
//        AndroidView(
//            factory = { previewView },
//            modifier = Modifier.fillMaxSize()
//        )
//
//        // Red flasher line in the middle
//        Box(
//            modifier = Modifier
//                .align(Alignment.Center)
//                .fillMaxWidth(0.8f)
//                .height(2.dp)
//                .background(Color.Red)
//        )
//    }
//}
