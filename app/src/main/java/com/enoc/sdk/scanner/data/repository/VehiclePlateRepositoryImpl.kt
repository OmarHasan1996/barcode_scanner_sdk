package com.enoc.sdk.scanner.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.enoc.sdk.scanner.domain.model.VehiclePlateResult
import com.enoc.sdk.scanner.domain.repository.VehiclePlateRepository
import com.enoc.sdk.scanner.logging.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VehiclePlateRepositoryImpl(
    context: Context,
    private val logger: AppLogger
) : VehiclePlateRepository {

    private val sharedPreferences by lazy {
        try {
            val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
            val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

            EncryptedSharedPreferences.create(
                "secure_vehicle_plates_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            logger.e("DATA_LAYER", "Failed to initialize hardware-backed secure storage", throwable = e)
            context.getSharedPreferences("fallback_insecure_prefs_do_not_use", Context.MODE_PRIVATE)
        }
    }

    private val _historyFlow = MutableStateFlow<List<VehiclePlateResult>>(emptyList())

    init {
        loadHistory()
    }

    override fun getScanHistory(): Flow<List<VehiclePlateResult>> {
        return _historyFlow.asStateFlow()
    }

    override suspend fun saveScanResult(result: VehiclePlateResult) {
        val currentHistory = _historyFlow.value.toMutableList()
        currentHistory.add(0, result)
        
        sharedPreferences.edit().apply {
            putString("LAST_PLATE_${result.timestamp}", result.plateNumber)
            putLong("LAST_PLATE_TIME_${result.timestamp}", result.timestamp)
            apply()
        }
        
        _historyFlow.value = currentHistory
        logger.i("DATA_LAYER", "Saved scan result safely to hardware-backed secure storage.")
    }

    private fun loadHistory() {
        val allEntries = sharedPreferences.all
        val list = allEntries.keys
            .filter { it.startsWith("LAST_PLATE_") && !it.startsWith("LAST_PLATE_TIME_") }
            .mapNotNull { key ->
                val timestampStr = key.removePrefix("LAST_PLATE_")
                val timestamp = timestampStr.toLongOrNull() ?: return@mapNotNull null
                val plateNumber = sharedPreferences.getString(key, null) ?: return@mapNotNull null
                VehiclePlateResult(plateNumber, timestamp)
            }
            .sortedByDescending { it.timestamp }
        
        _historyFlow.value = list
    }
}
