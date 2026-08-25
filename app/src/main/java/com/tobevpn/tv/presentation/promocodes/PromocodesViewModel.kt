package com.tobevpn.tv.presentation.promocodes

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.tobevpn.tv.data.remote.dto.PromocodeActivationResultDto
import com.tobevpn.tv.data.remote.dto.PromocodeErrorEnvelopeDto
import com.tobevpn.tv.data.remote.dto.PromocodeHistoryDto
import com.tobevpn.tv.data.repository.AuthRepository
import com.tobevpn.tv.data.repository.PromocodeAuthenticationException
import com.tobevpn.tv.data.repository.PromocodeRepository
import com.tobevpn.tv.data.repository.PromocodeResponseException
import com.tobevpn.tv.data.repository.PurchaseRepository
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.util.SafeDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class PromocodesViewModel @Inject constructor(
    private val promocodeRepository: PromocodeRepository,
    private val purchaseRepository: PurchaseRepository,
    authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PromocodesUiState())
    val uiState: StateFlow<PromocodesUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var activationJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.observeAuthState()
                .map { it is AuthState.Authenticated }
                .distinctUntilChanged()
                .collect { authenticated ->
                    loadJob?.cancel()
                    activationJob?.cancel()
                    _uiState.value = PromocodesUiState(
                        isAuthResolved = true,
                        isAuthenticated = authenticated,
                        isLoading = authenticated,
                    )
                    if (authenticated) requestRefresh()
                }
        }
    }

    fun refresh() = requestRefresh()

    private fun requestRefresh() {
        if (!_uiState.value.isAuthenticated || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val refreshFeedbackStartedAt = _uiState.value.history?.let {
                SystemClock.elapsedRealtime()
            }
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val (history, discount) = coroutineScope {
                    val history = async { promocodeRepository.getHistory(PAGE_SIZE, 0) }
                    val discount = async {
                        purchaseRepository.getPlans()?.effectiveDiscountPercent?.coerceIn(0, 100)
                    }
                    history.await() to discount.await()
                }
                awaitMinimumRefreshFeedback(refreshFeedbackStartedAt)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    history = history,
                    effectiveDiscountPercent = discount ?: 0,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SafeDiagnostics.warn(TAG, "Promocode history failed: ${SafeDiagnostics.failureCategory(error)}")
                awaitMinimumRefreshFeedback(refreshFeedbackStartedAt)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = error.toLoadError(),
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

    fun activate(rawCode: String) {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        if (code.isBlank() || !_uiState.value.isAuthenticated || activationJob?.isActive == true) return
        activationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isActivating = true,
                activationError = null,
                activationResult = null,
            )
            try {
                val result = promocodeRepository.activate(code)
                _uiState.value = _uiState.value.copy(
                    isActivating = false,
                    activationResult = result,
                )
                requestRefresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isActivating = false,
                    activationError = error.toActivationError(),
                )
            }
        }
    }

    fun clearActivationFeedback() {
        _uiState.value = _uiState.value.copy(
            activationError = null,
            activationResult = null,
        )
    }

    private companion object {
        const val TAG = "PromocodesViewModel"
        const val PAGE_SIZE = 50
        const val MIN_REFRESH_FEEDBACK_MS = 900L
    }
}

data class PromocodesUiState(
    val isAuthResolved: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val isActivating: Boolean = false,
    val history: PromocodeHistoryDto? = null,
    val effectiveDiscountPercent: Int = 0,
    val loadError: PromocodeLoadError? = null,
    val activationError: PromocodeActivationError? = null,
    val activationResult: PromocodeActivationResultDto? = null,
)

enum class PromocodeLoadError { NETWORK, AUTH_REQUIRED, UNAVAILABLE, UNKNOWN }

enum class PromocodeActivationError {
    NETWORK,
    NOT_FOUND,
    EXPIRED,
    ALREADY_ACTIVATED,
    ACTIVE_SUBSCRIPTION_REQUIRED,
    ALREADY_UNLIMITED,
    ACTIVATION_LIMIT_REACHED,
    NEW_USERS_ONLY,
    EXISTING_USERS_ONLY,
    INVITED_USERS_ONLY,
    NOT_AVAILABLE,
    AUTH_REQUIRED,
    TOO_MANY_REQUESTS,
    UNKNOWN,
}

private fun Exception.toLoadError(): PromocodeLoadError = when (this) {
    is IOException -> PromocodeLoadError.NETWORK
    is HttpException -> when (code()) {
        401, 403, 404 -> PromocodeLoadError.AUTH_REQUIRED
        in 500..599 -> PromocodeLoadError.UNAVAILABLE
        else -> PromocodeLoadError.UNKNOWN
    }
    is PromocodeResponseException -> PromocodeLoadError.UNAVAILABLE
    else -> PromocodeLoadError.UNKNOWN
}

private fun Exception.toActivationError(): PromocodeActivationError {
    if (this is IOException) return PromocodeActivationError.NETWORK
    if (this is PromocodeAuthenticationException) return PromocodeActivationError.AUTH_REQUIRED
    if (this !is HttpException) return PromocodeActivationError.UNKNOWN
    val detail = runCatching {
        Gson().fromJson(
            response()?.errorBody()?.string(),
            PromocodeErrorEnvelopeDto::class.java,
        )?.detail
    }.getOrNull()
    val serverCode = detail?.code.orEmpty().uppercase(Locale.ROOT)
    val message = detail?.message.orEmpty().lowercase(Locale.ROOT)
    return when {
        code() in setOf(401, 403) || serverCode == "USER_NOT_FOUND" ->
            PromocodeActivationError.AUTH_REQUIRED
        code() == 429 -> PromocodeActivationError.TOO_MANY_REQUESTS
        serverCode == "PROMOCODE_NOT_FOUND" || code() == 404 -> PromocodeActivationError.NOT_FOUND
        serverCode == "PROMOCODE_EXPIRED" -> PromocodeActivationError.EXPIRED
        serverCode == "PROMOCODE_ALREADY_ACTIVATED" -> PromocodeActivationError.ALREADY_ACTIVATED
        serverCode == "PROMOCODE_NOT_AVAILABLE" -> when {
            "active subscription required" in message ->
                PromocodeActivationError.ACTIVE_SUBSCRIPTION_REQUIRED
            "already unlimited" in message -> PromocodeActivationError.ALREADY_UNLIMITED
            "activation limit" in message -> PromocodeActivationError.ACTIVATION_LIMIT_REACHED
            "new users only" in message -> PromocodeActivationError.NEW_USERS_ONLY
            "existing users only" in message -> PromocodeActivationError.EXISTING_USERS_ONLY
            "invited users only" in message -> PromocodeActivationError.INVITED_USERS_ONLY
            else -> PromocodeActivationError.NOT_AVAILABLE
        }
        else -> PromocodeActivationError.UNKNOWN
    }
}
