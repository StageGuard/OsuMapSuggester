package me.stageguard.obms.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.database.model.User
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.verbose
import net.mamoe.mirai.utils.warning
import okhttp3.internal.closeQuietly
import org.ktorm.database.Database
import org.ktorm.database.Transaction
import java.lang.IllegalArgumentException

object Database {

    sealed class ConnectionStatus {
        object CONNECTED : ConnectionStatus()
        object DISCONNECTED : ConnectionStatus()
    }

    private lateinit var db : Database
    private lateinit var hikariSource : HikariDataSource
    private var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    suspend fun <T> query(block: suspend Transaction.(Database) -> T) : T? = if(connectionStatus == ConnectionStatus.DISCONNECTED) {
        OsuMapSuggester.logger.error { "Database is disconnected and the query operation will not be completed." }
        null
    } else db.useTransaction { block(it, db) }

    fun connect() {
        db = Database.connect(hikariDataSourceProvider().also { hikariSource = it })
        connectionStatus = ConnectionStatus.CONNECTED
        initDatabase()
        OsuMapSuggester.logger.info { "Database ${PluginConfig.database.table} is connected." }
    }

    fun isConnected() = connectionStatus == ConnectionStatus.CONNECTED

    private fun initDatabase() {

    }

    fun close() {
        connectionStatus = ConnectionStatus.DISCONNECTED
        hikariSource.closeQuietly()
    }

    private fun hikariDataSourceProvider() : HikariDataSource = HikariDataSource(HikariConfig().apply {
        when {
            PluginConfig.database.address == "" -> throw IllegalArgumentException("Database address is not set in config file ${PluginConfig.saveName}.")
            PluginConfig.database.table == "" -> {
                OsuMapSuggester.logger.warning { "Database table is not set in config file ${PluginConfig.saveName} and now it will be default value 'sctimetabledb'." }
                PluginConfig.database.table = "sctimetabledb"
            }
            PluginConfig.database.port !in 1024..65535 -> throw IllegalArgumentException("Database port is invalid.")
            PluginConfig.database.user == "" -> throw IllegalArgumentException("Database user is not set in config file ${PluginConfig.saveName}.")
            PluginConfig.database.password == "" -> throw IllegalArgumentException("Database password is not set in config file ${PluginConfig.saveName}.")
            PluginConfig.database.maximumPoolSize == null -> {
                OsuMapSuggester.logger.warning { "Database maximumPoolSize is not set in config file ${PluginConfig.saveName} and now it will be default value 10." }
                PluginConfig.database.maximumPoolSize = 10
            }
        }
        jdbcUrl         = "jdbc:mysql://${PluginConfig.database.address}:${PluginConfig.database.port}/${PluginConfig.database.table}"
        driverClassName = "com.mysql.cj.jdbc.Driver"
        username        = PluginConfig.database.user
        password        = PluginConfig.database.password
        maximumPoolSize = PluginConfig.database.maximumPoolSize!!
    })

}