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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.enoc.sdk.scanner.core.ScannerView
import com.enoc.sdk.scanner.data.repository.VehiclePlateRepositoryImpl
import com.enoc.sdk.scanner.domain.usecase.ScanVehiclePlateUseCase
import com.enoc.sdk.scanner.logging.AppLoggerImpl
import com.enoc.sdk.scanner.presentation.view.VehiclePlateScannerScreen
import com.enoc.sdk.scanner.presentation.viewmodel.VehiclePlateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerSdkTheme {
                val context = LocalContext.current
                val scannerManager = remember { ScannerManager(context.applicationContext) }
                val scope = rememberCoroutineScope()

                // Explicit Framework DI resolution (ARCH-004)
                val logger = remember { AppLoggerImpl(isProduction = false) }
                val repository = remember { VehiclePlateRepositoryImpl(context.applicationContext, logger) }
                val useCase = remember { ScanVehiclePlateUseCase(repository) }
                val vehiclePlateViewModel = remember { VehiclePlateViewModel(useCase, repository, logger) }

                var appMode by remember { mutableStateOf("barcode") } // "barcode" or "plate"

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (appMode == "plate") {
                            val plateState by vehiclePlateViewModel.uiState.collectAsState()
                            Column(modifier = Modifier.fillMaxSize()) {
                                Button(
                                    onClick = { appMode = "barcode" },
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text("← Switch to Barcode Mode")
                                }
                                VehiclePlateScannerScreen(
                                    state = plateState,
                                    onEvent = { event -> vehiclePlateViewModel.onEvent(event) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            var lastScannedCode by remember { mutableStateOf("Ready to scan") }
                            var showScanner by remember { mutableStateOf(false) }

                            if (showScanner) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    ScannerView(
                                        onClose = { showScanner = false },
                                        onBarcodeDetected = { _ -> }
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

                                        Button(onClick = { showScanner = false }) {
                                            Text("Close Scanner")
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = lastScannedCode,
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    Button(
                                        onClick = {
                                            showScanner = true
                                            scope.launch {
                                                scannerManager.scanOnce().collect { outcome ->
                                                    when (outcome) {
                                                        is ScanOutcome.Success -> {
                                                            lastScannedCode = "Result: ${outcome.value}"
                                                            showScanner = false
                                                        }
                                                        is ScanOutcome.Error -> {
                                                            lastScannedCode = "Error: ${outcome.message}"
                                                            showScanner = false
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Text("Start Barcode Scan Session")
                                    }

                                    Button(onClick = { appMode = "plate" }) {
                                        Text("Go to Vehicle Plate Reader →")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
