package me.stageguard.obms.bot.route

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.bot.graphicProcessorDispatcher
import me.stageguard.obms.bot.refactoredExceptionCatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.Profile
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import io.github.humbleui.skija.EncodedImageFormat
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.osu.algorithm.`pp+`.calculateSkills
import me.stageguard.obms.osu.algorithm.ppnative.PPCalculatorNative
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.Either.Companion.ifRight
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.math.max
import kotlin.math.pow

data class PerformanceStructure(
    var jumpAim: Double = 0.0,
    var flowAim: Double = 0.0,
    var speed: Double = 0.0,
    var accuracy: Double = 0.0,
    var flashlight: Double = 0.0,
    var bonus: Double = 0.0,
)

fun GroupMessageSubscribersBuilder.profile() {
    routeLock(startWithIgnoreCase(".info")) {
        OsuMapSuggester.launch(
            CoroutineName("Command \"info\" of ${sender.id}") + refactoredExceptionCatcher
        ) {
            val profile = OsuWebApi.me(sender.id).rightOrThrowLeft()
            val bestScores = OsuWebApi.userScore(sender.id, type = "best", limit = 100).rightOrNull
                ?.mapIndexed { index, scoreDTO -> index to scoreDTO } ?.sortedBy { it.first } ?.toMap() ?: mapOf()
            val bestScoresCount = bestScores.count()

            val newBestScores = kotlin.run {
                val currentLocalDateTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                bestScores.filter { (_, score) ->
                    currentLocalDateTime - score.createdAt.primitive.toEpochSecond(ZoneOffset.UTC) < 60 * 60 * 24
                }
            }
            val firstNewBestScoreRank = newBestScores.entries.firstOrNull() ?.key ?: -1
            val lastBestScore = bestScores.entries.lastOrNull() ?.value

            var bpOffset = 0
            val performances = PerformanceStructure()
            var newlyGainPp = 0.0
            var currentAverageBp = 0.0
            var lastAverageBp = 0.0
            var maxBpDiff = bestScores.values.run { last().pp!! - first().pp!! }
            var maxTweenDiff = 0.0
            var maxTweenDiffRank = 0
            val modUsage = mutableMapOf<Mod, Int>()

            bestScores.forEach { scoreAndRank -> BeatmapCache.getBeatmapFile(scoreAndRank.value.beatmap!!.id).ifRight { file ->
                val (rank, score) = scoreAndRank
                val beatmap = Beatmap.parse(file)
                val mods = score.mods.parseMods()

                val pp = PPCalculatorNative.of(file.absolutePath).mods(mods)
                    .passedObjects(score.statistics.run { count300 + count100 + count50 })
                    .misses(score.statistics.countMiss)
                    .combo(score.maxCombo)
                    .accuracy(score.accuracy * 100).calculate()
                val ppx = beatmap.calculateSkills(
                    ModCombination.of(mods),
                    Optional.of(score.statistics.run { count300 + count100 + count50 })
                )

                val totalAimStrain = ppx.jumpAimStrain + ppx.flowAimStrain
                val totalStrainWithoutMultiplier = pp.run {
                    aim.pow(1.1) + speed.pow(1.1) + accuracy.pow(1.1) + flashlight.pow(1.1)
                }

                performances.jumpAim += pp.total * (pp.aim.pow(1.1) / totalStrainWithoutMultiplier) *
                        (ppx.jumpAimStrain / totalAimStrain) * 0.95.pow(rank)
                performances.flowAim += pp.total * (pp.aim.pow(1.1) / totalStrainWithoutMultiplier) *
                        (ppx.flowAimStrain / totalAimStrain) * 0.95.pow(rank)
                performances.speed += pp.total * (pp.speed.pow(1.1) / totalStrainWithoutMultiplier) * 0.95.pow(rank)
                performances.accuracy += pp.total * (pp.accuracy.pow(1.1) / totalStrainWithoutMultiplier) * 0.95.pow(rank)
                performances.flashlight += pp.total * (pp.flashlight.pow(1.1) / totalStrainWithoutMultiplier) * 0.95.pow(rank)

                // assume that pp of the best performance that is out of rank 100 is the pp of current last best performance
                currentAverageBp += score.pp!! / bestScoresCount.toDouble()
                if (firstNewBestScoreRank != -1 && lastBestScore != null) {
                    if (rank >= firstNewBestScoreRank) {
                        if (newBestScores[rank] != null) { bpOffset -- }
                        val bestScoreWithOffset = bestScores[rank - bpOffset] ?: lastBestScore

                        newlyGainPp += (score.pp - bestScoreWithOffset.pp!!) * 0.95.pow(rank)
                        lastAverageBp += bestScoreWithOffset.pp / bestScoresCount.toDouble()
                    } else {
                        lastAverageBp += score.pp / bestScoresCount.toDouble()
                    }
                }

                val diff = score.pp - (bestScores[rank + 1] ?: lastBestScore!!).pp!!
                if (diff > maxTweenDiff) {
                    maxTweenDiff = diff
                    maxTweenDiffRank = rank
                }

                score.mods.parseMods().forEach m@ {
                    if (it == Mod.None) return@m
                    val prevCount = modUsage.putIfAbsent(it, 1)
                    if (prevCount != null) modUsage[it] = prevCount + 1
                }
            } }

            performances.bonus = performances.run { profile.statistics.pp!! - jumpAim - flowAim - speed - accuracy - flashlight }
            if (firstNewBestScoreRank == -1) { lastAverageBp = currentAverageBp }

            val bytes = withContext(graphicProcessorDispatcher) {
                Profile.drawProfilePanel(
                    profile, performances,
                    bestScores.entries.first().value, bestScores.entries.last().value,
                    newlyGainPp, currentAverageBp, lastAverageBp, maxTweenDiff, maxTweenDiffRank,
                    modUsage.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
                ).bytes(EncodedImageFormat.PNG)
            }
            val externalResource = bytes.toExternalResource("png")
            val image = group.uploadImage(externalResource)
            runInterruptible { externalResource.close() }
            atReply(image.toMessageChain())
        }
    }
}
