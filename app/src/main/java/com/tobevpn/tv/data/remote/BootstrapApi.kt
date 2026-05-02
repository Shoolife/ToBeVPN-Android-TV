package com.tobevpn.tv.data.remote

import com.tobevpn.tv.data.remote.dto.ApiResponse
import com.tobevpn.tv.data.remote.dto.BootstrapRequestDto
import com.tobevpn.tv.data.remote.dto.RefreshRequestDto
import com.tobevpn.tv.data.remote.dto.SessionTokensDto
import retrofit2.http.Body
import retrofit2.http.POST

interface BootstrapApi {

    @POST("api/device/bootstrap")
    suspend fun bootstrap(
        @Body request: BootstrapRequestDto,
    ): ApiResponse<SessionTokensDto>

    @POST("api/device/refresh")
    suspend fun refresh(
        @Body request: RefreshRequestDto,
    ): ApiResponse<SessionTokensDto>
}
