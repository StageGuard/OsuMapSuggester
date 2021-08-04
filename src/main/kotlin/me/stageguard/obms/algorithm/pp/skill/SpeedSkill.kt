package me.stageguard.obms.algorithm.pp.skill

import me.stageguard.obms.algorithm.beatmap.DifficultyObject
import me.stageguard.obms.algorithm.pp.Skill
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class SpeedSkill : Skill() {
    private val SPEED_SKILL_MULTIPLIER: Double = 1400.0
    private val SPEED_STRAIN_DECAY_BASE: Double = 0.3

    private val SINGLE_SPACING_THRESHOLD: Double = 125.0
    private val SPEED_ANGLE_BONUS_BEGIN: Double = 5.0 * Math.PI / 6.0
    private val PI_OVER_4: Double = Math.PI / 4.0
    private val PI_OVER_2: Double = Math.PI / 2.0
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