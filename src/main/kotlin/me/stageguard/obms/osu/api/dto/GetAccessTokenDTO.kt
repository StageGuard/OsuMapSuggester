package me.stageguard.obms.osu.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class GetAccessTokenRequestDTO(
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
data class RefreshTokenRequestDTO(
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
data class GetAccessTokenResponseDTO(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("token_type")
    val tokenType: String
)