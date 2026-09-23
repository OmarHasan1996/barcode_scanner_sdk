package com.enoc.sdk.scanner.domain.repository

import com.enoc.sdk.scanner.domain.model.VehiclePlateResult
import kotlinx.coroutines.flow.Flow

interface VehiclePlateRepository {
    fun getScanHistory(): Flow<List<VehiclePlateResult>>
    suspend fun saveScanResult(result: VehiclePlateResult)
}
