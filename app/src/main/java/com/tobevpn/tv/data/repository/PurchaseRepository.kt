package com.tobevpn.tv.data.repository

import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.dto.PurchasePlansDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val botApi: BotApi,
) {
    /**
     * Fetches available purchase plans for the current device session.
     * Returns null on failure (network / non-success response).
     */
    suspend fun getPlans(): PurchasePlansDto? {
        return try {
            val response = botApi.getPurchasePlans()
            if (response.success) response.data else null
        } catch (_: Exception) {
            null
        }
    }
}
