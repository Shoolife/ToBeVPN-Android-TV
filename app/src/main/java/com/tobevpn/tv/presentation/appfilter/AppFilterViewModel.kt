package com.tobevpn.tv.presentation.appfilter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.InstalledAppItem
import com.tobevpn.tv.data.InstalledAppsProvider
import com.tobevpn.tv.data.repository.AppFilterRepository
import com.tobevpn.tv.domain.model.AppFilterMode
import com.tobevpn.tv.domain.model.ConnectionState
import com.tobevpn.tv.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppFilterUiState(
    val mode: AppFilterMode = AppFilterMode.OFF,
    val selected: Set<String> = emptySet(),
    val apps: List<InstalledAppItem> = emptyList(),
    val visibleApps: List<InstalledAppItem> = emptyList(),
    val showSystem: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class AppFilterViewModel @Inject constructor(
    private val repo: AppFilterRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val connectionManager: VpnConnectionManager,
) : ViewModel() {
    private val _allApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val _showSystem = MutableStateFlow(false)
    private val _loading = MutableStateFlow(true)
    private val _reconnectBanner = MutableStateFlow(false)
    private var reconnectBannerJob: Job? = null

    val reconnectBanner: StateFlow<Boolean> = _reconnectBanner.asStateFlow()

    val mode: StateFlow<AppFilterMode> = repo.observeMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppFilterMode.OFF)

    val selected: StateFlow<Set<String>> = repo.observeSelectedPackages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val state: StateFlow<AppFilterUiState> = combine(
        mode,
        selected,
        _showSystem,
        _loading,
        _allApps,
    ) { mode, selected, showSystem, loading, allApps ->
        val visible = allApps
            .asSequence()
            .filter { showSystem || !it.isSystem }
            .toList()
        AppFilterUiState(
            mode = mode,
            selected = selected,
            apps = allApps,
            visibleApps = visible,
            showSystem = showSystem,
            loading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppFilterUiState())

    init {
        viewModelScope.launch {
            _allApps.value = installedAppsProvider.listApps(includeSystem = true)
            _loading.value = false
        }
    }

    fun setMode(mode: AppFilterMode) {
        repo.setMode(mode)
        flashReconnectBanner()
    }

    fun toggle(packageName: String) {
        repo.toggle(packageName)
        flashReconnectBanner()
    }

    fun toggleShowSystem() {
        _showSystem.value = !_showSystem.value
    }

    fun selectAllVisible() {
        repo.setSelected((selected.value + state.value.visibleApps.map { it.packageName }).toSet())
        flashReconnectBanner()
    }

    fun clearAll() {
        repo.clearAll()
        flashReconnectBanner()
    }

    private fun flashReconnectBanner() {
        if (connectionManager.connectionState.value !is ConnectionState.Connected) return
        reconnectBannerJob?.cancel()
        _reconnectBanner.value = true
        reconnectBannerJob = viewModelScope.launch {
            delay(3000)
            _reconnectBanner.value = false
        }
    }
}
