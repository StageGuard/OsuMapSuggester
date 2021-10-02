package me.stageguard.obms.database.model

import me.stageguard.obms.database.AddableTable
import org.ktorm.dsl.AssignmentsBuilder
import org.ktorm.entity.Entity
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.varchar

object WebVerificationStore : AddableTable<WebVerification>("web_verification") {
    val id = int("id").primaryKey().bindTo { it.id }
    val qq = long("qq").bindTo { it.qq }
    val token = varchar("token").bindTo { it.token }

    override fun <T : AssignmentsBuilder> T.mapElement(element: WebVerification) {
        set(qq, element.qq)
        set(token, element.token)
    }
}

interface WebVerification : Entity<WebVerification> {
    companion object : Entity.Factory<WebVerification>()
    var id: Int
    var qq: Long
    var token: String
}