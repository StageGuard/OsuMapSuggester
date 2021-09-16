package me.stageguard.obms.database

import org.ktorm.dsl.*
import org.ktorm.entity.Entity
import org.ktorm.schema.Column
import org.ktorm.schema.Table

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class AddableTable<E: Entity<E>>(name: String) : Table<E>(name) {
    abstract fun <T : AssignmentsBuilder> T.mapElement(element: E)

    suspend fun insert(item: E) {
        Database.query { db -> db.insert(this@AddableTable) { mapElement(item) } }
    }

    suspend fun <T> batchInsert(items: Collection<T>, tMapper: (T) -> E) = Database.query { db ->
        db.batchInsert(this@AddableTable) {
            items.forEach { e -> item { mapElement(tMapper(e)) } }
        }
    }

    suspend fun batchInsert(items: Collection<E>) = batchInsert(items) { it }

    suspend fun batchUpdate(statement: BatchUpdateStatementBuilder<*>.() -> Unit) = Database.query { db ->
        db.batchUpdate(this@AddableTable) {
            statement(this)
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