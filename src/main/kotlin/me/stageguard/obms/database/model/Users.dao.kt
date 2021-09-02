package me.stageguard.obms.database.model

import me.stageguard.obms.database.AddableTable
import me.stageguard.obms.database.Database
import org.ktorm.dsl.*
import org.ktorm.entity.Entity
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.varchar


object OsuUserInfo : AddableTable<User>("users") {
    val id = int("id").primaryKey()
    val qq = long("qq").bindTo { it.qq }
    val osuId = int("osuId").bindTo { it.osuId }
    val osuName = varchar("osuName").bindTo { it.osuName }
    val token = varchar("token").bindTo { it.token }
    val tokenExpireUnixSecond = long("tokenExpiresUnixSecond").bindTo { it.tokenExpireUnixSecond }
    val refreshToken = varchar("refreshToken").bindTo { it.refreshToken }

    suspend fun getOsuId(qq: Long) = Database.query { db ->
        db.sequenceOf(this@OsuUserInfo).find { it.qq eq qq } ?.osuId
    }

    suspend fun getOsuIdAndName(qq: Long) = Database.query { db ->
        db.sequenceOf(this@OsuUserInfo).find { it.qq eq qq } ?. osuId to osuName
    }

    override fun <T : AssignmentsBuilder> T.mapElement(element: User) {
        set(qq, element.qq)
        set(osuId, element.osuId)
        set(osuName, element.osuName)
        set(token, element.token)
        set(tokenExpireUnixSecond, element.tokenExpireUnixSecond)
        set(refreshToken, element.refreshToken)
    }
}

interface User : Entity<User> {
    companion object : Entity.Factory<User>()
    var qq: Long
    var osuId: Int
    var osuName: String
    var token: String
    var tokenExpireUnixSecond: Long
    var refreshToken: String
}