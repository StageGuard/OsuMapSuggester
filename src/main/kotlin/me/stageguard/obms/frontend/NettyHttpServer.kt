package me.stageguard.obms.frontend

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import me.stageguard.obms.frontend.route.authCallback
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.frontend.route.importBeatmap
import kotlin.coroutines.CoroutineContext

object NettyHttpServer : CoroutineScope {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext
        get() = CoroutineExceptionHandler {
                _, throwable -> OsuMapSuggester.logger.error("Http server occurred an error", throwable)
        }

    private lateinit var server: NettyApplicationEngine

    fun start(host: String, port: Int) {
        server = embeddedServer(Netty, applicationEngineEnvironment {
            parentCoroutineContext = coroutineContext
            log = OsuMapSuggester.moduleLogger
            connector {
                this.host = host
                this.port = port
            }
            module {
                authCallback()
                importBeatmap()
            }
        }).also { s -> OsuMapSuggester.launch(CoroutineName("NettyServer")) {
            (s as ApplicationEngine).start(false)
        } }
    }

    fun stop() = server.stop(500, 1000)
}