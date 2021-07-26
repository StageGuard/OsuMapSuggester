package me.stageguard.obms.database.model

import me.stageguard.obms.database.Database
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column


object OsuUserInfo : IntIdTable("users") {
    val qq: Column<Long> = long("qq").uniqueIndex()
    val osuId: Column<Int> = integer("osuId")
    val osuName: Column<String> = varchar("osuName", 16)
    val token: Column<String> = varchar("token", 1500)
    val tokenExpireUnixSecond: Column<Long> = long("tokenExpiresUnixSecond")
    val refreshToken: Column<String> = varchar("refreshToken", 1500)
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(OsuUserInfo)
    var osuId by OsuUserInfo.osuId
    var osuName by OsuUserInfo.osuName
    var qq by OsuUserInfo.qq
    var token by OsuUserInfo.token
    var tokenExpireUnixSecond by OsuUserInfo.tokenExpireUnixSecond
    var refreshToken by OsuUserInfo.refreshToken
}

suspend fun User.Companion.getOsuIdSuspend(qq: Long) = Database.suspendQuery {
    User.find { OsuUserInfo.qq eq qq }.singleOrNull() ?.osuId
}

suspend fun User.Companion.getOsuIdAndName(qq: Long) = Database.suspendQuery {
    User.find { OsuUserInfo.qq eq qq }.singleOrNull() ?.run { osuId to osuName }
}