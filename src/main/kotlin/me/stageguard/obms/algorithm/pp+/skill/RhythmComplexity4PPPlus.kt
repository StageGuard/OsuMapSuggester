@file:Suppress("PackageName")

package me.stageguard.obms.algorithm.`pp+`.skill

import me.stageguard.obms.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.algorithm.beatmap.ModCombination
import me.stageguard.obms.algorithm.beatmap.OsuStdObjectType
import me.stageguard.obms.algorithm.pp.NORMALIZED_RADIUS
import me.stageguard.obms.algorithm.pp.Skill
import me.stageguard.obms.utils.*
import kotlin.math.pow
import kotlin.math.tanh


class RhythmComplexity4PPPlus(mods: ModCombination) : Skill<DifficultyObject4PPPlus>(mods) {

    private var circleCount = 0
    private var noteIndex = 0
    private var isPreviousOffbeat: Boolean = false
    private val previousDoubles: MutableList<Int> = mutableListOf()
    private var difficultyTotal: Double = 0.0

    override fun difficultyValue(useOutdatedAlgorithm: Boolean): Double {
        val lengthRequirement = tanh(circleCount / 50.0)
        return 1 + difficultyTotal / circleCount * lengthRequirement
    }

    override fun process(current: DifficultyObject4PPPlus) {
        if (current.base.kind is OsuStdObjectType.Circle) {
            difficultyTotal += calculateRhythmBonus(current)
            circleCount++
        } else {
            isPreviousOffbeat = false
        }
        noteIndex++
    }

    private fun calculateRhythmBonus(current: DifficultyObject4PPPlus): Double {
        var rhythmBonus = 0.05 * current.flow

        if (current.prevDifficultyObject.isEmpty)
            return rhythmBonus

        when (current.prevDifficultyObject.get().base.kind) {
            is OsuStdObjectType.Circle -> rhythmBonus += calculateCircleToCircleRhythmBonus(current)
            is OsuStdObjectType.Slider -> rhythmBonus += calculateSliderToCircleRhythmBonus(current)
            else -> isPreviousOffbeat = false
        }

        return rhythmBonus
    }

    private fun calculateCircleToCircleRhythmBonus(current: DifficultyObject4PPPlus): Double {
        var rhythmBonus = 0.0

        current.prevDifficultyObject.ifPresent { previous ->
            if (isPreviousOffbeat && isRatioEqualGreater(1.5, current.gapTime, previous.gapTime)) {
                rhythmBonus = 5.0
                if(previousDoubles.isNotEmpty()) {
                    previousDoubles.subList(0.coerceAtLeast(previousDoubles.size - 10), previousDoubles.lastIndex)
                        .forEach { previousDouble ->
                            if (previousDouble > 0)
                                rhythmBonus *= 1 - 0.5 * 0.9.pow(noteIndex - previousDouble)
                            else
                                rhythmBonus = 5.0
                        }
                }

                previousDoubles.add(noteIndex)
            } else if (isRatioEqual(0.667, current.gapTime, previous.gapTime)) {
                rhythmBonus = 4 + 8 * current.flow
                if (current.flow > 0.8) previousDoubles.add(-1)
            } else if (isRatioEqual(0.333, current.gapTime, previous.gapTime)) {
                rhythmBonus = 0.4 + 0.8 * current.flow
            } else if (isRatioEqual(0.5, current.gapTime, previous.gapTime) || isRatioEqualLess(
                    0.25,
                    current.gapTime,
                    previous.gapTime
                )
            ) {
                rhythmBonus = 0.1 + 0.2 * current.flow
            }

            isPreviousOffbeat = if (isRatioEqualLess(0.667, current.gapTime, previous.gapTime) && current.flow > 0.8)
                true
            else if (isRatioEqual(1.0, current.gapTime, previous.gapTime) && current.flow > 0.8)
                !isPreviousOffbeat
            else
                false
        }

        return rhythmBonus
    }

    private fun calculateSliderToCircleRhythmBonus(current: DifficultyObject4PPPlus): Double {
        var rhythmBonus = 0.0
        val sliderMS = current.strainTime - current.gapTime

        if (isRatioEqual(0.5, current.gapTime, sliderMS) || isRatioEqual(0.25, current.gapTime, sliderMS)) {
            val endFlow = calculateSliderEndFlow(current)
            rhythmBonus = 0.3 * endFlow

            isPreviousOffbeat = endFlow > 0.8
        } else {
            isPreviousOffbeat = false
        }

        return rhythmBonus
    }

    private fun calculateSliderEndFlow(current: DifficultyObject4PPPlus): Double {
        val streamBpm = 15000 / current.gapTime
        val isFlowSpeed = transitionToTrue(streamBpm, 120.0, 30.0)

        val distanceOffset = (tanh((streamBpm - 140) / 20) + 2) * NORMALIZED_RADIUS
        val isFlowDistance = transitionToFalse(current.jumpDist, distanceOffset, NORMALIZED_RADIUS)

        return isFlowSpeed * isFlowDistance
    }

    // unnecessary fields and methods
    override val strainDecayBase: Double = 0.0
    override val skillMultiplier: Double = 0.0
    override fun strainValueOf(current: DifficultyObject4PPPlus) = 0.0
}