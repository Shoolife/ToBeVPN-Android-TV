package com.tobevpn.tv.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Subset of the GitHub Releases API response we care about.
 *
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/releases
 * Docs:     https://docs.github.com/rest/releases/releases#list-releases
 *
 * Unauthenticated requests are limited to 60/hour per IP — well above what we
 * need (one check per app launch). Authenticating would just complicate the
 * client and add no value here.
 */
data class GithubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    // Gson does not invoke Kotlin's default argument for an omitted key.
    val assets: List<GithubAssetDto>? = null,
)

data class GithubAssetDto(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    val size: Long = 0L,
    @SerializedName("content_type") val contentType: String? = null,
)
