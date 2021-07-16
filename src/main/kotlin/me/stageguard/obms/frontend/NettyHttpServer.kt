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
                _, throwable -> logger.error("Http server occurred an error", throwable)
        }

    private lateinit var server: NettyApplicationEngine

    private val logger = if (PluginManager.plugins.any {
            it.description.id == "net.mamoe.mirai.mirai-slf4j-bridge"
        } || runCatching {
            // library mode
            Class.forName(
                "org.slf4j.impl.StaticLoggerBinder",
                false,
                Logger::class.java.classLoader
            )
        }.isSuccess)
        LoggerFactory.getLogger(OsuMapSuggester.description.name)
    else synchronized(System.err) {
        try {
            System.setErr(PrintStream(object : OutputStream() {
                override fun write(b: Int) {}
                override fun write(b: ByteArray) {}
                override fun write(b: ByteArray, off: Int, len: Int) {}
            }))
            LoggerFactory.getLogger(OsuMapSuggester.description.name)
        } finally { System.setErr(System.err) }
    }


    fun start(host: String, port: Int) {
        server = embeddedServer(Netty, applicationEngineEnvironment {
            parentCoroutineContext = coroutineContext
            log = logger
            connector {
                this.host = host
                this.port = port
            }
            module {
                authCallback()
            }
        }).also { OsuMapSuggester.launch {
            (it as ApplicationEngine).start(true)
            OsuMapSuggester.logger.info { "Http frontend server started at http://${PluginConfig.frontend.host}:${PluginConfig.frontend.port}" }
        } }
    }

    fun stop() = server.stop(5000, 5000)
}