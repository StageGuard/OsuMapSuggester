package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.beatmap.DifficultyObject
import java.lang.Math.PI
import java.util.*
import kotlin.math.*

@Suppress("PrivatePropertyName")
class Skill(
    val kind: SkillType,
    var currentStrain: Double = 1.0,
    var currentSectionPeak: Double = 1.0,
    val strainPeaks: MutableList<Double> = mutableListOf(),
    var prevTime: Optional<Double> = Optional.empty()
) {
    private val SPEED_SKILL_MULTIPLIER: Double = 1400.0
    private val SPEED_STRAIN_DECAY_BASE: Double = 0.3
    private val AIM_SKILL_MULTIPLIER: Double = 26.25
    private val AIM_STRAIN_DECAY_BASE: Double = 0.15
    private val DECAY_WEIGHT: Double = 0.9
    private val SINGLE_SPACING_THRESHOLD: Double = 125.0
    private val SPEED_ANGLE_BONUS_BEGIN: Double = 5.0 * PI / 6.0
    private val PI_OVER_4: Double = PI / 4.0
    private val PI_OVER_2: Double = PI / 2.0
    private val MIN_SPEED_BONUS: Double = 75.0
    private val MAX_SPEED_BONUS: Double = 45.0
    private val SPEED_BALANCING_FACTOR: Double = 40.0
    private val AIM_ANGLE_BONUS_BEGIN: Double = PI / 3.0
    private val TIMING_THRESHOLD: Double = 107.0
    private val REDUCED_SECTION_COUNT: Int = 10
    private val REDUCED_STRAIN_BASELINE: Double = 0.75
    private val DIFFICULTY_MULTIPLIER: Double = 1.06

    private val strainDecayBase get() = when(kind) {
        SkillType.Aim -> AIM_STRAIN_DECAY_BASE
        SkillType.Speed -> SPEED_STRAIN_DECAY_BASE
    }

    private val skillMultiplier get() = when(kind) {
        SkillType.Aim -> AIM_SKILL_MULTIPLIER
        SkillType.Speed -> SPEED_SKILL_MULTIPLIER
    }

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

    private fun strainValueOf(current: DifficultyObject) : Double = when(kind) {
        SkillType.Aim -> if (current.base.isSpinner) { 0.0 } else {
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
        SkillType.Speed -> if (current.base.isSpinner) { 0.0 } else {
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

    @Suppress("NOTHING_TO_INLINE")
    private inline fun applyDiminishingExp(value: Double) = value.pow(0.99)
}

@Suppress("NOTHING_TO_INLINE")
inline fun lerp(v0: Double, v1: Double, t: Double): Double {
    return (1 - t) * v0 + t * v1
}


enum class SkillType {
    Aim,
    Speed
}