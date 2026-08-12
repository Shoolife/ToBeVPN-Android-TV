package com.tobevpn.tv.presentation.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.data.remote.dto.PurchasePlansDto
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.CurrencyRepository
import com.tobevpn.tv.data.repository.PurchaseRepository
import com.tobevpn.tv.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CurrentPlanLimits(
    val trafficLimitBytes: Long,
    val deviceLimit: Int,
    val renewalUrl: String?,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val currencyRepository: CurrencyRepository,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Unauthenticated)

    private val _rubToUsdRate = MutableStateFlow<Double?>(null)
    val rubToUsdRate: StateFlow<Double?> = _rubToUsdRate

    private val _purchasePlans = MutableStateFlow<PurchasePlansDto?>(null)
    val purchasePlans: StateFlow<PurchasePlansDto?> = _purchasePlans

    private val _purchasePlansLoading = MutableStateFlow(true)
    val purchasePlansLoading: StateFlow<Boolean> = _purchasePlansLoading

    private val _currentLimits = MutableStateFlow<CurrentPlanLimits?>(null)
    val currentLimits: StateFlow<CurrentPlanLimits?> = _currentLimits

    private val _currentLimitsLoading = MutableStateFlow(false)
    val currentLimitsLoading: StateFlow<Boolean> = _currentLimitsLoading

    private val _isRefreshing = MutableStateFlow(false)

    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            _rubToUsdRate.value = currencyRepository.getRubToUsdRate()
        }
        loadPurchasePlans()
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    if (_currentLimits.value == null &&
                        !_currentLimitsLoading.value &&
                        !_isRefreshing.value
                    ) {
                        loadCurrentLimits()
                    }
                } else {
                    _currentLimits.value = null
                    _currentLimitsLoading.value = false
                }
            }
        }
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                refresh()
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Prices can change after a promocode is applied in the bot;
                // always refresh server-calculated final_amount as well.
                loadPurchasePlans()
                authRepository.syncSubscription()
                loadCurrentLimits()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadPurchasePlans() {
        viewModelScope.launch {
            _purchasePlansLoading.value = true
            try {
                _purchasePlans.value = purchaseRepository.getPlans()
            } finally {
                _purchasePlansLoading.value = false
            }
        }
    }

    private fun loadCurrentLimits() {
        val state = authState.value
        if (state !is AuthState.Authenticated) {
            _currentLimits.value = null
            _currentLimitsLoading.value = false
            return
        }
        viewModelScope.launch {
            _currentLimitsLoading.value = true
            try {
                val limits = authRepository.getCurrentPlanLimits()
                _currentLimits.value = limits?.let {
                    CurrentPlanLimits(
                        trafficLimitBytes = it.trafficLimitBytes,
                        deviceLimit = it.deviceLimit,
                        renewalUrl = it.renewalUrl,
                    )
                }
            } finally {
                _currentLimitsLoading.value = false
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}
