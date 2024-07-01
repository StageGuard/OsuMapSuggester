package me.stageguard.obms

import jakarta.annotation.Resource
import kotlinx.coroutines.*
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.DatabaseLeaker
import me.stageguard.obms.frontend.NettyHttpServer
import me.stageguard.obms.osu.api.OsuHttpClient
import me.stageguard.obms.script.ScriptEnvironHost
import me.stageguard.obms.utils.error
import me.stageguard.obms.utils.exportStaticResourcesToDataFolder
import me.stageguard.obms.utils.info
import me.stageguard.obms.utils.retry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import kotlin.properties.Delegates

@Component
@SpringBootApplication
open class OsuMapSuggester: ApplicationRunner, DisposableBean {
    @Resource
    private lateinit var osuHttpClient: OsuHttpClient
    @Resource
    private lateinit var database: Database
    @Resource
    private lateinit var databaseLeaker: DatabaseLeaker
    @Resource
    private lateinit var httpServer: NettyHttpServer

    @Value("\${qq}")
    private lateinit var qq: String

    override fun run(args: ApplicationArguments?) {
        val connectionAsync = scope.async {
            retry(5, exceptionBlock = {
                logger.error { "Failed to connect database: $it." }
                logger.error { "Retry to connect database in 10 seconds." }
                delay(10000L)
            }) {
                database.connect()
                database.isConnected()
            }
        }
        scope.launch {
            logger.info { "Exporting static resources..." }
            exportStaticResourcesToDataFolder()
            ScriptEnvironHost.init()
            botQq = qq.toLong()
            logger.info { "Waiting target Bot $qq goes online..." }
            val connectionResult = connectionAsync.await()
            if(connectionResult.isSuccess) {
                databaseLeaker.resolveInstance()
                httpServer.start()
            } else {
                logger.error("Database is not connected, all services will not start.")
            }
        }
    }

    override fun destroy() {
        logger.info { "Stopping services..." }
        database.close()
        httpServer.stop()
        osuHttpClient.closeClient()
    }

    companion object {
        lateinit var scope: CoroutineScope
        var botQq by Delegates.notNull<Long>()

        val dataFolder = File(".")

        val logger: Logger = synchronized(System.err) {
            try {
                System.setErr(PrintStream(object : OutputStream() {
                    override fun write(b: Int) {}
                    override fun write(b: ByteArray) {}
                    override fun write(b: ByteArray, off: Int, len: Int) {}
                }))
                LoggerFactory.getLogger(this::class.java)
            } finally { System.setErr(System.err) }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            runApplication<OsuMapSuggester>()
        }
    }
}
