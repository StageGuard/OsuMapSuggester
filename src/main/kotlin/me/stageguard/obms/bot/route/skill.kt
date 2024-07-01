package me.stageguard.obms.bot.route

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.algorithm.`pp+`.PPPlusCalculator
import me.stageguard.obms.osu.algorithm.`pp+`.PPPlusResult
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.MessageRoute.routeLock
import me.stageguard.obms.bot.calculatorProcessorDispatcher
import me.stageguard.obms.bot.refactoredExceptionCatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.cache.BeatmapCache
import kotlin.math.pow

/*
fun GroupMessageSubscribersBuilder.skill() {
    routeLock(startWithIgnoreCase(".skill")) {
        OsuMapSuggester.launch(
            CoroutineName("Command \"skill\" of ${sender.id}") + refactoredExceptionCatcher
        ) {
            val scores = OsuWebApi.userScore(user = sender.id, type = "best", limit = 100).rightOrThrowLeft()
            withContext(calculatorProcessorDispatcher) {
                val scoreSkills = scores.map { score ->
                    val beatmap = BeatmapCache.getBeatmap(score.beatmap!!.id).rightOrThrowLeft()
                    PPPlusCalculator.of(beatmap)
                        .accuracy(score.accuracy * 100.0)
                        .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                        .mods(score.mods.parseMods())
                        .combo(score.maxCombo)
                        .n300(score.statistics.count300)
                        .n100(score.statistics.count100)
                        .n50(score.statistics.count50)
                        .misses(score.statistics.countMiss)
                        .calculate()
                }.asSequence()

                fun Sequence<PPPlusResult>.calculateWeightedSkill(skill: PPPlusResult.() -> Double) =
                    map(skill).sortedDescending().foldIndexed(0.0) { idx, last, cur ->
                        last + cur * 0.95.pow(idx)
                    }

                val result = PPPlusResult(
                    total = scoreSkills.calculateWeightedSkill { total },
                    aim = scoreSkills.calculateWeightedSkill { aim },
                    jumpAim = scoreSkills.calculateWeightedSkill { jumpAim },
                    flowAim = scoreSkills.calculateWeightedSkill { flowAim },
                    speed = scoreSkills.calculateWeightedSkill { speed },
                    stamina = scoreSkills.calculateWeightedSkill { stamina },
                    accuracy = scoreSkills.calculateWeightedSkill { accuracy },
                    flashlight = 0.0,
                    precision = 0.0, //precision calculation have bugs.
                )


                atReply("""
            Total       ${result.total}
            Aim         ${result.aim}
            Aim(Jump)   ${result.jumpAim}
            Aim(Flow)   ${result.flowAim}
            Speed       ${result.speed}
            Accuracy    ${result.accuracy}
            Stamina     ${result.stamina}
        """.trimIndent())
            }
        }
    }
}*/
