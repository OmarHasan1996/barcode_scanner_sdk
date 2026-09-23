package com.enoc.sdk.scanner.presentation.state

sealed class VehiclePlateUiEvent {
    object StartScanSession : VehiclePlateUiEvent()
    object StopScanSession : VehiclePlateUiEvent()
    data class PlateDetected(val text: String) : VehiclePlateUiEvent()
    data class ScanError(val error: String) : VehiclePlateUiEvent()
    object ClearError : VehiclePlateUiEvent()
}
