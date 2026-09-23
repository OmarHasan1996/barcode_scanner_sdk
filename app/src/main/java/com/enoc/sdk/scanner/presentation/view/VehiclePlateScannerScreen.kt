package com.enoc.sdk.scanner.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enoc.sdk.scanner.core.Scanner
import com.enoc.sdk.scanner.core.ScannerSdk
import com.enoc.sdk.scanner.core.ScannerView
import com.enoc.sdk.scanner.presentation.state.VehiclePlateUiEvent
import com.enoc.sdk.scanner.presentation.state.VehiclePlateUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VehiclePlateScannerScreen(
    state: VehiclePlateUiState,
    onEvent: (VehiclePlateUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    LaunchedEffect(state.showScanner) {
        if (state.showScanner) {
            val scanner = ScannerSdk.getScanner()
            scanner.enableAllCodeType(false)
            scanner.setCodeTypeOn(Scanner.CodeType.PLATE)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.showScanner) {
            Box(modifier = Modifier.fillMaxSize()) {
                ScannerView(
                    onClose = { onEvent(VehiclePlateUiEvent.StopScanSession) },
                    onBarcodeDetected = { result ->
                        onEvent(VehiclePlateUiEvent.PlateDetected(result.text))
                    }
                )

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Vehicle Plate Reader Feature",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Status / Last Scan:",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.lastScannedPlate,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                state.errorMessage?.let { error ->
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Button(onClick = { onEvent(VehiclePlateUiEvent.ClearError) }) {
                        Text("Clear Error")
                    }
                }

                Button(
                    onClick = { onEvent(VehiclePlateUiEvent.StartScanSession) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text("Launch Vehicle Plate Scanner", fontSize = 16.sp)
                }

                Text(
                    text = "Secure Local Scan History:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                if (state.history.isEmpty()) {
                    Text(
                        text = "No history recorded yet.",
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.history) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.plateNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = sdf.format(Date(item.timestamp)),
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
