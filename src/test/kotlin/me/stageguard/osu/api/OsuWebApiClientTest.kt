package me.stageguard.osu.api

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import java.util.UUID
import kotlin.test.*

internal class OsuWebApiClientTest {
    private val holder = object : OsuWebApiTokenHolder {
        override suspend fun loadTokens(): BearerTokens? {
            val file = File("run/tokens.properties")
            val properties = Properties()
            return try {
                file.inputStream().use { input -> properties.load(input) }
                BearerTokens(
                    accessToken = properties["access_token"].toString(),
                    refreshToken = properties["refresh_token"].toString()
                )
            } catch (cause: Exception) {
                null
            }
        }

        override suspend fun saveTokens(tokens: BearerTokens) {
            val properties = Properties()
            properties.setProperty("access_token", tokens.accessToken)
            properties.setProperty("refresh_token", tokens.refreshToken)
            val file = File("run/tokens.properties")
            file.parentFile.mkdirs()
            file.outputStream().use { output -> properties.store(output, "BearerTokens") }
        }

        override fun getCode(state: String): String {
            TODO("Not yet implemented")
        }
    }
    private val config = object : OsuWebApiClientConfig {
        val properties = Properties()
        init {
            val file = File("run/config.properties")
            file.inputStream().use { input -> properties.load(input) }
        }

        override val clientId: Int = properties["client_id"].toString().toInt()
        override val secret: String = properties["secret"].toString()
        override val baseUrl: String = properties["base_url"].toString()
        override val v1ApiKey: String get() = TODO("Not yet implemented")
        override val timeout: Long = 30_000

    }
    private val client = OsuWebApiClient(holder = holder, config = config)

    @Test
    fun buildAuthorizationCodeUrl() {
        val url = client.bindAuthorizationCodeUrl(state = UUID.randomUUID().toString())
        assertEquals("code", url.parameters["response_type"])
        assertEquals(config.clientId.toString(), url.parameters["client_id"])
        assertEquals(config.baseUrl + "/authCallback", url.parameters["redirect_uri"])
    }

    @Test
    fun me(): Unit = runBlocking {
        val user = client.me()
        assertNotEquals(0, user.id)
    }
}