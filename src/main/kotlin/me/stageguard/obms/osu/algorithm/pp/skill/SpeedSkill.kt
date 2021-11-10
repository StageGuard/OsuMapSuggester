package me.stageguard.obms.osu.algorithm.pp.skill

import me.stageguard.obms.osu.algorithm.pp.DifficultyObject
import me.stageguard.obms.osu.algorithm.pp.Skill
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.lerp
import kotlin.math.*

@Suppress("PrivatePropertyName")
class SpeedSkill(mods: ModCombination, val hitWindow: Double) : Skill<DifficultyObject>(mods) {
    private val SPEED_SKILL_MULTIPLIER: Double = 1400.0
    private val SPEED_STRAIN_DECAY_BASE: Double = 0.3

    private val SINGLE_SPACING_THRESHOLD: Double = 125.0
    private val RHYTHM_MULTIPLIER: Double = 0.75
    private val HISTORY_TIME_MAX: Int = 5000
    private val MIN_SPEED_BONUS: Double = 75.0
    private val SPEED_BALANCING_FACTOR: Double = 40.0

    private var currentStrain = 1.0
    private var currentRhythm = 1.0

    override val reducedSectionCount = 5
    override val difficultyMultiplier = 1.04
    override val prevObjQueueCapacity = 32

    override val strainDecayBase: Double
        get() = SPEED_STRAIN_DECAY_BASE
    override val skillMultiplier: Double
        get() = SPEED_SKILL_MULTIPLIER

    private fun calculateRhythmBonus(current: DifficultyObject) : Double {
        if (current.base.isSpinner)
            return 0.0

        var previousIslandSize = 0

        var rhythmComplexitySum = 0.0
        var islandSize = 1
        var startRatio = 0.0

        var firstDeltaSwitch = false

        var i = prevCount - 2
        while (i > 0) {
            val currObj = prevObjQueue[i - 1]!!
            val prevObj = prevObjQueue[i]!!
            val lastObj = prevObjQueue[i + 1]!!

            var currHistoricalDecay = 0.0.coerceAtLeast((HISTORY_TIME_MAX - (current.base.time - currObj.base.time))) / HISTORY_TIME_MAX

            if (currHistoricalDecay != 0.0) {
                currHistoricalDecay = ((prevCount - i) / prevCount.toDouble()).coerceAtMost(currHistoricalDecay)

                val currDelta = currObj.strainTime
                val prevDelta = prevObj.strainTime
                val lastDelta = lastObj.strainTime
                val currRatio = 1.0 + 6.0 * 0.5.coerceAtMost(sin(Math.PI / (prevDelta.coerceAtMost(currDelta) / prevDelta.coerceAtLeast(currDelta))).pow(2))

                var windowPenalty = 1.0.coerceAtMost(0.0.coerceAtLeast(abs(prevDelta - currDelta) - hitWindow * 0.6) / (hitWindow * 0.6))

                windowPenalty = 1.0.coerceAtMost(windowPenalty)

                var effectiveRatio = windowPenalty * currRatio

                if (firstDeltaSwitch) {
                    if (!(prevDelta > 1.25 * currDelta || prevDelta * 1.25 < currDelta)) {
                        if (islandSize < 7)
                            islandSize ++
                    } else {
                        if (prevObjQueue[i - 1]?.base?.isSlider == true) effectiveRatio *= 0.125
                        if (prevObjQueue[i]?.base?.isSlider == true) effectiveRatio *= 0.25
                        if (previousIslandSize == islandSize) effectiveRatio *= 0.25
                        if (previousIslandSize % 2 == islandSize % 2) effectiveRatio *= 0.50
                        if (lastDelta > prevDelta + 10 && prevDelta > currDelta + 10) effectiveRatio *= 0.125

                        rhythmComplexitySum += sqrt(effectiveRatio * startRatio) * currHistoricalDecay * sqrt(4.0 + islandSize) / 2 * sqrt(4.0 + previousIslandSize) / 2

                        startRatio = effectiveRatio

                        previousIslandSize = islandSize

                        if (prevDelta * 1.25 < currDelta) firstDeltaSwitch = false

                        islandSize = 1
                    }
                } else if (prevDelta > 1.25 * currDelta) {
                    firstDeltaSwitch = true
                    startRatio = effectiveRatio
                    islandSize = 1
                }
            }
            i --
        }

        return sqrt(4 + rhythmComplexitySum * RHYTHM_MULTIPLIER) / 2
    }

    override fun strainValueOf(current: DifficultyObject): Double {
        if (current.base.isSpinner)
            return 0.0

        var strainTime = current.strainTime
        val greatWindowFull = hitWindow * 2
        val speedWindowRatio = strainTime / greatWindowFull

        if (prevObj != null && strainTime < greatWindowFull && prevObj!!.strainTime > strainTime)
            strainTime = lerp(prevObj!!.strainTime, strainTime, speedWindowRatio)

        strainTime /= ((strainTime / greatWindowFull) / 0.93).coerceIn(0.92, 1.0)

        var speedBonus = 1.0

        if (strainTime < MIN_SPEED_BONUS)
            speedBonus = 1 + 0.75 * ((MIN_SPEED_BONUS - strainTime) / SPEED_BALANCING_FACTOR).pow(2)

        val distance = SINGLE_SPACING_THRESHOLD.coerceAtMost(current.travelDistance + current.jumpDistance)

        return (speedBonus + speedBonus * (distance / SINGLE_SPACING_THRESHOLD).pow(3.5)) / strainTime
    }

    override fun calculateInitialStrain(time: Double): Double {
        return (currentStrain * currentRhythm) * strainDecay(time - prevObj!!.base.time/* equal to prevTime.get() */)
    }

    override fun strainValueAt(current: DifficultyObject): Double {
        currentStrain *= strainDecay(current.delta)
        currentStrain += strainValueOf(current) * skillMultiplier

        currentRhythm = calculateRhythmBonus(current)

        return currentStrain * currentRhythm
    }
}