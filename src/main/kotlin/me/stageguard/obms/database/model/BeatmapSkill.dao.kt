package me.stageguard.obms.database.model

import me.stageguard.obms.database.AddableTable
import me.stageguard.obms.database.Database
import me.stageguard.obms.osu.algorithm.`pp+`.SkillAttributes
import org.ktorm.dsl.AssignmentsBuilder
import org.ktorm.entity.Entity
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.*

object BeatmapSkillTable : AddableTable<BeatmapSkill>("beatmap_skill") {
    val id = int("id").primaryKey().bindTo { it.id }
    val bid = int("bid").bindTo { it.bid }
    val stars = double("stars").bindTo { it.stars }
    val jumpAimStrain = double("jump").bindTo { it.jumpAimStrain }
    val flowAimStrain = double("flow").bindTo { it.flowAimStrain }
    val speedStrain = double("speed").bindTo { it.speedStrain }
    val staminaStrain= double("stamina").bindTo { it.staminaStrain }
    val precisionStrain = double("precision").bindTo { it.precisionStrain }
    val rhythmComplexity = double("complexity").bindTo { it.rhythmComplexity }

    override fun <T : AssignmentsBuilder> T.mapElement(element: BeatmapSkill) {
        set(bid, element.bid)
        set(stars, element.stars)
        set(jumpAimStrain, element.jumpAimStrain)
        set(flowAimStrain, element.flowAimStrain)
        set(speedStrain, element.speedStrain)
        set(staminaStrain, element.staminaStrain)
        set(precisionStrain, element.precisionStrain)
        set(rhythmComplexity, element.rhythmComplexity)
    }

    @Suppress("DuplicatedCode")
    suspend fun addAll(items: List<Pair<Int, SkillAttributes>>) = Database.query { db ->
        val existBeatmap = db.sequenceOf(BeatmapSkillTable).map { it.bid }
        val toUpdate = mutableListOf<Pair<Int, SkillAttributes>>()
        val toInsert = mutableListOf<Pair<Int, SkillAttributes>>()

        items.forEach { b -> if(b.first in existBeatmap) toUpdate.add(b) else toInsert.add(b) }

        BeatmapSkillTable.batchInsert(toInsert) {
            BeatmapSkill {
                this.bid = it.first
                stars = it.second.stars
                jumpAimStrain = it.second.jumpAimStrain
                flowAimStrain = it.second.flowAimStrain
                speedStrain = it.second.speedStrain
                staminaStrain = it.second.staminaStrain
                precisionStrain = it.second.precisionStrain
                rhythmComplexity = it.second.accuracyStrain
            }
        }
        BeatmapSkillTable.batchUpdate1(toUpdate, bid, { first }) {
            BeatmapSkill {
                this.bid = it.first
                stars = it.second.stars
                jumpAimStrain = it.second.jumpAimStrain
                flowAimStrain = it.second.flowAimStrain
                speedStrain = it.second.speedStrain
                staminaStrain = it.second.staminaStrain
                precisionStrain = it.second.precisionStrain
                rhythmComplexity = it.second.accuracyStrain
            }
        }
    }
}

interface BeatmapSkill : Entity<BeatmapSkill> {
    companion object : Entity.Factory<BeatmapSkill>()
    var id: Int
    var bid: Int
    var stars: Double
    var jumpAimStrain: Double
    var flowAimStrain: Double
    var speedStrain: Double
    var staminaStrain: Double
    var precisionStrain: Double
    var rhythmComplexity: Double
}