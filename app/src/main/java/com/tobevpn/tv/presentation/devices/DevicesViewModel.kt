package com.tobevpn.tv.presentation.devices

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.R
import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.dto.DeviceUnlinkRequestDto
import com.tobevpn.tv.data.remote.dto.LinkedDeviceDto
import com.tobevpn.tv.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val currentDeviceId = authRepository.getOrCreateDeviceId()
            val currentDeviceAliases = authRepository.getCurrentDeviceAliases()
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                currentDeviceId = currentDeviceId,
                currentDeviceAliases = currentDeviceAliases,
            )

            authRepository.registerCurrentDevice()
            runCatching { authRepository.pingHwidOnly() }

            _state.value = try {
                val response = botApi.getDevices()
                val data = response.data
                if (response.success && data != null) {
                    LinkedDevicesUiState(
                        isLoading = false,
                        currentDeviceId = currentDeviceId,
                        currentDeviceAliases = currentDeviceAliases,
                        currentCount = data.currentCount,
                        maxDevices = data.maxDevices,
                        devices = data.devices,
                    )
                } else {
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = response.message ?: context.getString(R.string.devices_load_error),
                    )
                }
            } catch (e: Exception) {
                _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: context.getString(R.string.devices_load_error),
                )
            }
        }
    }

    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busyDeviceId = deviceId, errorMessage = null)
            _state.value = try {
                val response = botApi.unlinkDevice(DeviceUnlinkRequestDto(deviceId = deviceId))
                if (response.success) {
                    _state.value.copy(busyDeviceId = null)
                } else {
                    _state.value.copy(
                        busyDeviceId = null,
                        errorMessage = response.message ?: context.getString(R.string.error_devices_disconnect),
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
}
