package com.tobevpn.tv.data.remote

import com.tobevpn.tv.data.remote.dto.GithubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

/**
 * Read-only client for GitHub Releases API used by the in-app updater.
 * The Accept header pins API v3 — without it GitHub may switch to a newer
 * schema in the future and break our parser.
 */
interface GithubReleasesApi {

    @Headers("Accept: application/vnd.github+json")
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GithubReleaseDto
}
