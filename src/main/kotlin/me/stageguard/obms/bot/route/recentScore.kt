package me.stageguard.obms.bot.route

import kotlinx.coroutines.runInterruptible
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.cache.BeatmapPool
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.RecentPlay
import me.stageguard.obms.osu.algorithm.`pp+`.SkillAttributes
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.pp.DifficultyAttributes
import me.stageguard.obms.osu.algorithm.pp.PPCalculator
import me.stageguard.obms.osu.algorithm.pp.calculateDifficultyAttributes
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.BeatmapUserScoreDTO
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.Either
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import java.lang.Exception
import java.util.*

fun GroupMessageSubscribersBuilder.recentScore() {
    startsWith(".rep") {
        getLastScore(5).onSuccess { score ->
            val beatmap = BeatmapPool.getBeatmap(score.beatmap!!.id)
            //calculate pp, first: current miss, second: full combo
            val mods = score.mods.parseMods()
            val ppCurvePoints = (mutableListOf<Pair<Double, Double>>() to mutableListOf<Pair<Double, Double>>()).also { p ->
                if(beatmap is Either.Left) {
                    p.first.add(score.accuracy * 100 to PPCalculator.of(beatmap.value).mods(mods)
                        .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                        .misses(score.statistics.countMiss)
                        .combo(score.maxCombo)
                        .accuracy(score.accuracy * 100).calculate().total)
                    (900..1000 step 5).forEach { step ->
                        val acc = step / 10.0
                        p.second.add(acc to PPCalculator.of(beatmap.value).mods(mods).accuracy(acc).calculate().total)
                        p.first.add(acc to
                            PPCalculator.of(beatmap.value).mods(mods)
                                .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                                .misses(score.statistics.countMiss)
                                .combo(score.maxCombo)
                                .accuracy(acc).calculate().total
                        )
                    }
                }
            }
            val ppPlusStrain = kotlin.run {
                if(beatmap is Either.Left) {
                    Either.Left(beatmap.value.calculateSkills(
                        ModCombination.of(mods), Optional.of(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                    ))
                } else Either.Right<Exception>(IllegalStateException())
            }
            val modCombination = ModCombination.of(mods)
            val difficultyAttribute = kotlin.run {
                if(beatmap is Either.Left) {
                    Either.Left(beatmap.value.calculateDifficultyAttributes(modCombination))
                } else {
                    Either.Right<Exception>(IllegalStateException())
                }
            }

            val userBestScore = OsuWebApi.userBeatmapScore(sender.id, score.beatmap.id)

            processRecentPlayData(score, modCombination, difficultyAttribute, ppCurvePoints, ppPlusStrain, userBestScore)
        }.onFailure {
            atReply("从服务器获取最近成绩时发生了异常：$it")
        }
    }
}

tailrec suspend fun GroupMessageEvent.getLastScore(
    maxTryCount: Int,
    triedCount: Int = 0
) : Result<ScoreDTO> {
    return when(val score = OsuWebApi.userScore(user = sender.id, limit = 1, offset = triedCount, includeFails = true)) {
        is Either.Left -> {
            val single = score.value.single()
            return if(single.mode != "osu") {
                if(maxTryCount == triedCount + 1) {
                    Result.failure(IllegalStateException("NO_RECENT_GRADE"))
                } else getLastScore(maxTryCount, triedCount + 1)
            } else Result.success(single)
        }
        is Either.Right -> {
            if(maxTryCount == triedCount + 1) {
                Result.failure(score.value)
            } else getLastScore(maxTryCount, triedCount + 1)
        }
    }
}


suspend fun GroupMessageEvent.processRecentPlayData(
    scoreDTO: ScoreDTO, mods: ModCombination,
    attribute: Either<DifficultyAttributes, Exception>,
    ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
    skillAttributes: Either<SkillAttributes, Exception>,
    userBestScore: Either<BeatmapUserScoreDTO, Exception>
) {
    val surfaceOutput = RecentPlay.drawRecentPlayCard(scoreDTO, mods, attribute, ppCurvePoints, skillAttributes, userBestScore)
    val bytes = surfaceOutput.bytes(EncodedImageFormat.PNG)
    val externalResource = bytes.toExternalResource("png")
    val image = group.uploadImage(externalResource)
    runInterruptible { externalResource.close() }
    atReply(image.toMessageChain())
}
