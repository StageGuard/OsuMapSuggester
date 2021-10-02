package me.stageguard.obms.frontend.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebVerificationResponseDTO(
    //  0: success
    //  1: cookie not found
    //  2: not bind (not a valid qq) -> 用户没有绑定过 QQ 而验证(qq = -1)
    //  3: not bind (a valid qq) -> 用户之前解绑了而验证
    // -1: internal error
    val result: Int,
    val qq: Long = -1,
    val osuId: Int = -1,
    val osuName: String = "",
    val errorMessage: String = ""
)

@Serializable
data class WebVerificationRequestDTO(
    val token: String
)

@Serializable
data class CreateVerificationLinkRequestDTO(
    @SerialName("callback")
    val callbackPath: String
)

@Serializable
data class CreateVerificationLinkResponseDTO(
    //  0: success
    // -1: internal error
    val result: Int,
    val link: String = "",
    val errorMessage: String = ""
)