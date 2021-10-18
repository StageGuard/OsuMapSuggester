package me.stageguard.obms.osu.api.oauth

import me.stageguard.obms.RefactoredException
import me.stageguard.obms.osu.api.dto.GetAccessTokenResponseDTO
import me.stageguard.obms.osu.api.dto.GetUserDTO

sealed class OAuthResult {
    class Succeed(
        val type: Int,
        val additionalData: List<String>,
        val tokenResponse: GetAccessTokenResponseDTO,
        val userResponse: GetUserDTO
    ): OAuthResult()
    class Failed(
        val exception: RefactoredException
    ) : OAuthResult()
}
