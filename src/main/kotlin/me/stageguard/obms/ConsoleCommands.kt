package me.stageguard.obms

import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.BeatmapSkill
import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.osu.algorithm.`pp+`.SkillAttributes
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import java.io.File

@ConsoleExperimentalApi
@Suppress("unused")
object ConsoleCommands : CompositeCommand(
    OsuMapSuggester, "obms"
) {
    @Suppress("DuplicatedCode")
    @SubCommand
    @Description("Load all beatmap skill info in cache to database")
    suspend fun CommandSender.refreshBeatmap() {
        var succeeded = 0
        var all = 0
        OsuMapSuggester.logger.info { "Loading beatmap skill info in database to database..." }
        val existBeatmap = Database.query { db -> db.sequenceOf(BeatmapSkillTable).map { it.bid } }
        if(existBeatmap != null) {
            File(BeatmapCache.CACHE_FOLDER).listFiles { _, s -> s.split(".").last() == "osu" }?.also {
                all = it.size
            }?.forEach { f ->
                try {
                    val bid = f.nameWithoutExtension.toInt()
                    BeatmapCache.getBeatmap(bid).onRight { b ->
                        val skillAttributes = b.calculateSkills(ModCombination.of(Mod.None))
                        val toUpdate = mutableListOf<Pair<Int, SkillAttributes>>()
                        val toInsert = mutableListOf<Pair<Int, SkillAttributes>>()
                        if(bid in existBeatmap) {
                            toUpdate.add(bid to skillAttributes)
                        } else {
                            toInsert.add(bid to skillAttributes)
                        }
                        Database.query {
                            BeatmapSkillTable.batchInsert(toInsert) {
                                BeatmapSkill {
                                    this.bid = it.first
                                    jumpAimStrain = it.second.jumpAimStrain
                                    flowAimStrain = it.second.flowAimStrain
                                    speedStrain = it.second.speedStrain
                                    staminaStrain = it.second.staminaStrain
                                    precisionStrain = it.second.precisionStrain
                                    rhythmComplexity = it.second.accuracyStrain
                                }
                            }
                            BeatmapSkillTable.batchUpdate1(toUpdate, BeatmapSkillTable.bid, { first }) {
                                BeatmapSkill {
                                    this.bid = it.first
                                    jumpAimStrain = it.second.jumpAimStrain
                                    flowAimStrain = it.second.flowAimStrain
                                    speedStrain = it.second.speedStrain
                                    staminaStrain = it.second.staminaStrain
                                    precisionStrain = it.second.precisionStrain
                                    rhythmComplexity = it.second.accuracyStrain
                                }
                            }
                        }
                        succeeded ++
                    }.onLeft { throw it }
                } catch (ex: Exception) {
                    OsuMapSuggester.logger.error { "Cannot record beatmap ${f.name}: $ex" }
                    ex.printStackTrace()
                }
            }
            OsuMapSuggester.logger.info { "Beatmap cache has already recorded. succeeded: $succeeded, all: $all" }
        }
    }
}