@file:Suppress("PrivatePropertyName")

package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.beatmap.DifficultyObject
import me.stageguard.obms.utils.lerp
import java.lang.Math.PI
import java.util.*
import kotlin.math.*

@Suppress("PrivatePropertyName")
abstract class Skill {
    private val DECAY_WEIGHT: Double = 0.9
    private val REDUCED_SECTION_COUNT: Int = 10
    private val REDUCED_STRAIN_BASELINE: Double = 0.75
    private val DIFFICULTY_MULTIPLIER: Double = 1.06

    private var currentStrain: Double = 1.0
    private var currentSectionPeak: Double = 1.0
    private val strainPeaks: MutableList<Double> = mutableListOf()
    private var prevTime: Optional<Double> = Optional.empty()

    abstract val strainDecayBase: Double
    abstract val skillMultiplier: Double

    fun difficultyValue(useOutdatedAlgorithm: Boolean = false) = run {
        var difficulty = 0.0
        var weight = 1.0

        strainPeaks.sortDescending()

        if(!useOutdatedAlgorithm) {
            for (i in 0 until strainPeaks.size.coerceAtMost(REDUCED_SECTION_COUNT)) {
                val scale: Double =
                    log10(lerp(1.0, 10.0, max(0.0, min(i.toDouble() / REDUCED_SECTION_COUNT, 1.0))))
                strainPeaks[i] *= lerp(REDUCED_STRAIN_BASELINE, 1.0, scale)
            }

            strainPeaks.sortDescending()
        }

        for (strain in strainPeaks) {
            difficulty += strain * weight
            weight *= DECAY_WEIGHT
        }

        if(useOutdatedAlgorithm) difficulty else difficulty * DIFFICULTY_MULTIPLIER
    }

    fun saveCurrentPeak() {
        strainPeaks.add(currentSectionPeak)
    }

    fun startNewSectionFrom(time: Double) {
        currentSectionPeak = peakStrain(time - prevTime.get())
    }

    fun process(current: DifficultyObject) {
        currentStrain *= strainDecay(current.delta)
        currentStrain += strainValueOf(current) * skillMultiplier
        currentSectionPeak = max(currentSectionPeak, currentStrain)
        prevTime = Optional.of(current.base.time)
    }

    private fun strainDecay(ms: Double) = strainDecayBase.pow(ms / 1000.0)

    private fun peakStrain(deltaTime: Double) = currentStrain * strainDecay(deltaTime)

    abstract fun strainValueOf(current: DifficultyObject) : Double

    @Suppress("NOTHING_TO_INLINE")
    inline fun applyDiminishingExp(value: Double) = value.pow(0.99)
}

class AimSkill : Skill() {
    private val AIM_SKILL_MULTIPLIER: Double = 26.25
    private val AIM_STRAIN_DECAY_BASE: Double = 0.15

    private val AIM_ANGLE_BONUS_BEGIN: Double = PI / 3.0
    private val TIMING_THRESHOLD: Double = 107.0

    override val strainDecayBase: Double
        get() = AIM_STRAIN_DECAY_BASE
    override val skillMultiplier: Double
        get() = AIM_SKILL_MULTIPLIER

    override fun strainValueOf(current: DifficultyObject): Double = if (current.base.isSpinner) { 0.0 } else {
        var result = 0.0

        current.prev.ifPresent { (prevJumpDist, prevStrainTime) ->
            current.angle.filter { it > AIM_ANGLE_BONUS_BEGIN }.ifPresent { angle ->
                val scale = 90.0

                val angleBonus = sqrt(
                    sin(angle - AIM_ANGLE_BONUS_BEGIN).pow(2) *
                            max(prevJumpDist - scale, 0.0) *
                            max(current.jumpDist - scale, 0.0)
                )

                result = 1.4 * applyDiminishingExp(max(angleBonus, 0.0)) / max(TIMING_THRESHOLD, prevStrainTime)
            }
        }

        val jumpDistExp = applyDiminishingExp(current.jumpDist)
        val travelDistExp = applyDiminishingExp(current.travelDist)

        val distExp = jumpDistExp + travelDistExp + sqrt(travelDistExp * jumpDistExp)

        max(result + distExp / max(current.strainTime, TIMING_THRESHOLD), distExp / current.strainTime)
    }
}

class SpeedSkill : Skill() {
    private val SPEED_SKILL_MULTIPLIER: Double = 1400.0
    private val SPEED_STRAIN_DECAY_BASE: Double = 0.3

    private val SINGLE_SPACING_THRESHOLD: Double = 125.0
    private val SPEED_ANGLE_BONUS_BEGIN: Double = 5.0 * PI / 6.0
    private val PI_OVER_4: Double = PI / 4.0
    private val PI_OVER_2: Double = PI / 2.0
    private val MIN_SPEED_BONUS: Double = 75.0
    private val MAX_SPEED_BONUS: Double = 45.0
    private val SPEED_BALANCING_FACTOR: Double = 40.0

    override val strainDecayBase: Double
        get() = SPEED_STRAIN_DECAY_BASE
    override val skillMultiplier: Double
        get() = SPEED_SKILL_MULTIPLIER

    override fun strainValueOf(current: DifficultyObject): Double = if (current.base.isSpinner) { 0.0 } else {
        val dist = min(SINGLE_SPACING_THRESHOLD, current.travelDist + current.jumpDist)
        val deltaTime = max(MAX_SPEED_BONUS, current.delta)

        var speedBonus = 1.0

        if (deltaTime < MIN_SPEED_BONUS) {
            speedBonus = 1.0 + ((MIN_SPEED_BONUS - deltaTime) / SPEED_BALANCING_FACTOR).pow(2)
        }

        var angleBonus = 1.0

        current.angle.filter { it < SPEED_ANGLE_BONUS_BEGIN }.ifPresent { angle ->
            val expBase = sin(1.5 * (SPEED_ANGLE_BONUS_BEGIN - angle))
            angleBonus = 1.0 + expBase * expBase / 3.57

            if (angle < PI_OVER_2) {
                angleBonus = 1.28

                if (dist < 90.0 && angle < PI_OVER_4) {
                    angleBonus += (1.0 - angleBonus) * min((90.0 - dist) / 10.0, 1.0)
                } else if (dist < 90.0) {
                    angleBonus += (1.0 - angleBonus) *
                            min((90.0 - dist) / 10.0, 1.0) *
                            sin((PI_OVER_2 - angle) / PI_OVER_4)
                }
            }
        }
        (1.0 + (speedBonus - 1.0) * 0.75) *
                angleBonus *
                (0.95 + speedBonus * (dist / SINGLE_SPACING_THRESHOLD).pow(3.5)) /
                current.strainTime
    }
}