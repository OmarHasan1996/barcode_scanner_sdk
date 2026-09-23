package com.enoc.sdk.scanner.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enoc.sdk.scanner.core.Scanner
import com.enoc.sdk.scanner.core.ScannerSdk
import com.enoc.sdk.scanner.domain.repository.VehiclePlateRepository
import com.enoc.sdk.scanner.domain.usecase.ScanVehiclePlateUseCase
import com.enoc.sdk.scanner.presentation.state.VehiclePlateUiEvent
import com.enoc.sdk.scanner.presentation.state.VehiclePlateUiState
import com.enoc.sdk.scanner.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VehiclePlateViewModel(
    private val scanVehiclePlateUseCase: ScanVehiclePlateUseCase,
    private val repository: VehiclePlateRepository,
    private val logger: AppLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehiclePlateUiState())
    val uiState: StateFlow<VehiclePlateUiState> = _uiState.asStateFlow()

    init {
        logger.i("VIEW_MODEL", "VehiclePlateViewModel initialized cleanly with Dependency Injection.")
        observeScanHistory()
    }

    fun onEvent(event: VehiclePlateUiEvent) {
        when (event) {
            is VehiclePlateUiEvent.StartScanSession -> {
                val scanner = ScannerSdk.getScanner()
                scanner.enableAllCodeType(false)
                scanner.setCodeTypeOn(Scanner.CodeType.PLATE)
                _uiState.update { it.copy(showScanner = true, errorMessage = null) }
                logger.i("VIEW_MODEL", "User action: Started vehicle plate scanning session.")
            }
            is VehiclePlateUiEvent.StopScanSession -> {
                _uiState.update { it.copy(showScanner = false) }
                logger.i("VIEW_MODEL", "User action: Stopped vehicle plate scanning session.")
            }
            is VehiclePlateUiEvent.PlateDetected -> {
                viewModelScope.launch {
                    try {
                        _uiState.update { it.copy(isLoading = true) }
                        logger.i("ENOC_SCANNER_SDK", "[STEP 8 - VIEWMODEL] Plate detected event received: ${event.text}")
                        
                        val result = scanVehiclePlateUseCase.execute(event.text)
                        
                        _uiState.update {
                            it.copy(
                                lastScannedPlate = "Last plate successfully saved: ${result.plateNumber}",
                                showScanner = false,
                                isLoading = false
                            )
                        }
                    } catch (e: Exception) {
                        logger.e("VIEW_MODEL", "Error processing detected vehicle plate text", throwable = e)
                        _uiState.update { it.copy(errorMessage = e.message ?: "Failed to process plate", isLoading = false) }
                    }
                }
            }
            is VehiclePlateUiEvent.ScanError -> {
                _uiState.update { it.copy(errorMessage = event.error, showScanner = false) }
                logger.w("VIEW_MODEL", "Scan error received: ${event.error}")
            }
            is VehiclePlateUiEvent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun observeScanHistory() {
        viewModelScope.launch {
            repository.getScanHistory().collect { historyList ->
                _uiState.update { it.copy(history = historyList) }
            }
        }
    }
}
