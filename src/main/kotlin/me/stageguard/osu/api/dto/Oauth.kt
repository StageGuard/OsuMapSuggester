package me.stageguard.osu.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
public data class GetAccessTokenRequest(
    @SerialName("client_id")
    val clientId: Int,
    @SerialName("client_secret")
    val clientSecret: String,
    @SerialName("code")
    val code: String,
    @SerialName("grant_type")
    val grantType: String,
    @SerialName("redirect_uri")
    val redirectUri: String
)

@Serializable
public data class RefreshTokenRequest(
    @SerialName("client_id")
    val clientId: Int,
    @SerialName("client_secret")
    val clientSecret: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("grant_type")
    val grantType: String,
    @SerialName("redirect_uri")
    val redirectUri: String
)

@Serializable
public data class GetAccessTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("token_type")
    val tokenType: String
)