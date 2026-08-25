package com.tobevpn.tv.presentation.devices

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.R
import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.dto.LinkedDeviceDto
import com.tobevpn.tv.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkedDevicesUiState(
    val isLoading: Boolean = false,
    val currentDeviceId: String? = null,
    val currentDeviceAliases: Set<String> = emptySet(),
    val currentCount: Int? = null,
    val maxDevices: Int = 5,
    val devices: List<LinkedDeviceDto> = emptyList(),
    val busyDeviceId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val botApi: BotApi,
) : ViewModel() {

    private val _state = MutableStateFlow(LinkedDevicesUiState())
    val state: StateFlow<LinkedDevicesUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val before = _state.value
            val refreshFeedbackStartedAt = if (
                before.devices.isNotEmpty() || before.currentCount != null
            ) {
                SystemClock.elapsedRealtime()
            } else {
                null
            }
            val currentDeviceId = authRepository.getOrCreateDeviceId()
            val currentDeviceAliases = authRepository.getCurrentDeviceAliases()
            _state.value = before.copy(
                isLoading = true,
                errorMessage = null,
                currentDeviceId = currentDeviceId,
                currentDeviceAliases = currentDeviceAliases,
            )

            _state.value = try {
                authRepository.registerCurrentDevice()
                runCatching { authRepository.pingHwidOnly() }
                val response = botApi.getDevices()
                val data = response.data
                if (response.success && data != null) {
                    awaitMinimumRefreshFeedback(refreshFeedbackStartedAt)
                    LinkedDevicesUiState(
                        isLoading = false,
                        currentDeviceId = currentDeviceId,
                        currentDeviceAliases = currentDeviceAliases,
                        currentCount = data.currentCount,
                        maxDevices = data.maxDevices,
                        devices = data.devices.orEmpty(),
                    )
                } else {
                    awaitMinimumRefreshFeedback(refreshFeedbackStartedAt)
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = response.message ?: context.getString(R.string.devices_load_error),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                awaitMinimumRefreshFeedback(refreshFeedbackStartedAt)
                _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: context.getString(R.string.devices_load_error),
                )
            }
        }
    }

    private suspend fun awaitMinimumRefreshFeedback(startedAt: Long?) {
        if (startedAt == null) return
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = MIN_REFRESH_FEEDBACK_MS - elapsed
        if (remaining > 0L) delay(remaining)
    }

    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busyDeviceId = deviceId, errorMessage = null)
            _state.value = try {
                val result = authRepository.unlinkOtherDevice(deviceId)
                if (result.isSuccess) {
                    _state.value.copy(busyDeviceId = null)
                } else {
                    _state.value.copy(
                        busyDeviceId = null,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: context.getString(R.string.error_devices_disconnect),
                    )
                }
            } catch (_: Exception) {
                _state.value.copy(
                    busyDeviceId = null,
                    errorMessage = context.getString(R.string.error_devices_disconnect),
                )
            }
            refresh()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private companion object {
        const val MIN_REFRESH_FEEDBACK_MS = 900L
    }
}
