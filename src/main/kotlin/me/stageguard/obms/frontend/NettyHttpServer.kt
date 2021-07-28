package me.stageguard.obms.frontend

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.frontend.route.authCallback
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.console.plugin.PluginManager
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.description
import net.mamoe.mirai.utils.info
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.io.PrintStream
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
            }
        }).also { OsuMapSuggester.launch {
            (it as ApplicationEngine).start(false)
        } }
    }

    fun stop() = server.stop(500, 1000)
}