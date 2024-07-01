package me.stageguard.obms.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.annotation.Resource
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.database.model.*
import me.stageguard.obms.utils.error
import me.stageguard.obms.utils.info
import me.stageguard.obms.utils.retry
import me.stageguard.obms.utils.warning
import okhttp3.internal.closeQuietly
import org.ktorm.database.Database
import org.ktorm.database.Transaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.lang.IllegalArgumentException
import java.sql.SQLException

@Component
class DatabaseLeaker {
    @Resource
    lateinit var database: me.stageguard.obms.database.Database


    fun resolveInstance() {
        INSTANCE = this
    }

    companion object {
        var INSTANCE: DatabaseLeaker? = null
    }
}

@Component
class Database {
    private val logger = LoggerFactory.getLogger(this::class.java)

    sealed class ConnectionStatus {
        data object CONNECTED : ConnectionStatus()
        data object DISCONNECTED : ConnectionStatus()
    }

    @Value("\${database.address}")
    private lateinit var address: String

    @Value("\${database.port}")
    private lateinit var _port: String
    private val port by lazy { _port.toInt() }

    @Value("\${database.user}")
    private lateinit var user: String

    @Value("\${database.password}")
    private lateinit var password: String

    @Value("\${database.table}")
    private lateinit var table: String

    @Value("\${database.maximumPoolSize}")
    private lateinit var _maximumPoolSize: String
    private val maximumPoolSize by lazy { _maximumPoolSize.toInt() }

    private lateinit var db : Database
    private lateinit var hikariSource : HikariDataSource
    private var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    suspend fun <T> query(block: suspend Transaction.(Database) -> T) : T? = if(connectionStatus == ConnectionStatus.DISCONNECTED) {
        logger.error { "Database is disconnected and the query operation will not be completed." }
        null
    } else db.useTransaction { t ->
        retry(3, exceptionBlock = {
            if(it is SQLException && it.toString().contains("Connection is closed")) {
                logger.warning { "Database connection is closed, reconnecting..." }
                close()
                connect()
            } else throw it
        }) {
            if(isConnected()) {
                block(t, db)
            } else {
                throw SQLException("Connection is closed")
            }
        }.getOrThrow()
    }

    fun connect() {
        logger.info { "Connecting to database $address." }
        db = Database.connect(hikariDataSourceProvider().also { hikariSource = it })
        connectionStatus = ConnectionStatus.CONNECTED
        initDatabase()
        logger.info { "Database $table is connected." }
    }

    fun isConnected() = connectionStatus == ConnectionStatus.CONNECTED

    private fun initDatabase() {
        // ktorm doesn't support creating database schema.
        db.useConnection { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `${OsuUserInfo.tableName}` (
                    `id` int NOT NULL AUTO_INCREMENT,
                    `osuId` int NOT NULL,
                    `osuName` varchar(16) NOT NULL,
                    `qq` bigint NOT NULL,
                    `token` varchar(1500) NOT NULL,
                    `tokenExpiresUnixSecond` bigint NOT NULL,
                    `refreshToken` varchar(1500) NOT NULL,
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `users_qq_unique` (`qq`)
                );
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `${BeatmapSkillTable.tableName}` (
                	`id` INT NOT NULL AUTO_INCREMENT,
                	`bid` INT NOT NULL,
                	`stars` DOUBLE NOT NULL,
                    `bpm` DOUBLE NOT NULL,
                    `length` INT NOT NULL,
                    `circle_size` DOUBLE NOT NULL,
                    `hp_drain` DOUBLE NOT NULL,
                    `approaching_rate` DOUBLE NOT NULL,
                    `overall_difficulty` DOUBLE NOT NULL,
                	`jump` DOUBLE NOT NULL,
                	`flow` DOUBLE NOT NULL,
                	`speed` DOUBLE NOT NULL,
                	`stamina` DOUBLE NOT NULL,
                	`precision` DOUBLE NOT NULL,
                	`complexity` DOUBLE NOT NULL,
                	PRIMARY KEY (`id`),
                    UNIQUE KEY `beatmap_skills_unique` (`bid`)
                );
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `${RulesetCollection.tableName}` (
                    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                    `name` VARCHAR(200) NOT NULL COMMENT '规则名称',
                    `triggers` VARCHAR(1500) NOT NULL COMMENT '触发条件',
                    `authorQq` BIGINT NOT NULL COMMENT '添加者 QQ',
                    `expression` VARCHAR(1500) NOT NULL COMMENT 'JavaScript条件表达式',
                    `priority` INT NOT NULL COMMENT '优先级',
                    `addDate` DATE NOT NULL COMMENT '添加日期',
                    `lastEdited` DATE NOT NULL COMMENT '修改日期',
                    `enabled` INT NOT NULL COMMENT '是否启用',
                    `lastError` VARCHAR(1500) NOT NULL COMMENT '最后一次错误信息',
                    PRIMARY KEY (`id`)
                );
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `${WebVerificationStore.tableName}` (
                    `id` INT NOT NULL AUTO_INCREMENT,
                    `qq` bigint NOT NULL,
                    `osuId` INT NOT NULL,
                    `token` VARCHAR(64) NOT NULL,
                    PRIMARY KEY (`id`)
                );
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `${BeatmapCommentTable.tableName}` (
                    `id` INT NOT NULL AUTO_INCREMENT,
                    `bid` INT NOT NULL,
                    `rulesetId` INT NOT NULL,
                    `commenterQq` bigint NOT NULL,
                    `content` VARCHAR(256) NOT NULL,
                    PRIMARY KEY (`id`)
                );
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `${RulesetCommentTable.tableName}` (
                    `id` INT NOT NULL AUTO_INCREMENT,
                    `rulesetId` INT NOT NULL,
                    `commenterQq` bigint NOT NULL,
                    `positive` INT NOT NULL,
                    PRIMARY KEY (`id`)
                );
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `${ProfilePanelStyleTable.tableName}` (
                    `id` INT NOT NULL AUTO_INCREMENT,
                    `qq` bigint NOT NULL,
                    `type` INT NOT NULL,
                    `blurRadius` DOUBLE NOT NULL,
                    `backgroundAlpha` DOUBLE NOT NULL,
                    `cardBackgroundAlpha` DOUBLE NOT NULL,
                    `useCustomBG` boolean NOT NULL,
                    PRIMARY KEY (`id`)
                );
            """.trimIndent())
        }
    }

    fun close() {
        connectionStatus = ConnectionStatus.DISCONNECTED
        hikariSource.closeQuietly()
    }


    private fun hikariDataSourceProvider() : HikariDataSource = HikariDataSource(HikariConfig().apply {
        when {
            address == "" -> throw IllegalArgumentException("Database address is not set in config file application.yml.")
            table == "" -> {
                logger.warning { "Database table is not set in application.yml and now it will be default value 'sctimetabledb'." }
                table = "osu!beatmap suggester"
            }
            port !in 1024..65535 -> throw IllegalArgumentException("Database port is invalid.")
            user == "" -> throw IllegalArgumentException("Database user is not set in config file application.yml.")
            password == "" -> throw IllegalArgumentException("Database password is not set in config file application.yml.")
        }
        jdbcUrl         = "jdbc:mysql://${this@Database.address}:${this@Database.port}/${this@Database.table}"
        driverClassName = "com.mysql.cj.jdbc.Driver"
        username        = this@Database.user
        password        = this@Database.password
        maximumPoolSize = this@Database.maximumPoolSize
        poolName        = "OBMSDB Pool"
    })

}