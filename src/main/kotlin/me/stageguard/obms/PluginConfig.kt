package me.stageguard.obms

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

object PluginConfig : AutoSavePluginConfig("OsuMapSuggester.Config") {
    val qq by value(1234567890L)
    val database by value<DatabaseConfig>()
    val osuAuth by value<OsuAuthConfig>()
    val frontend by value<FrontendConfig>()
    val clientProxy by value<String>("")
    val helpLink by value("https://github.com/StageGuard/OsuMapSuggester/wiki")
}

@Serializable
data class DatabaseConfig(
    val address: String = "localhost",
    val port: Int = 3306,
    val user: String = "root",
    val password: String = "testpwd",
    var table: String = "osu!beatmap suggester",
    var maximumPoolSize: Int? = 10
)

@Serializable
data class OsuAuthConfig(
    val clientId: Int = 0,
    val secret: String = "",
    val authCallbackBaseUrl: String = "http://localhost:8081",
    val v1ApiKey: String = ""
)

@Serializable
data class FrontendConfig(
    val host: String = "localhost",
    val port: Int = 8081
)
