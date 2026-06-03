package com.tobevpn.tv.data.remote

import com.tobevpn.tv.data.remote.dto.ApiResponse
import com.tobevpn.tv.data.remote.dto.AuthRequestDto
import com.tobevpn.tv.data.remote.dto.AuthRequestResponseDto
import com.tobevpn.tv.data.remote.dto.AuthStatusDto
import com.tobevpn.tv.data.remote.dto.CurrentPlanDto
import com.tobevpn.tv.data.remote.dto.DeviceRegisterRequestDto
import com.tobevpn.tv.data.remote.dto.DeviceUnlinkRequestDto
import com.tobevpn.tv.data.remote.dto.LinkedDevicesDto
import com.tobevpn.tv.data.remote.dto.PanelNodeDto
import com.tobevpn.tv.data.remote.dto.PanelResponse
import com.tobevpn.tv.data.remote.dto.PanelSubInfoDto
import com.tobevpn.tv.data.remote.dto.PanelUserDto
import com.tobevpn.tv.data.remote.dto.PurchasePlansDto
import com.tobevpn.tv.data.remote.dto.TvPairCreateRequestDto
import com.tobevpn.tv.data.remote.dto.TvPairCreateResponseDto
import com.tobevpn.tv.data.remote.dto.TvPairStatusDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * All endpoints below rely on device-session auth (Authorization: Bearer <access_token>).
 * Backend derives device_id / telegram_id / panel_user_uuid from the session — clients no
 * longer pass these in the body.
 */
interface BotApi {

    @POST("api/device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequestDto,
    ): ApiResponse<Unit>

    /** Unlinks a device by id. Backend resolves the account from the current session. */
    @POST("api/device/unlink")
    suspend fun unlinkDevice(
        @Body request: DeviceUnlinkRequestDto,
    ): ApiResponse<Unit>

    @POST("api/device/logout")
    suspend fun logoutDevice(): ApiResponse<Unit>

    @GET("api/devices")
    suspend fun getDevices(): ApiResponse<LinkedDevicesDto>

    @GET("api/subscription/current-plan")
    suspend fun getCurrentPlan(): ApiResponse<CurrentPlanDto>

    @POST("api/auth/request")
    suspend fun requestAuth(
        @Body request: AuthRequestDto,
    ): ApiResponse<AuthRequestResponseDto>

    @GET("api/auth/status")
    suspend fun checkAuthStatus(
        @Query("token") authToken: String,
    ): ApiResponse<AuthStatusDto>

    @POST("api/tv/pair/create")
    suspend fun createTvPairing(
        @Body request: TvPairCreateRequestDto = TvPairCreateRequestDto(),
    ): ApiResponse<TvPairCreateResponseDto>

    @GET("api/tv/pair/status")
    suspend fun checkTvPairingStatus(
        @Query("code") code: String,
    ): ApiResponse<TvPairStatusDto>

    // Panel proxy endpoints (panel token stays server-side)

    @GET("api/panel/user-by-telegram/{telegramId}")
    suspend fun getUserByTelegramId(
        @Path("telegramId") telegramId: Long,
    ): PanelResponse<List<PanelUserDto>>

    @GET("api/panel/nodes")
    suspend fun getNodes(): PanelResponse<List<PanelNodeDto>>

    @GET("api/panel/sub/{shortUuid}/info")
    suspend fun getSubscriptionInfo(
        @Path("shortUuid") shortUuid: String,
    ): PanelResponse<PanelSubInfoDto>

    // Purchase / tariff plans

    @GET("api/purchase/plans")
    suspend fun getPurchasePlans(): ApiResponse<PurchasePlansDto>
}
