package me.stageguard.obms.bot.route

import kotlinx.coroutines.runInterruptible
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.cache.BeatmapCache
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
import me.stageguard.obms.utils.ValueOrIllegalStateException
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.Either
import net.mamoe.mirai.utils.Either.Companion.mapRight
import net.mamoe.mirai.utils.Either.Companion.onLeft
import net.mamoe.mirai.utils.Either.Companion.onRight
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import java.util.Optional

fun GroupMessageSubscribersBuilder.recentScore() {
    startsWith(".rep") {
        getLastScore(5).onRight { score ->
            val beatmap = BeatmapCache.getBeatmap(score.beatmap!!.id)
            //calculate pp, first: current miss, second: full combo
            val mods = score.mods.parseMods()
            val ppCurvePoints = (mutableListOf<Pair<Double, Double>>() to mutableListOf<Pair<Double, Double>>()).also { p ->
                beatmap.onRight {
                    p.first.add(score.accuracy * 100 to PPCalculator.of(it).mods(mods)
                        .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                        .misses(score.statistics.countMiss)
                        .combo(score.maxCombo)
                        .accuracy(score.accuracy * 100).calculate().total)
                    (900..1000 step 5).forEach { step ->
                        val acc = step / 10.0
                        p.second.add(acc to PPCalculator.of(it).mods(mods).accuracy(acc).calculate().total)
                        p.first.add(acc to
                                PPCalculator.of(it).mods(mods)
                                    .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                                    .misses(score.statistics.countMiss)
                                    .combo(score.maxCombo)
                                    .accuracy(acc).calculate().total
                        )
                    }
                }
            }
            val ppPlusStrain = beatmap.mapRight {
                it.calculateSkills(ModCombination.of(mods), Optional.of(
                    score.statistics.count300 + score.statistics.count100 + score.statistics.count50
                ))
            }
            val modCombination = ModCombination.of(mods)
            val difficultyAttribute = beatmap.mapRight {
                it.calculateDifficultyAttributes(modCombination)
            }

            val userBestScore = OsuWebApi.userBeatmapScore(sender.id, score.beatmap.id)

            processRecentPlayData(score, modCombination, difficultyAttribute, ppCurvePoints, ppPlusStrain, userBestScore)
        }.onLeft {
            atReply("从服务器获取最近成绩时发生了异常：${parseExceptions(it)}")
        }
    }
}

tailrec suspend fun GroupMessageEvent.getLastScore(
    maxTryCount: Int,
    triedCount: Int = 0,
    lastException: IllegalStateException? = null
) : ValueOrIllegalStateException<ScoreDTO> =
    OsuWebApi.userScore(user = sender.id, limit = 1, offset = triedCount, includeFails = true).onLeft {
        return if(maxTryCount == triedCount + 1) {
            Either(it)
        } else getLastScore(
            maxTryCount,
            triedCount + 1,
            IllegalStateException("FAILED_AFTER_N_TRIES:${it}")
        )
    }.mapRight {
        val single = it.single()
        if(single.mode != "osu") {
            return if(maxTryCount == triedCount + 1) {
                Either(lastException!!)
            } else getLastScore(
                maxTryCount,
                triedCount + 1,
                IllegalStateException("NO_RECENT_SCORE"))
        }
        single
    }


suspend fun GroupMessageEvent.processRecentPlayData(
    scoreDTO: ScoreDTO, mods: ModCombination,
    attribute: ValueOrIllegalStateException<DifficultyAttributes>,
    ppCurvePoints: Pair<MutableList<Pair<Double, Double>>, MutableList<Pair<Double, Double>>>,
    skillAttributes: ValueOrIllegalStateException<SkillAttributes>,
    userBestScore: ValueOrIllegalStateException<BeatmapUserScoreDTO>
) {
    val surfaceOutput = RecentPlay.drawRecentPlayCard(scoreDTO, mods, attribute, ppCurvePoints, skillAttributes, userBestScore)
    val bytes = surfaceOutput.bytes(EncodedImageFormat.PNG)
    val externalResource = bytes.toExternalResource("png")
    val image = group.uploadImage(externalResource)
    runInterruptible { externalResource.close() }
    atReply(image.toMessageChain())
}
