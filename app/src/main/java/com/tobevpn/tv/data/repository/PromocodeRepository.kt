package com.tobevpn.tv.data.repository

import com.tobevpn.tv.data.local.dao.PendingPromocodeActivationDao
import com.tobevpn.tv.data.local.dao.SessionDao
import com.tobevpn.tv.data.local.entity.PendingPromocodeActivationEntity
import com.tobevpn.tv.data.remote.BootstrapManager
import com.tobevpn.tv.data.remote.BotApi
import com.tobevpn.tv.data.remote.dto.ApiResponse
import com.tobevpn.tv.data.remote.dto.PromocodeActivateRequestDto
import com.tobevpn.tv.data.remote.dto.PromocodeActivationResultDto
import com.tobevpn.tv.data.remote.dto.PromocodeHistoryDto
import com.tobevpn.tv.util.SafeDiagnostics
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

@Singleton
class PromocodeRepository @Inject constructor(
    private val botApi: BotApi,
    private val bootstrapManager: BootstrapManager,
    private val sessionDao: SessionDao,
    private val pendingActivationDao: PendingPromocodeActivationDao,
) {
    private val activationMutex = Mutex()

    suspend fun getHistory(limit: Int, offset: Int): PromocodeHistoryDto {
        val response = executeWithSessionRecovery {
            botApi.getAppliedPromocodes(limit, offset)
        }
        return response.data?.takeIf { response.success }
            ?: throw PromocodeResponseException()
    }

    suspend fun activate(code: String): PromocodeActivationResultDto = activationMutex.withLock {
        val normalizedCode = code.trim().uppercase(Locale.ROOT)
        require(normalizedCode.isNotBlank())
        bootstrapManager.ensureBootstrapped()
        val telegramId = sessionDao.getSession()
            ?.takeIf { it.authState == "AUTHENTICATED" }
            ?.telegramId
            ?: throw PromocodeAuthenticationException()
        val stored = pendingActivationDao.get(telegramId, normalizedCode)
        val requestId = stored?.requestId?.let(::canonicalUuid4OrNull)
            ?: UUID.randomUUID().toString()
        if (stored?.requestId != requestId) {
            pendingActivationDao.upsert(
                PendingPromocodeActivationEntity(
                    telegramId = telegramId,
                    code = normalizedCode,
                    requestId = requestId,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        try {
            val response = executeWithSessionRecovery {
                botApi.activatePromocode(
                    PromocodeActivateRequestDto(normalizedCode, requestId),
                )
            }
            val result = response.data?.takeIf { response.success }
                ?: throw PromocodeResponseException()
            if (result.requestId != null && result.requestId != requestId) {
                throw PromocodeResponseException()
            }
            clearPending(telegramId, normalizedCode, requestId)
            result
        } catch (error: Exception) {
            if (shouldDiscardPendingPromocodeAttempt((error as? HttpException)?.code())) {
                clearPending(telegramId, normalizedCode, requestId)
            }
            throw error
        }
    }

    private suspend fun clearPending(telegramId: Long, code: String, requestId: String) {
        try {
            pendingActivationDao.deleteIfMatches(telegramId, code, requestId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SafeDiagnostics.warn(
                TAG,
                "Promocode idempotency cleanup failed: ${SafeDiagnostics.failureCategory(error)}",
            )
        }
    }

    private suspend fun <T> executeWithSessionRecovery(
        request: suspend () -> ApiResponse<T>,
    ): ApiResponse<T> {
        bootstrapManager.ensureBootstrapped()
        return try {
            request()
        } catch (error: Exception) {
            if (error !is HttpException || error.code() !in setOf(401, 403)) throw error
            runCatching { bootstrapManager.bootstrap() }.getOrElse { throw error }
            request()
        }
    }

    private companion object {
        const val TAG = "PromocodeRepository"
    }
}

internal class PromocodeResponseException : IllegalStateException()
internal class PromocodeAuthenticationException : IllegalStateException()

internal fun canonicalUuid4OrNull(raw: String): String? {
    val parsed = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
    val canonical = parsed.toString()
    return canonical.takeIf { parsed.version() == 4 && canonical.equals(raw, ignoreCase = true) }
}

internal fun shouldDiscardPendingPromocodeAttempt(httpStatus: Int?): Boolean =
    httpStatus != null && httpStatus in 400..499 && httpStatus != 408
