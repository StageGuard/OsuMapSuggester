package me.stageguard.osu.api

import io.ktor.client.plugins.auth.providers.*

public interface OsuWebApiTokenHolder {

    public suspend fun loadTokens(): BearerTokens?

    public suspend fun saveTokens(tokens: BearerTokens)

    public fun getCode(state: String): String
}