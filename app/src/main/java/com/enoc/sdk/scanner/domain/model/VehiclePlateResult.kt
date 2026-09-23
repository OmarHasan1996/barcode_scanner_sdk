package com.enoc.sdk.scanner.domain.model

data class VehiclePlateResult(
    val plateNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)
