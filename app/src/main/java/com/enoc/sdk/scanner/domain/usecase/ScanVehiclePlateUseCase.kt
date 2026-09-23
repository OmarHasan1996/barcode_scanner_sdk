package com.enoc.sdk.scanner.domain.usecase

import com.enoc.sdk.scanner.domain.model.VehiclePlateResult
import com.enoc.sdk.scanner.domain.repository.VehiclePlateRepository

class ScanVehiclePlateUseCase(
    private val repository: VehiclePlateRepository
) {
    suspend fun execute(plateText: String): VehiclePlateResult {
        val result = VehiclePlateResult(plateNumber = plateText)
        repository.saveScanResult(result)
        return result
    }
}
