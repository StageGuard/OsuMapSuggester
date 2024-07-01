package me.stageguard.obms.database

import jakarta.annotation.Resource
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.utils.warning
import org.ktorm.dsl.*
import org.ktorm.entity.Entity
import org.ktorm.schema.Column
import org.ktorm.schema.Table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class AddableTable<E: Entity<E>>(name: String) : Table<E>(name) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    abstract fun <T : AssignmentsBuilder> T.mapElement(element: E)

    suspend fun insert(item: E) {
        val leaker = DatabaseLeaker.INSTANCE
        if (leaker == null) {
            logger.warning { "database is not initialized, insert operation cannot be completed." }
            return
        }
        leaker.database.query { db -> db.insert(this@AddableTable) { mapElement(item) } }
    }

    suspend fun <T> batchInsert(items: Collection<T>, tMapper: (T) -> E): IntArray? {
        val leaker = DatabaseLeaker.INSTANCE
        if (leaker == null) {
            logger.warning { "database is not initialized, batchInsert operation cannot be completed." }
            return null
        }
        return leaker.database.query { db ->
            db.batchInsert(this@AddableTable) {
                items.forEach { e -> item { mapElement(tMapper(e)) } }
            }
        }
    }

    suspend fun batchInsert(items: Collection<E>) = batchInsert(items) { it }

    suspend fun batchUpdate(statement: BatchUpdateStatementBuilder<*>.() -> Unit): IntArray? {
        val leaker = DatabaseLeaker.INSTANCE
        if (leaker == null) {
            logger.warning { "database is not initialized, insert operation cannot be completed." }
            return null
        }
        return leaker.database.query { db ->
            db.batchUpdate(this@AddableTable) {
                statement(this)
            }
        }
    }

    suspend fun <T, C : Any> batchUpdate1(
        items: Collection<T>, columnToIdentify: Column<C>,
        whereExpr: T.() -> C, tMapper: (T) -> E
    ) = batchUpdate {
        items.forEach { e ->
            item {
                mapElement(tMapper(e))
                where { columnToIdentify eq whereExpr(e) }
            }
        }
    }
}