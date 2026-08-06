package com.enoc.sdk.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.enoc.sdk.scanner.ui.theme.ScannerSdkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.barcode.sdk.view.BarcodeScannerView
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerSdkTheme {
                val cameraPermissionState = rememberPermissionState(
                    android.Manifest.permission.CAMERA
                )

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (cameraPermissionState.status.isGranted) {
                            var lastScannedCode by remember { mutableStateOf("Scan a barcode") }
                            
                            Box(modifier = Modifier.fillMaxSize()) {
                                val lifecycleOwner = LocalLifecycleOwner.current
                                AndroidView(
                                    factory = { context ->
                                        BarcodeScannerView(context).apply {
                                            startScanning(
                                                lifecycleOwner = lifecycleOwner,
                                                formats = setOf(
                                                    BarcodeFormat.CODE_128
                                                )
                                            ) { result ->
                                                lastScannedCode = "${result.format}: ${result.text}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Red flasher line in the middle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(0.8f)
                                        .height(2.dp)
                                        .background(Color.Red)
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = lastScannedCode,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }

                                Text(
                                    text = "Focus Code 128 on Red Line",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 32.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Camera permission is required")
                                androidx.compose.material3.Button(
                                    onClick = { cameraPermissionState.launchPermissionRequest() }
                                ) {
                                    Text("Request Permission")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ScannerSdkTheme {
        Greeting("Android")
    }
}