package me.stageguard.obms.frontend

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import jakarta.annotation.Resource
import kotlinx.coroutines.*
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.database.Database
import me.stageguard.obms.frontend.route.*
import me.stageguard.obms.osu.api.OsuHttpClient
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.utils.info
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.coroutines.CoroutineContext

@Component
class NettyHttpServer : CoroutineScope {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${frontend.host}")
    private lateinit var host: String
    @Value("\${frontend.port}")
    private lateinit var _port: String
    private val port by lazy { _port.toInt() }

    @Value("\${osuAuth.clientId}")
    private lateinit var _clientId: String
    private val clientId by lazy { _clientId.toInt() }
    @Value("\${osuAuth.authCallbackBaseUrl}")
    private lateinit var authCallbackBaseUrl: String


    @Resource
    private lateinit var osuWebApi: OsuWebApi
    @Resource
    private lateinit var osuHttpClient: OsuHttpClient
    @Resource
    private lateinit var oAuthManager: OAuthManager
    @Resource
    private lateinit var database: Database

    override val coroutineContext: CoroutineContext
        get() = OsuMapSuggester.scope.coroutineContext + CoroutineExceptionHandler {
            _, throwable -> logger.error("Http server occurred an error", throwable)
        }

    private lateinit var server: NettyApplicationEngine

    @Suppress("HttpUrlsUsage")
    fun start() {
        server = embeddedServer(Netty, applicationEngineEnvironment {
            parentCoroutineContext = coroutineContext
            log = logger
            connector {
                this.host = this@NettyHttpServer.host
                this.port = this@NettyHttpServer.port
            }
            module {
                //auth 相关
                authorize(clientId, authCallbackBaseUrl)
                authCallback(oAuthManager, database, authCallbackBaseUrl)
                //便捷的 osu!direct
                importBeatmap()
                //前端相关
                frontendResource()
                ruleset(oAuthManager, osuHttpClient, osuWebApi, database)
            }
        }).also { s -> launch(CoroutineName("NettyServer")) {
            (s as ApplicationEngine).start(false)
            logger.info { "Frontend server respond at http://$host:$port" }
        } }
    }

    fun stop() = server.stop(500, 1000)
}
