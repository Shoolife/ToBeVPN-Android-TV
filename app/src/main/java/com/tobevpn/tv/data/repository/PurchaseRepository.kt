package com.tobevpn.tv.data.repository

import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.BootstrapManager
import com.tobevpn.tv.data.remote.dto.ApiResponse
import com.tobevpn.tv.data.remote.dto.PurchasePlansDto
import com.tobevpn.tv.util.SafeDiagnostics
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val botApi: BotApi,
    private val bootstrapManager: BootstrapManager,
) {
    /**
     * Fetches available purchase plans for the current device session.
     * Returns null on failure (network / non-success response).
     */
    suspend fun getPlans(): PurchasePlansDto? {
        return try {
            bootstrapManager.ensureBootstrapped()
            val response = getPurchasePlansWithSessionRecovery()
            if (response.success) response.data else null
        } catch (error: Exception) {
            SafeDiagnostics.warn(TAG, "Purchase plans failed: ${SafeDiagnostics.failureCategory(error)}")
            null
        }
    }

    private suspend fun getPurchasePlansWithSessionRecovery(): ApiResponse<PurchasePlansDto> {
        return try {
            botApi.getPurchasePlans()
        } catch (error: Exception) {
            if (error !is HttpException || error.code() !in setOf(401, 403)) throw error
            runCatching { bootstrapManager.bootstrap() }.getOrElse { throw error }
            botApi.getPurchasePlans()
        }
    }

    private companion object {
        const val TAG = "PurchaseRepository"
    }
}
