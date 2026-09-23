package com.enoc.sdk.scanner.presentation.state

import com.enoc.sdk.scanner.domain.model.VehiclePlateResult

data class VehiclePlateUiState(
    val lastScannedPlate: String = "Ready to scan vehicle plate",
    val showScanner: Boolean = false,
    val isLoading: Boolean = false,
    val history: List<VehiclePlateResult> = emptyList(),
    val errorMessage: String? = null
)
