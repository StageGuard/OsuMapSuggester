package me.stageguard.obms.database.model

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.database.AddableTable
import me.stageguard.obms.database.Database
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.pp.calculateDifficultyAttributes
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.right
import net.mamoe.mirai.utils.warning
import org.ktorm.dsl.AssignmentsBuilder
import org.ktorm.dsl.eq
import org.ktorm.entity.*
import org.ktorm.schema.*

object BeatmapSkillTable : AddableTable<BeatmapSkill>("beatmap_skill") {
    val id = int("id").primaryKey().bindTo { it.id }
    val bid = int("bid").bindTo { it.bid }
    val stars = double("stars").bindTo { it.stars }
    val bpm = double("bpm").bindTo { it.bpm }
    val length = int("length").bindTo { it.length }
    val circleSize = double("circle_size").bindTo { it.circleSize }
    val hpDrain = double("hp_drain").bindTo { it.hpDrain }
    val approachingRate = double("approaching_rate").bindTo { it.approachingRate }
    val overallDifficulty = double("overall_difficulty").bindTo { it.overallDifficulty }
    val jumpAimStrain = double("jump").bindTo { it.jumpAimStrain }
    val flowAimStrain = double("flow").bindTo { it.flowAimStrain }
    val speedStrain = double("speed").bindTo { it.speedStrain }
    val staminaStrain= double("stamina").bindTo { it.staminaStrain }
    val precisionStrain = double("precision").bindTo { it.precisionStrain }
    val rhythmComplexity = double("complexity").bindTo { it.rhythmComplexity }

    @Suppress("DuplicatedCode")
    override fun <T : AssignmentsBuilder> T.mapElement(element: BeatmapSkill) {
        set(bid, element.bid)
        set(stars, element.stars)
        set(bpm, element.bpm)
        set(length, element.length)
        set(circleSize, element.circleSize)
        set(hpDrain, element.hpDrain)
        set(approachingRate, element.approachingRate)
        set(overallDifficulty, element.overallDifficulty)
        set(jumpAimStrain, element.jumpAimStrain)
        set(flowAimStrain, element.flowAimStrain)
        set(speedStrain, element.speedStrain)
        set(staminaStrain, element.staminaStrain)
        set(precisionStrain, element.precisionStrain)
        set(rhythmComplexity, element.rhythmComplexity)
    }

    suspend fun addSingle(item: BeatmapSkill) = addAll(listOf(item))

    suspend fun addAll(items: List<BeatmapSkill>) = Database.query { db ->
        val toUpdate = mutableListOf<BeatmapSkill>()
        val toInsert = mutableListOf<BeatmapSkill>()

        items.forEach { b ->
            val find = db.sequenceOf(this@BeatmapSkillTable).find { it.bid eq b.bid }
            if(find != null) toUpdate.add(b) else toInsert.add(b)
        }

        var effected = 0
        if(toInsert.isNotEmpty()) effected += batchInsert(toInsert)?.size ?: 0
        if(toUpdate.isNotEmpty()) effected += batchUpdate1(toUpdate, bid, { this.bid }) { it }?.size ?: 0
        effected
    }

    suspend fun addAllViaBid(items: List<Int>, ifAbsent: Boolean = false) = Database.query { db ->
        val toUpdate = mutableListOf<BeatmapSkill>()
        val toInsert = mutableListOf<BeatmapSkill>()

        val randomUser = db.sequenceOf(OsuUserInfo).toList().firstOrNull()?.qq

        if(randomUser == null) {
            OsuMapSuggester.logger.warning { "Error while add beatmap: no user exist in database" }
            return@query 0f
        }

        items.forEach { bid -> try {
            val find = db.sequenceOf(this@BeatmapSkillTable).find { it.bid eq bid }
            if(find != null && ifAbsent) return@forEach

            val beatmap = BeatmapCache.getBeatmap(bid).rightOrThrowLeft()

            val skills = beatmap.calculateSkills(ModCombination.of(Mod.None))
            val difficultyAttributes = beatmap.calculateDifficultyAttributes(ModCombination.of(Mod.None))

            val dao = BeatmapSkill {
                this.bid = bid
                this.stars = difficultyAttributes.stars
                this.circleSize = beatmap.circleSize
                this.hpDrain = beatmap.hpDrainRate
                this.approachingRate = beatmap.approachRate
                this.overallDifficulty = beatmap.overallDifficulty
                this.jumpAimStrain = skills.jumpAimStrain
                this.flowAimStrain = skills.flowAimStrain
                this.speedStrain = skills.speedStrain
                this.staminaStrain = skills.staminaStrain
                this.precisionStrain = skills.precisionStrain
                this.rhythmComplexity = skills.accuracyStrain
            }

            if(find != null) {
                dao.bpm = find.bpm
                dao.length = find.length

                toUpdate.add(dao)
            } else {
                val beatmapInfo = OsuWebApi.getBeatmap(randomUser, bid).rightOrThrowLeft()

                dao.bpm = beatmapInfo.bpm
                dao.length = beatmapInfo.totalLength

                toInsert.add(dao)
            }
        } catch (ex: Exception) {
            OsuMapSuggester.logger.warning("Error while add beatmap $bid.", ex)
        } }

        (batchInsert(toInsert)?.size ?: 0) + (batchUpdate1(toUpdate, bid, { this.bid }) { it }?.size ?: 0)
    }
}

interface BeatmapSkill : Entity<BeatmapSkill> {
    companion object : Entity.Factory<BeatmapSkill>()
    var id: Int
    var bid: Int
    var stars: Double
    var bpm: Double
    var length: Int
    var circleSize: Double
    var hpDrain: Double
    var approachingRate: Double
    var overallDifficulty: Double
    var jumpAimStrain: Double
    var flowAimStrain: Double
    var speedStrain: Double
    var staminaStrain: Double
    var precisionStrain: Double
    var rhythmComplexity: Double
}