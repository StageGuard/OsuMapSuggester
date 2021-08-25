package me.stageguard.obms.bot.route

import kotlinx.coroutines.runInterruptible
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.cache.ReplayCache
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.RecentPlay
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.pp.PPCalculator
import me.stageguard.obms.osu.algorithm.pp.calculateDifficultyAttributes
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.processor.replay.ReplayFrameAnalyzer
import me.stageguard.obms.utils.InferredEitherOrISE
import me.stageguard.obms.utils.ValueOrISE
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.toMessageChain
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.mapRight
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.Either.Companion.right
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import java.lang.NumberFormatException
import java.util.Optional

fun GroupMessageSubscribersBuilder.recentScore() {
    startsWith(".bps") {
        val bp = message.contentToString().removePrefix(".bps").trim().run {
            try {
                val b = toInt()
                require(b in (1..100)) { throw IllegalStateException("INVALID_BP_ORD") }
                InferredEitherOrISE(b)
            } catch (ex: NumberFormatException) {
                Either(IllegalStateException("INVALID_INPUT_FORMAT"))
            } catch (ex: IllegalStateException) {
                Either(ex)
            }
        }
        bp.onRight {
            val score = OsuWebApi.userScore(sender.id, type = "best", limit = 1, offset = it - 1)
            score.onRight { ls ->
                processRecentPlayData(ls.single())
            }.onLeft {
                atReply("从服务器获取你的 Best Performance 信息时发生了异常: ${parseExceptions(it)}")
            }
        }.onLeft {
            atReply("从服务器获取你的 Best Performance 信息时发生了异常: ${parseExceptions(it)}")
        }

    }
    startsWith(".scr") {
        val bid = message.contentToString().removePrefix(".scr").trim().run {
            try {
                InferredEitherOrISE(toInt())
            } catch (ex: NumberFormatException) {
                Either(IllegalStateException("INVALID_INPUT_FORMAT"))
            }
        }
        bid.onRight {
            val score = OsuWebApi.userBeatmapScore(sender.id, it)
            score.onRight { s ->
                processRecentPlayData(s.score)
            }.onLeft {
                atReply("从服务器获取你的成绩信息时发生了异常: ${parseExceptions(it)}")
            }
        }.onLeft {
            atReply("从服务器获取你的成绩信息时发生了异常: ${parseExceptions(it)}")
        }
    }
    startsWith(".rep") {
        getLastScore(5).onRight { score ->
            processRecentPlayData(score)
        }.onLeft {
            atReply("从服务器获取最近成绩时发生了异常：${parseExceptions(it)}")
        }
    }
}



tailrec suspend fun GroupMessageEvent.getLastScore(
    maxTryCount: Int,
    triedCount: Int = 0,
    lastException: IllegalStateException? = null
) : ValueOrISE<ScoreDTO> =
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


suspend fun GroupMessageEvent.processRecentPlayData(score: ScoreDTO) {
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
    val skillAttributes = beatmap.mapRight {
        it.calculateSkills(ModCombination.of(mods), Optional.of(
            score.statistics.count300 + score.statistics.count100 + score.statistics.count50
        ))
    }
    val modCombination = ModCombination.of(mods)
    val difficultyAttribute = beatmap.mapRight { it.calculateDifficultyAttributes(modCombination) }
    val userBestScore = if(score.bestId != score.id) {
        OsuWebApi.userBeatmapScore(sender.id, score.beatmap.id)
    } else {
        Either.invoke(IllegalStateException())
    }

    //我草，血压上来了
    val replayAnalyzer = beatmap.run b@ { this@b.ifRight { b ->
        score.run s@ {
            if(this@s.replay) {
                ReplayCache.getReplayData(this@s.id).run r@ { this@r.ifRight { r ->
                    kotlin.runCatching {
                        val rep = ReplayFrameAnalyzer(b, r, modCombination)
                        InferredEitherOrISE(rep)
                    }.getOrElse { Either.invoke(IllegalStateException(it)) }
                } ?: Either.invoke(this@r.left) }
            } else Either.invoke(IllegalStateException("REPLAY_NOT_AVAILABLE"))
        }
    } ?: Either.invoke(this@b.left) }

    val beatmapSet = if(score.beatmapset == null) {
        val beatmapInfo = OsuWebApi.getBeatmap(sender.id, score.beatmap.id)
        beatmapInfo.ifRight {
            InferredEitherOrISE(it.beatmapset!!)
        } ?: Either.invoke(beatmapInfo.left)
    } else {
        InferredEitherOrISE(score.beatmapset)
    }.run {
        onLeft {
            atReply("从服务器获取铺面信息时发生了异常: ${parseExceptions(it)}")
        }.right
    }

    val surfaceOutput = RecentPlay.drawRecentPlayCard(
        score, beatmapSet, modCombination, difficultyAttribute, ppCurvePoints, skillAttributes, userBestScore, replayAnalyzer
    )
    val bytes = surfaceOutput.bytes(EncodedImageFormat.PNG)
    val externalResource = bytes.toExternalResource("png")
    val image = group.uploadImage(externalResource)
    runInterruptible { externalResource.close() }
    atReply(image.toMessageChain())
}
