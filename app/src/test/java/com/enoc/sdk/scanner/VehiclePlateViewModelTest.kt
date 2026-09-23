package com.enoc.sdk.scanner

import com.enoc.sdk.scanner.domain.model.VehiclePlateResult
import com.enoc.sdk.scanner.domain.repository.VehiclePlateRepository
import com.enoc.sdk.scanner.domain.usecase.ScanVehiclePlateUseCase
import com.enoc.sdk.scanner.presentation.state.VehiclePlateUiEvent
import com.enoc.sdk.scanner.presentation.viewmodel.VehiclePlateViewModel
import com.enoc.sdk.scanner.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclePlateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepository: FakeVehiclePlateRepository
    private lateinit var useCase: ScanVehiclePlateUseCase
    private lateinit var fakeLogger: FakeLogger
    private lateinit var viewModel: VehiclePlateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeVehiclePlateRepository()
        useCase = ScanVehiclePlateUseCase(fakeRepository)
        fakeLogger = FakeLogger()
        viewModel = VehiclePlateViewModel(useCase, fakeRepository, fakeLogger)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_isEmptyAndReady() {
        val state = viewModel.uiState.value
        assertEquals("Ready to scan vehicle plate", state.lastScannedPlate)
        assertFalse(state.showScanner)
        assertFalse(state.isLoading)
        assertTrue(state.history.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun testStartScanSession_updatesShowScanner() {
        viewModel.onEvent(VehiclePlateUiEvent.StartScanSession)
        assertTrue(viewModel.uiState.value.showScanner)
    }

    @Test
    fun testStopScanSession_hidesScanner() {
        viewModel.onEvent(VehiclePlateUiEvent.StartScanSession)
        viewModel.onEvent(VehiclePlateUiEvent.StopScanSession)
        assertFalse(viewModel.uiState.value.showScanner)
    }

    @Test
    fun testPlateDetected_happyPath_savesToHistoryAndState() = runTest {
        viewModel.onEvent(VehiclePlateUiEvent.PlateDetected("DXB-5555"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showScanner)
        assertFalse(state.isLoading)
        assertTrue(state.lastScannedPlate.contains("DXB-5555"))
        assertEquals(1, state.history.size)
        assertEquals("DXB-5555", state.history[0].plateNumber)
    }

    @Test
    fun testScanError_updatesErrorMessage() {
        viewModel.onEvent(VehiclePlateUiEvent.ScanError("Camera failed entirely"))
        val state = viewModel.uiState.value
        assertEquals("Camera failed entirely", state.errorMessage)
        assertFalse(state.showScanner)
    }

    @Test
    fun testClearError_resetsErrorMessageToNull() {
        viewModel.onEvent(VehiclePlateUiEvent.ScanError("Some error"))
        viewModel.onEvent(VehiclePlateUiEvent.ClearError)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // Fakes implementation to mock external boundaries (TEST-001)
    private class FakeVehiclePlateRepository : VehiclePlateRepository {
        private val _history = MutableStateFlow<List<VehiclePlateResult>>(emptyList())
        
        override fun getScanHistory(): Flow<List<VehiclePlateResult>> = _history.asStateFlow()

        override suspend fun saveScanResult(result: VehiclePlateResult) {
            val list = _history.value.toMutableList()
            list.add(0, result)
            _history.value = list
        }
    }

    private class FakeLogger : AppLogger {
        override fun d(feature: String, message: String, metadata: Map<String, String>) {}
        override fun i(feature: String, message: String, metadata: Map<String, String>) {}
        override fun w(feature: String, message: String, metadata: Map<String, String>, throwable: Throwable?) {}
        override fun e(feature: String, message: String, metadata: Map<String, String>, throwable: Throwable?) {}
    }
}
