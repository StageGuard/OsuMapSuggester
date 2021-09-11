package me.stageguard.obms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.bot.calculatorProcessorDispatcher
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.BeatmapSkill
import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.osu.algorithm.`pp+`.SkillAttributes
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.pp.calculateDifficultyAttributes
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.warning
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import java.io.File

@ConsoleExperimentalApi
@Suppress("unused")
object ConsoleCommands : CompositeCommand(
    OsuMapSuggester, "obms"
) {
    @SubCommand
    @Description("Save all user's best performance beatmap to cache")
    suspend fun CommandSender.saveBestPerformanceBeatmap() {
        var succeeded = 0
        OsuMapSuggester.logger.info { "Saving all user's best performance beatmap to cache..." }
        Database.query { db -> db.sequenceOf(OsuUserInfo).map { it.qq } }?.forEach { qq ->
            val bpScores = OsuWebApi.userScore(qq, type = "best", limit = 100)
            bpScores.onRight { scr ->
                scr.forEach {
                    val beatmapFile = File("${BeatmapCache.CACHE_FOLDER}${it.beatmap!!.id}.osu")
                    if (!beatmapFile.exists()) {
                        try {
                            beatmapFile.parentFile.mkdirs()
                            val stream = OsuWebApi.getBeatmapFileStream(it.beatmap.id)
                            withContext(Dispatchers.IO) {
                                runInterruptible {
                                    beatmapFile.createNewFile()
                                    stream.use { s ->
                                        beatmapFile.writeBytes(s.readAllBytes())
                                    }
                                }
                            }
                            succeeded ++
                        } catch (ex: Exception) {
                            OsuMapSuggester.logger.warning { "Cannot save beatmap ${it.beatmap.id}: $ex" }
                        }
                    }
                }
            }.onLeft {
                OsuMapSuggester.logger.warning { "Cannot get best performance of user $qq: $it" }
            }
        }
        OsuMapSuggester.logger.info { "Succeeded, count of new added beatmap: $succeeded" }
    }

    @Suppress("DuplicatedCode")
    @SubCommand
    @Description("Load all beatmap skill info in cache to database")
    suspend fun CommandSender.refreshBeatmap() {
        var (succeeded, all) = 0 to 0
        OsuMapSuggester.logger.info { "Loading beatmap skill info in database to database..." }
        val existBeatmap = Database.query { db -> db.sequenceOf(BeatmapSkillTable).map { it.bid } }
        if(existBeatmap != null) {
            val toUpdate = mutableListOf<Pair<Int, SkillAttributes>>()
            val toInsert = mutableListOf<Pair<Int, SkillAttributes>>()
            withContext(calculatorProcessorDispatcher) {
                File(BeatmapCache.CACHE_FOLDER).listFiles { _, s -> s.split(".").last() == "osu" }?.also {
                    all = it.size
                }?.forEach { f ->
                    try {
                        val bid = f.nameWithoutExtension.toInt()
                        val beatmap = Beatmap.parse(f)
                        val skillAttributes = beatmap.calculateSkills(ModCombination.of(Mod.None))
                        // star calculation of pp+ algorithm is corrupted
                        // spend one more time to calculate stars with pp algorithm
                        skillAttributes.stars = beatmap.calculateDifficultyAttributes(ModCombination.of(Mod.None)).stars
                        if(bid in existBeatmap) {
                            toUpdate.add(bid to skillAttributes)
                        } else {
                            toInsert.add(bid to skillAttributes)
                        }
                        succeeded ++
                    } catch (ex: Exception) {
                        OsuMapSuggester.logger.error { "Cannot record beatmap ${f.name}: $ex" }
                        ex.printStackTrace()
                    }
                }
            }
            Database.query {
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
                BeatmapSkillTable.batchUpdate1(toUpdate, BeatmapSkillTable.bid, { first }) {
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
            OsuMapSuggester.logger.info { "Beatmap cache has already recorded. succeeded: $succeeded, all: $all" }
        }
    }
}