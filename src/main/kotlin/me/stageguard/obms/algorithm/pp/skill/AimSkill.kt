package me.stageguard.obms.algorithm.pp.skill

import me.stageguard.obms.algorithm.beatmap.DifficultyObject
import me.stageguard.obms.algorithm.pp.Skill
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AimSkill : Skill() {
    private val AIM_SKILL_MULTIPLIER: Double = 26.25
    private val AIM_STRAIN_DECAY_BASE: Double = 0.15

    private val AIM_ANGLE_BONUS_BEGIN: Double = Math.PI / 3.0
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