package me.stageguard.obms.database.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column


object PPPlusInfo : IntIdTable("ppplus") {
    val osuId: Column<Int> = integer("osuId").uniqueIndex()
    val aimTotal: Column<Double> = double("aimTotal")
    val aimJump: Column<Double> = double("aimJump")
    val aimFlow: Column<Double> = double("aimFlow")
    val precision: Column<Double> = double("precision")
    val speed: Column<Double> = double("speed")
    val stamina: Column<Double> = double("stamina")
    val accuracy: Column<Double> = double("accuracy")

}

class UserPPPlus(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserPPPlus>(PPPlusInfo)
    val osuId by PPPlusInfo.osuId
    val aimTotal by PPPlusInfo.aimTotal
    val aimJump by PPPlusInfo.aimJump
    val aimFlow by PPPlusInfo.aimFlow
    val precision by PPPlusInfo.precision
    val speed by PPPlusInfo.speed
    val stamina by PPPlusInfo.stamina
    val accuracy by PPPlusInfo.accuracy
}