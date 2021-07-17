package me.stageguard.obms

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.database.Database
import me.stageguard.obms.frontend.NettyHttpServer
import me.stageguard.obms.utils.retry
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.extension.PluginComponentStorage
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.utils.info

object OsuMapSuggester : KotlinPlugin(
    JvmPluginDescription(
        id = "me.stageguard.obms.OsuMapSuggester",
        name = "OsuMapSuggester",
        version = "1.0-SNAPSHOT",
    ) { author("StageGuard") }
) {
    lateinit var botInstance: Bot
    override fun onEnable() {
        logger.info { "Plugin loaded." }
        logger.info { "Connecting to database ${PluginConfig.database.address}." }
        val connectionAsync = async {
            retry(5, exceptionBlock = {
                logger.info { "Failed to connect database: $it." }
                logger.info { "Retry to connect database in 10 seconds." }
                delay(10000L)
            }) {
                Database.connect()
                Database.isConnected()
            }
        }
        launch {
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
        NettyHttpServer.stop()
    }
}