package me.stageguard.obms.osu.algorithm.pp.skill

import me.stageguard.obms.osu.algorithm.pp.DifficultyObject
import me.stageguard.obms.osu.algorithm.pp.Skill
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import kotlin.math.*

class AimSkill(mods: ModCombination, private val withSliders: Boolean = true) : Skill<DifficultyObject>(mods) {
    private val WIDE_ANGLE_MULTIPLIER = 1.5
    private val ACUTE_ANGLE_MULTIPLIER = 2.0
    private val SLIDER_MULTIPLIER = 1.5
    private val VELOCITY_CHANGE_MULTIPLIER = 0.75

    override val strainDecayBase = 0.15
    override val skillMultiplier = 23.25

    override fun strainValueOf(current: DifficultyObject): Double {
        if (current.base.isSpinner || !isPrevQueueHasFirst2Objs || prevObj?.base?.isSpinner == true)
            return 0.0

        var currVelocity = current.jumpDistance / current.strainTime
        if (prevObj?.base?.isSlider == true && withSliders) {
            val movementVelocity = current.movementDistance / current.movementTime
            val travelVelocity = current.travelDistance / current.travelTime
            currVelocity = max(currVelocity, movementVelocity + travelVelocity)
        }

        var prevVelocity = prevObj!!.jumpDistance / prevObj!!.strainTime
        if (prevPrevObj?.base?.isSlider == true && withSliders) {
            val movementVelocity = prevObj!!.movementDistance / prevObj!!.movementTime
            val travelVelocity = prevObj!!.travelDistance / prevObj!!.travelTime
            prevVelocity = max(prevVelocity, movementVelocity + travelVelocity)
        }

        var wideAngleBonus = 0.0
        var acuteAngleBonus = 0.0
        var velocityChangeBonus = 0.0
        val sliderBonus = if (current.travelTime != 0.0) current.travelDistance / current.travelTime else 0.0

        var aimStrain = currVelocity

        if (max(current.strainTime, prevObj!!.strainTime) < 1.25 * min(current.strainTime, prevObj!!.strainTime)) {
            if (current.angle.isPresent && prevObj!!.angle.isPresent && prevPrevObj!!.angle.isPresent) {
                val currAngle: Double = current.angle.get()
                val lastAngle: Double = prevObj!!.angle.get()
                val lastLastAngle: Double = prevPrevObj!!.angle.get()

                val angleBonus: Double = min(currVelocity, prevVelocity)
                wideAngleBonus = calcWideAngleBonus(currAngle)
                acuteAngleBonus = calcAcuteAngleBonus(currAngle)
                if (current.strainTime > 100) {
                    acuteAngleBonus = 0.0
                } else {
                    acuteAngleBonus *= (calcAcuteAngleBonus(lastAngle) * min(angleBonus, 125 / current.strainTime)
                        * sin(Math.PI / 2 * min(1.0, (100 - current.strainTime) / 25)).pow(2)
                        * sin(Math.PI / 2 * (current.jumpDistance.coerceIn(50.0, 100.0) - 50.0) / 50.0).pow(2))
                }

                wideAngleBonus *= angleBonus * (1 - min(wideAngleBonus, calcWideAngleBonus(lastAngle).pow(3)))
                acuteAngleBonus *= 0.5 + 0.5 * (1 - min(acuteAngleBonus, calcAcuteAngleBonus(lastLastAngle).pow(3)))
            }
        }

        if (max(prevVelocity, currVelocity) != 0.0) {
            prevVelocity = (prevObj!!.jumpDistance + prevObj!!.travelDistance) / prevObj!!.strainTime
            currVelocity = (current.jumpDistance + current.travelDistance) / current.strainTime

            val distRatio: Double = sin(
                Math.PI / 2 * abs(prevVelocity - currVelocity) / max(prevVelocity, currVelocity)
            ).pow(2)

            val overlapVelocityBuff: Double = min(
                125 / min(current.strainTime, prevObj!!.strainTime), abs(prevVelocity - currVelocity)
            )

            val nonOverlapVelocityBuff: Double = (abs(prevVelocity - currVelocity)
                    * sin(Math.PI / 2 * min(1.0, min(current.jumpDistance, prevObj!!.jumpDistance) / 100.0)
            ).pow(2))

            velocityChangeBonus = max(overlapVelocityBuff, nonOverlapVelocityBuff) * distRatio

            velocityChangeBonus *= (min(current.strainTime, prevObj!!.strainTime) / max(
                current.strainTime, prevObj!!.strainTime
            )).pow(2)
        }

        aimStrain += max(
            acuteAngleBonus * ACUTE_ANGLE_MULTIPLIER,
            wideAngleBonus * WIDE_ANGLE_MULTIPLIER + velocityChangeBonus * VELOCITY_CHANGE_MULTIPLIER
        )
        if (withSliders) aimStrain += sliderBonus * SLIDER_MULTIPLIER

        return aimStrain
    }
    
    private fun calcWideAngleBonus(angle: Double) =
        sin(3.0 / 4 * (angle.coerceIn(1.0 / 6 * Math.PI, 5.0 / 6 * Math.PI) - Math.PI / 6)).pow(2)

    private fun calcAcuteAngleBonus(angle: Double) = 1 - calcWideAngleBonus(angle)
}