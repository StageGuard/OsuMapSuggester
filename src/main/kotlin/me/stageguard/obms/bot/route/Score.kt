package me.stageguard.obms.bot.route

import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import me.stageguard.obms.*
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.MessageRoute.routeLock
import me.stageguard.obms.bot.calculatorProcessorDispatcher
import me.stageguard.obms.bot.refactoredExceptionCatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.cache.ReplayCache
import me.stageguard.obms.database.model.BeatmapSkill
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.RecentPlayDraw
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.pp.calculateDifficultyAttributes
import me.stageguard.obms.osu.algorithm.ppnative.PPCalculatorNative
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.processor.replay.ReplayFrameAnalyzer
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.mapRight
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.Either.Companion.right
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import io.github.humbleui.skija.EncodedImageFormat
import jakarta.annotation.Resource
import kotlinx.coroutines.*
import me.stageguard.obms.database.model.BeatmapSkillTableEx
import me.stageguard.obms.utils.warning
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*
import kotlin.math.pow

@Shiro
@Component
open class Score {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Resource
    private lateinit var osuWebApi: OsuWebApi
    @Resource
    private lateinit var replayCache: ReplayCache
    @Resource
    private lateinit var recentPlayDraw: RecentPlayDraw
    @Resource
    private lateinit var beatmapSkillTableEx: BeatmapSkillTableEx
    @Resource
    private lateinit var beatmapCache: BeatmapCache

    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".bps", "。bps"])
    fun bestPerformance(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch(refactoredExceptionCatcher) {
            val bp = message.substringAfter("bps").trim().run {
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
            val score = osuWebApi.userScore(sender.userId, type = "best", limit = 1, offset = bp - 1).rightOrThrowLeft()
            processScoreData(score.single())
        }
    }
    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".rep", "。rep"])
    fun recentScore(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch(refactoredExceptionCatcher) {
            processScoreData(getLastScore(5).rightOrThrowLeft())
        }
    }
    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".scr", "。scr"])
    fun score(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch(refactoredExceptionCatcher) {
            val mods = mutableListOf<String>()
            val bid = message.substringAfter("scr").trim().run {
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
            val score = osuWebApi.userBeatmapScore(sender.userId, bid, mods = mods.map { it.uppercase() }).rightOrThrowLeft()
            processScoreData(score.score)
        }
    }




    private tailrec suspend fun GroupMessageEvent.getLastScore(
        maxTryCount: Int,
        triedCount: Int = 0
    ) : OptionalValue<ScoreDTO> =
        osuWebApi.userScore(user = sender.userId, limit = 1, offset = triedCount, includeFails = true).onLeft {
            return if(maxTryCount == triedCount + 1) {
                Either(it)
            } else {
                getLastScore(maxTryCount, triedCount + 1)
            }
        }.mapRight {
            val single = it.single()
            if(single.mode != "osu") {
                return if(maxTryCount == triedCount + 1) {
                    Either(UserScoreEmptyException(sender.userId))
                } else {
                    getLastScore(maxTryCount, triedCount + 1)
                }
            }
            single
        }


    private suspend fun GroupMessageEvent.processScoreData(score: ScoreDTO) = withContext(calculatorProcessorDispatcher) {
        logger.info("Processing score data of ${sender.userId} on score ${score.id}.")
        val currentTimestamp = System.currentTimeMillis()

        val beatmapFile = beatmapCache.getBeatmapFile(score.beatmap!!.id)
        val beatmap = beatmapCache.getBeatmap(score.beatmap.id)
        //calculate pp, first: current miss, second: full combo
        val mods = score.mods.parseMods()
        val pp = beatmapFile.mapRight {
            PPCalculatorNative.of(it.absolutePath).mods(mods)
                .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                .misses(score.statistics.countMiss)
                .combo(score.maxCombo)
                .accuracy(score.accuracy * 100).calculate()
        }
        val ppCurvePoints = (mutableListOf<Pair<Double, Double>>() to mutableListOf<Pair<Double, Double>>()).also { p ->
            beatmapFile.onRight {
                p.first.add(score.accuracy * 100 to pp.right.total)
                p.second.add(score.accuracy * 100 to PPCalculatorNative.of(it.absolutePath).mods(mods).accuracy(score.accuracy * 100).calculate().total)
                generateSequence(900) { s -> if(s == 1000) null else s + 5 }.forEach { step ->
                    val acc = step / 10.0
                    p.second.add(acc to PPCalculatorNative.of(it.absolutePath).mods(mods).accuracy(acc).calculate().total)
                    p.first.add(acc to
                            PPCalculatorNative.of(it.absolutePath).mods(mods)
                                .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                                .misses(score.statistics.countMiss)
                                .combo(score.maxCombo)
                                .accuracy(acc).calculate().total
                    )
                }
            }
        }

        val skillAttributes = mutableMapOf<String, Double>()
        val ppx = beatmap.mapRight {
            it.calculateSkills(
                ModCombination.of(mods),
                Optional.of(score.statistics.run { count300 + count100 + count50 })
            )
        }.onRight { unwrapped ->
            val totalAimStrain = unwrapped.jumpAimStrain + unwrapped.flowAimStrain
            val totalStrainWithoutMultiplier = pp.right.run {
                aim.pow(1.1) + speed.pow(1.1) + accuracy.pow(1.1) + flashlight.pow(1.1)
            }

            skillAttributes["Jump"] = pp.right.total * (pp.right.aim.pow(1.1) / totalStrainWithoutMultiplier) *
                    (unwrapped.jumpAimStrain / totalAimStrain)
            skillAttributes["Flow"] = pp.right.total * (pp.right.aim.pow(1.1) / totalStrainWithoutMultiplier) *
                    (unwrapped.flowAimStrain / totalAimStrain)
            skillAttributes["Speed"] = pp.right.total * (pp.right.speed.pow(1.1) / totalStrainWithoutMultiplier)
            skillAttributes["Accuracy"] = pp.right.total * (pp.right.accuracy.pow(1.1) / totalStrainWithoutMultiplier)
            skillAttributes["Flashlight"] = pp.right.total * (pp.right.flashlight.pow(1.1) / totalStrainWithoutMultiplier)

            if (skillAttributes["Flashlight"] == 0.0) skillAttributes.remove("Flashlight")
        }

        val modCombination = ModCombination.of(mods)
        val difficultyAttribute = beatmap.mapRight { it.calculateDifficultyAttributes(modCombination) }
        val userBestScore = if(score.bestId != score.id && (score.replay == null || score.replay == false)) {
            osuWebApi.userBeatmapScore(sender.userId, score.beatmap.id, mods = score.mods)
        } else { Either.invoke(UnhandledException()) }

        //我草，血压上来了
        val replayAnalyzer = beatmap.run b@ { this@b.ifRight { b ->
            score.run s@ {
                if(this@s.replay == true) {
                    //if replay available, the replay must be the best score play of this beatmap
                    replayCache.getReplayData(this@s.bestId!!).run r@ { this@r.ifRight { r ->
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
            val sk = ppx.onLeft {
                logger.warning { "Error while add beatmap ${score.beatmap.id}: $it" }
                return@launch
            }.right
            beatmapSkillTableEx.addSingle(BeatmapSkill {
                this.bid = bid
                this.stars = score.beatmap.difficultyRating
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
            osuWebApi.getBeatmap(sender.userId, score.beatmap.id).mapRight { it.beatmapset!! }
        } else {
            InferredOptionalValue(score.beatmapset)
        }.rightOrThrowLeft()
        val mapperInfo = osuWebApi.usersViaUID(sender.userId, beatmapSet.userId).rightOrNull

        val processTimeDiff = System.currentTimeMillis() - currentTimestamp
        logger.info("Finished processing score data of ${sender.userId} on ${score.id}, took $processTimeDiff milliseconds.")

        logger.info("Generating image ScorePanel of ${sender.userId} on ${score.id}.")
        val imageTimeStamp = System.currentTimeMillis()
        val bytes = withContext(Dispatchers.IO) {
            recentPlayDraw.drawRecentPlayCard(
                score, beatmapSet, mapperInfo, modCombination, difficultyAttribute, ppCurvePoints,
                if(skillAttributes.isNotEmpty()) InferredOptionalValue(skillAttributes) else Either(ppx.left),
                userBestScore, replayAnalyzer
            ).bytes(EncodedImageFormat.PNG)
        }
        val imageTimeDiff = System.currentTimeMillis() - imageTimeStamp
        logger.info("Finished generating image ScorePanel of ${sender.userId} on ${score.id}, took $imageTimeDiff milliseconds.")

        val imageBase64String = Base64.getEncoder().encodeToString(bytes)
        atReply { img("base64://$imageBase64String") }
    }
}
