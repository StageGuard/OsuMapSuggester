package me.stageguard.obms.osu.api.oauth

import me.stageguard.obms.RefactoredException
import me.stageguard.osu.api.dto.*

sealed class OAuthResult {
    class Succeed(
        val type: Int,
        val additionalData: List<String>,
        val tokenResponse: GetAccessTokenResponse,
        val userResponse: OsuUser
    ) : OAuthResult()

    class Failed(
        val exception: RefactoredException
    ) : OAuthResult()
}
