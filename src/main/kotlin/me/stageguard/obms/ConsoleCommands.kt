package me.stageguard.obms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.bot.calculatorProcessorDispatcher
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
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
                            stream.onRight r1@ { s ->
                                withContext(Dispatchers.IO) {
                                    runInterruptible {
                                        beatmapFile.createNewFile()
                                        s.use { s ->
                                            beatmapFile.writeBytes(s.readAllBytes())
                                        }
                                    }
                                }
                                succeeded ++
                                return@r1
                            }.left.also { re -> throw re }
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
        OsuMapSuggester.logger.info { """
            Loading beatmap skill info in database to database...
            Note that if count of beatmap in cache folder is large, this process will take a long time
        """.trimIndent() }
        withContext(calculatorProcessorDispatcher) {
            val beatmap = File(BeatmapCache.CACHE_FOLDER).listFiles { _, s ->
                s.split(".").last() == "osu"
            }?.map { f -> f.nameWithoutExtension.toInt() }
            if (beatmap != null) {
                val result = BeatmapSkillTable.addAllViaBid(beatmap) ?: 0
                OsuMapSuggester.logger.info { "Finish refreshing beatmap cache, newly updated: $result" }
            }
        }
    }
}