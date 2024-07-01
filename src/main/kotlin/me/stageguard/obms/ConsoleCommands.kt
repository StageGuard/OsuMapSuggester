package me.stageguard.obms

import jakarta.annotation.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.bot.calculatorProcessorDispatcher
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.database.model.BeatmapSkillTableEx
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.info
import me.stageguard.obms.utils.warning
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.io.File

@Component
class ConsoleCommands : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Resource
    private lateinit var osuWebApi: OsuWebApi
    @Resource
    private lateinit var database: Database
    @Resource
    private lateinit var beatmapSkillTableEx: BeatmapSkillTableEx
    @Resource
    private lateinit var beatmapCache: BeatmapCache


    suspend fun saveBestPerformanceBeatmap() {
        var succeeded = 0
        logger.info { "Saving all user's best performance beatmap to cache..." }
        database.query { db -> db.sequenceOf(OsuUserInfo).map { it.qq } }?.forEach { qq ->
            val bpScores = osuWebApi.userScore(qq, type = "best", limit = 100)
            bpScores.onRight { scr ->
                scr.forEach {
                    val beatmapFile = File("${beatmapCache.CACHE_FOLDER}${it.beatmap!!.id}.osu")
                    if (!beatmapFile.exists()) {
                        try {
                            beatmapFile.parentFile.mkdirs()
                            val stream = osuWebApi.getBeatmapFileStream(it.beatmap.id)
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
                            logger.warning { "Cannot save beatmap ${it.beatmap.id}: $ex" }
                        }
                    }
                }
            }.onLeft {
                logger.warning { "Cannot get best performance of user $qq: $it" }
            }
        }
        logger.info { "Succeeded, count of new added beatmap: $succeeded" }
    }


    suspend fun refreshBeatmap() {
        logger.info { """
            Loading beatmap skill info in database to database...
            Note that if count of beatmap in cache folder is large, this process will take a long time
        """.trimIndent() }
        withContext(calculatorProcessorDispatcher) {
            val beatmap = File(beatmapCache.CACHE_FOLDER).listFiles { _, s ->
                s.split(".").last() == "osu"
            }?.map { f -> f.nameWithoutExtension.toInt() }
            if (beatmap != null) {
                val result = beatmapSkillTableEx.addAllViaBid(beatmap) ?: 0
                logger.info { "Finish updating beatmap cache, newly updated: $result." }
            }
        }
    }

    override fun run(vararg args: String?) {
        if (!(args.isNotEmpty() && args.getOrNull(0) == "obms")) return

        when (args.getOrNull(1)) {
            "saveBestPerformanceBeatmap" -> OsuMapSuggester.scope.launch { saveBestPerformanceBeatmap() }
            "refreshBeatmap" -> OsuMapSuggester.scope.launch { refreshBeatmap() }
        }
    }
}