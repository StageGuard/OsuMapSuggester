package me.stageguard.obms.bot.route

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.*
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.bot.calculatorProcessorDispatcher
import me.stageguard.obms.bot.graphicProcessorDispatcher
import me.stageguard.obms.bot.refactoredExceptionCatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.cache.ReplayCache
import me.stageguard.obms.database.model.BeatmapSkill
import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.RecentPlay
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.pp.PPCalculator
import me.stageguard.obms.osu.algorithm.pp.calculateDifficultyAttributes
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.processor.replay.ReplayFrameAnalyzer
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
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
import net.mamoe.mirai.utils.warning
import org.jetbrains.skija.EncodedImageFormat

fun GroupMessageSubscribersBuilder.recentScore() {
    routeLock(startWithIgnoreCase(".bps")) {
        OsuMapSuggester.launch(
            CoroutineName("Command \"bps\" of ${sender.id}") + refactoredExceptionCatcher
        ) {
            val bp = message.contentToString().removePrefix(".bps").trim().run {
                try {
                    val b = toInt()
                    require(b in 1..100)
                    InferredOptionalValue(b)
                } catch (ex: IllegalArgumentException) {
                    Either(InvalidInputException(this))
                } catch (ex: NumberFormatException) {
                    Either(InvalidInputException(this))
                }
            }.rightOrThrowLeft()
            val score = OsuWebApi.userScore(sender.id, type = "best", limit = 1, offset = bp - 1).rightOrThrowLeft()
            processRecentPlayData(score.single())
        }
    }
    routeLock(startWithIgnoreCase(".scr")) {
        OsuMapSuggester.launch(
            CoroutineName("Command \"scr\" of ${sender.id}") + refactoredExceptionCatcher
        ) {
            val mods = mutableListOf<String>()
            val bid = message.contentToString().removePrefix(".scr").trim().run {
                try {
                    if (contains("+")) {
                        var rawMods = substringAfter("+").trim()
                        while (rawMods.isNotEmpty()) {
                            mods.add(rawMods.take(2))
                            rawMods = rawMods.drop(2)
                        }
                        InferredOptionalValue(substringBefore("+").trim().toInt())
                    } else {
                        InferredOptionalValue(toInt())
                    }
                } catch (ex: NumberFormatException) {
                    Either(InvalidInputException(this))
                }
            }.rightOrThrowLeft()
            val score = OsuWebApi.userBeatmapScore(sender.id, bid, mods = mods.map { it.uppercase() }).rightOrThrowLeft()
            processRecentPlayData(score.score)
        }

    }
    routeLock(startWithIgnoreCase(".rep")) {
        OsuMapSuggester.launch(
            CoroutineName("Command \"rep\" of ${sender.id}") + refactoredExceptionCatcher
        ) {
            processRecentPlayData(getLastScore(5).rightOrThrowLeft())
        }
    }
}



tailrec suspend fun GroupMessageEvent.getLastScore(
    maxTryCount: Int,
    triedCount: Int = 0
) : OptionalValue<ScoreDTO> =
    OsuWebApi.userScore(user = sender.id, limit = 1, offset = triedCount, includeFails = true).onLeft {
        return if(maxTryCount == triedCount + 1) {
            Either(it)
        } else {
            getLastScore(maxTryCount, triedCount + 1)
        }
    }.mapRight {
        val single = it.single()
        if(single.mode != "osu") {
            return if(maxTryCount == triedCount + 1) {
                Either(UserScoreEmptyException(sender.id))
            } else {
                getLastScore(maxTryCount, triedCount + 1)
            }
        }
        single
    }


suspend fun GroupMessageEvent.processRecentPlayData(score: ScoreDTO) = withContext(calculatorProcessorDispatcher) {
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
            p.second.add(score.accuracy * 100 to PPCalculator.of(it).mods(mods).accuracy(score.accuracy * 100).calculate().total)
            generateSequence(900) { s -> if(s == 1000) null else s + 5 }.forEach { step ->
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
    val skillAttributes = beatmap.mapRight { it.calculateSkills(ModCombination.of(mods)) }

    val modCombination = ModCombination.of(mods)
    val difficultyAttribute = beatmap.mapRight { it.calculateDifficultyAttributes(modCombination) }
    val userBestScore = if(score.bestId != score.id && (score.replay == null || score.replay == false)) {
        OsuWebApi.userBeatmapScore(sender.id, score.beatmap.id, mods = score.mods)
    } else { Either.invoke(UnhandledException()) }

    //我草，血压上来了
    val replayAnalyzer = beatmap.run b@ { this@b.ifRight { b ->
        score.run s@ {
            if(this@s.replay == true) {
                //if replay available, the replay must be the best score play of this beatmap
                ReplayCache.getReplayData(this@s.bestId!!).run r@ { this@r.ifRight { r ->
                    try {
                        val rep = ReplayFrameAnalyzer(b, r, modCombination)
                        InferredOptionalValue(rep)
                    } catch (ex: Exception) {
                        Either.invoke(UnhandledException().suppress(ex))
                    }
                } ?: Either.invoke(this@r.left) }
            } else Either.invoke(ReplayNotAvailable(this@s.id))
        }
    } ?: Either.invoke(this@b.left) }

    launch(CoroutineName("Add skillAttribute of beatmap ${score.beatmap.id} to database")) {
        val da = difficultyAttribute.onLeft {
            OsuMapSuggester.logger.warning { "Error while add beatmap ${score.beatmap.id}: $it" }
            return@launch
        }.right
        val sk = skillAttributes.onLeft {
            OsuMapSuggester.logger.warning { "Error while add beatmap ${score.beatmap.id}: $it" }
            return@launch
        }.right
        BeatmapSkillTable.addSingle(BeatmapSkill {
            this.bid = bid
            this.stars = da.stars
            this.bpm = score.beatmap.bpm
            this.length = score.beatmap.totalLength
            this.circleSize = score.beatmap.cs
            this.hpDrain = score.beatmap.drain
            this.approachingRate = score.beatmap.ar
            this.overallDifficulty = score.beatmap.accuracy
            this.jumpAimStrain = sk.jumpAimStrain
            this.flowAimStrain = sk.flowAimStrain
            this.speedStrain = sk.speedStrain
            this.staminaStrain = sk.staminaStrain
            this.precisionStrain = sk.precisionStrain
            this.rhythmComplexity = sk.accuracyStrain
        })
    }

    val beatmapSet = if(score.beatmapset == null) {
        OsuWebApi.getBeatmap(sender.id, score.beatmap.id).mapRight { it.beatmapset!! }
    } else {
        InferredOptionalValue(score.beatmapset)
    }.rightOrThrowLeft()

    val mapperInfo = OsuWebApi.usersViaUID(sender.id, beatmapSet.userId).rightOrNull

    val bytes = withContext(graphicProcessorDispatcher) {
        RecentPlay.drawRecentPlayCard(
            score, beatmapSet, mapperInfo, modCombination, difficultyAttribute,
            ppCurvePoints, skillAttributes, userBestScore, replayAnalyzer
        ).bytes(EncodedImageFormat.PNG)
    }
    val externalResource = bytes.toExternalResource("png")
    val image = group.uploadImage(externalResource)
    runInterruptible { externalResource.close() }
    atReply(image.toMessageChain())
}
