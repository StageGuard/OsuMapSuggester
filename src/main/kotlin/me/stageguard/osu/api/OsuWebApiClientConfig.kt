package me.stageguard.osu.api

public interface OsuWebApiClientConfig {
    public val clientId: Int
    public val secret: String
    public val baseUrl: String

    public val v1ApiKey: String

    public val timeout: Long
}