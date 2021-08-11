package me.stageguard.obms

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.api.osu.OsuWebApi
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.database.Database
import me.stageguard.obms.frontend.NettyHttpServer
import me.stageguard.obms.utils.exportStaticResourcesToDataFolder
import me.stageguard.obms.utils.retry
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.extension.PluginComponentStorage
import net.mamoe.mirai.console.plugin.PluginManager
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.description
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.io.PrintStream

object OsuMapSuggester : KotlinPlugin(
    JvmPluginDescription(
        id = "me.stageguard.obms.OsuMapSuggester",
        name = "OsuMapSuggester",
        version = "1.3",
    ) { author("StageGuard") }
) {
    val moduleLogger: Logger = if (PluginManager.plugins.any {
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

    lateinit var botInstance: Bot
    override fun onEnable() {
        logger.info { "Connecting to database ${PluginConfig.database.address}." }
        val connectionAsync = async {
            retry(5, exceptionBlock = {
                logger.error { "Failed to connect database: $it." }
                logger.error { "Retry to connect database in 10 seconds." }
                delay(10000L)
            }) {
                Database.connect()
                Database.isConnected()
            }
        }
        launch {
            logger.info { "Exporting static resources..." }
            exportStaticResourcesToDataFolder()
            val connectionResult = connectionAsync.await()
            if(connectionResult.isSuccess) {
                NettyHttpServer.start(PluginConfig.frontend.host, PluginConfig.frontend.port)
                logger.info { "Waiting target Bot ${PluginConfig.qq} goes online..." }
                GlobalEventChannel.filter {
                    it is BotOnlineEvent && it.bot.id == PluginConfig.qq
                }.subscribeOnce<BotOnlineEvent> {
                    MessageRoute.subscribeMessages(this.bot.also {
                        this@OsuMapSuggester.botInstance = it
                    })
                }
            } else {
                logger.error("Database is not connected, all services will not start.")
            }
        }
    }

    override fun PluginComponentStorage.onLoad() {
        PluginConfig.reload()
    }

    override fun onDisable() {
        logger.info { "Stopping services..." }
        Database.close()
        NettyHttpServer.stop()
        OsuWebApi.closeClient()
    }
}