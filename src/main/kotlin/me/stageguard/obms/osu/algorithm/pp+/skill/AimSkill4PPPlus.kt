@file:Suppress("PackageName")

package me.stageguard.obms.osu.algorithm.`pp+`.skill

import me.stageguard.obms.osu.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.osu.processor.beatmap.HitObjectPosition
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.algorithm.pp.NORMALIZED_RADIUS
import me.stageguard.obms.osu.algorithm.pp.Skill
import me.stageguard.obms.utils.isRatioEqual
import me.stageguard.obms.utils.isRatioEqualGreater
import me.stageguard.obms.utils.transitionToTrue
import java.util.*
import kotlin.math.*

@Suppress("PrivatePropertyName")
open class AimSkill4PPPlus(mods: ModCombination) : Skill<DifficultyObject4PPPlus>(mods) {
    private val AIM_SKILL_MULTIPLIER: Double = 1059.0
    private val AIM_STRAIN_DECAY_BASE: Double = 0.15
    @Suppress("SpellCheckingInspection")
    private val PLAYFIELD_X: Int = 512
    @Suppress("SpellCheckingInspection")
    private val PLAYFIELD_Y: Int = 384

    private val preemptHitObjects = LinkedList<DifficultyObject4PPPlus>()

    override val strainDecayBase: Double
        get() = AIM_STRAIN_DECAY_BASE
    override val skillMultiplier: Double
        get() = AIM_SKILL_MULTIPLIER

    override fun strainValueOf(current: DifficultyObject4PPPlus): Double {
        val jumpAim = calculateJumpAimValue(current)
        val flowAim = calculateFlowAimValue(current)

        val aimValue =  (jumpAim + flowAim) * calculateSmallCircleBonus(current.base.radius)
        val readingMultiplier = calculateReadingMultiplier(current)

        return aimValue * readingMultiplier
    }

    protected fun calculateJumpAimValue(current: DifficultyObject4PPPlus) : Double {
        if (current.flow == 1.0)
            return 0.0

        val distance = current.movementDistance / NORMALIZED_RADIUS

        val jumpAimBase = distance / current.movementTime

        var locationWeight = 1.0
        current.prevDifficultyObject.ifPresent { prevObj ->
            locationWeight = calculateLocationWeight(current.base.position, prevObj.base.position)
        }

        val angleWeight = calculateJumpAngleWeight(
            current.angle, current.movementTime,
            current.prevDifficultyObject.run { if(this.isPresent) this.get().movementTime else 0.0 },
            current.prevDifficultyObject.run { if(this.isPresent) this.get().movementDistance else 0.0 }
        )
        val patternWeight = calculateJumpPatternWeight(current,
            mutableListOf<DifficultyObject4PPPlus>().also { list ->
                current.prevPrevDifficultyObject.ifPresent { list.add(it) }
                current.prevDifficultyObject.ifPresent { list.add(it) }
            }
        )
        val jumpAim = jumpAimBase * angleWeight * patternWeight * locationWeight
        return jumpAim * (1.0 - current.flow)
    }

    protected fun calculateFlowAimValue(current: DifficultyObject4PPPlus) : Double
    {
        if (current.flow == 0.0)
            return 0.0

        val distance = current.movementDistance / NORMALIZED_RADIUS

        val flowAimBase = (tanh(distance - 2.0) + 1.0) * 2.5 / current.movementTime + distance / 5.0 / current.movementTime

        var locationWeight = 1.0
        current.prevDifficultyObject.ifPresent { prevObj ->
            locationWeight = calculateLocationWeight(current.base.position, prevObj.base.position)
        }

        val angleWeight = calculateFlowAngleWeight(current.angle)
        val patternWeight = calculateFlowPatternWeight(current, current.prevDifficultyObject, distance)

        val flowAim = flowAimBase * angleWeight * patternWeight * (1.0 + (locationWeight - 1.0) / 2.0)
        return flowAim * current.flow
    }

    protected fun calculateReadingMultiplier(current: DifficultyObject4PPPlus) : Double {
        while (preemptHitObjects.isNotEmpty() && preemptHitObjects.last().startTime < current.startTime - current.preempt) {
            preemptHitObjects.removeLast()
        }

        var readingStrain = 0.0

        preemptHitObjects.forEach { previousObject ->
            readingStrain += calculateReadingDensity(previousObject.baseFlow, previousObject.movementDistance)
        }

        val densityBonus = readingStrain.pow(1.5) / 100.0
        val readingMultiplier = if (mods.hd()) 1.05 + densityBonus * 1.5 else 1.0 + densityBonus

        val flashlightMultiplier = calculateFlashlightMultiplier(mods.fl(), current.rawJumpDist, current.base.radius)
        val highApproachRateMultiplier = calculateHighApproachRateMultiplier(current.preempt)

        preemptHitObjects.push(current)

        return readingMultiplier * flashlightMultiplier * highApproachRateMultiplier
    }

    private fun calculateJumpPatternWeight(current: DifficultyObject4PPPlus, previousTwoObjects: List<DifficultyObject4PPPlus>) : Double {
        var jumpPatternWeight = 1.0
        previousTwoObjects.asReversed().forEachIndexed { i, previousObject ->
            var velocityWeight = 1.05
            if (previousObject.movementDistance > 0) {
                val velocityRatio = current.movementDistance / current.movementTime / (previousObject.movementDistance / previousObject.movementTime) - 1.0
                if (velocityRatio <= 0)
                    velocityWeight = 1.0 + velocityRatio * velocityRatio / 2.0
                else if (velocityRatio < 1)
                    velocityWeight = 1.0 + (-cos(velocityRatio * Math.PI) + 1.0) / 40
            }

            var angleWeight = 1.0
            if (isRatioEqual(1.0, current.movementTime, previousObject.movementTime) && current.angle.isPresent && previousObject.angle.isPresent
            ) {
                val angleChange = abs(current.angle.get()) - abs(previousObject.angle.get())
                angleWeight = if (abs(angleChange) >= Math.PI / 1.5)
                    1.05
                else
                    1.0 + (-sin(cos(angleChange * 1.5) * Math.PI / 2.0) + 1.0) / 40.0
            }

            jumpPatternWeight *= (velocityWeight * angleWeight).pow(2 - i)
        }
        var distanceRequirement = 0.0
        if (previousTwoObjects.isNotEmpty()) {
            distanceRequirement = calculateDistanceRequirement(
                current.movementTime,
                previousTwoObjects.last().movementTime,
                previousTwoObjects.last().movementDistance
            )
        }

        return 1.0 + (jumpPatternWeight - 1.0) * distanceRequirement
    }

    private fun calculateFlowPatternWeight(
        current: DifficultyObject4PPPlus, previousObject: Optional<DifficultyObject4PPPlus>, distance: Double
    ) : Double {
        if (previousObject.isEmpty) return 1.0

        var distanceRatio = 1.0
        if (previousObject.get().movementDistance > 0) distanceRatio = current.movementDistance / previousObject.get().movementDistance - 1.0

        var distanceBonus = 1.0
        if (distanceRatio <= 0)
            distanceBonus = distanceRatio * distanceRatio
        else if (distanceRatio < 1)
            distanceBonus = (-cos(distanceRatio * Math.PI) + 1.0) / 2.0

        var angleBonus = 0.0
        if (!current.angle.isPresent && !previousObject.get().angle.isPresent) {
            if (current.angle.get() > 0 && previousObject.get().angle.get() < 0 || current.angle.get() < 0 && previousObject.get().angle.get() > 0) {
                val angleChange: Double = if (abs(current.angle.get()) > (Math.PI - abs(previousObject.get().angle.get())) / 2.0)
                    Math.PI - abs(current.angle.get())
                else
                    abs(previousObject.get().angle.get()) + abs(current.angle.get())
                angleBonus = (-cos(sin(angleChange / 2.0) * Math.PI) + 1.0) / 2.0
            } else if (abs(current.angle.get()) < abs(previousObject.get().angle.get())) {
                val angleChange: Double = current.angle.get() - previousObject.get().angle.get()
                angleBonus = (-cos(sin(angleChange / 2.0) * Math.PI) + 1.0) / 2.0
            }
            if (angleBonus > 0) {
                val angleChange: Double = abs(current.angle.get()) - abs(previousObject.get().angle.get())
                angleBonus = angleBonus.coerceAtMost((-cos(sin(angleChange / 2) * Math.PI) + 1) / 2)
            }
        }
        val isStreamJump: Double = transitionToTrue(distanceRatio, 0.0, 1.0)
        val distanceWeight = (1.0 + distanceBonus) * calculateStreamJumpWeight(current.movementDistance, isStreamJump, distance)
        val angleWeight = 1.0 + angleBonus * (1.0 - isStreamJump)

        return 1.0 + (distanceWeight * angleWeight - 1.0) * previousObject.get().flow
    }

    private fun calculateJumpAngleWeight(angle: Optional<Double>, deltaTime: Double, previousDeltaTime: Double, previousDistance: Double) =
        if(angle.isEmpty) 1.0 else {
            val distanceRequirement = calculateDistanceRequirement(deltaTime, previousDeltaTime, previousDistance)
            1.0 + (-sin(cos(angle.get()) * Math.PI / 2.0) + 1.0) / 10 * distanceRequirement
        }

    private fun calculateFlowAngleWeight(angle: Optional<Double>) = if(angle.isEmpty) 1.0 else 1.0 + (cos(angle.get()) + 1.0) / 10.0

    private fun calculateStreamJumpWeight(jumpDistance: Double, isStreamJump: Double, distance: Double) =
        if (jumpDistance > 0) {
            val flowAimRevertFactor: Double = 1.0 / ((tanh(distance - 2.0) + 1.0) * 2.5 + distance / 5.0)
            (1.0 - isStreamJump) * 1.0 + isStreamJump * flowAimRevertFactor * distance
        } else 1.0

    private fun calculateLocationWeight(position: HitObjectPosition, previousPosition: HitObjectPosition) : Double {

        var (x, y) = (position + previousPosition) * 0.5

        x -= PLAYFIELD_X / 2.0
        y -= PLAYFIELD_Y / 2.0

        val angle = Math.PI / 3.0
        val a: Double = (x * cos(angle) + y * sin(angle)) / 750.0
        val b: Double = (x * sin(angle) - y * cos(angle)) / 1000.0

        val locationBonus = a * a + b * b

        return 1.0 + locationBonus
    }

    private fun calculateDistanceRequirement(deltaTime: Double, previousDeltaTime: Double, previousDistance: Double) =
        if (isRatioEqualGreater(1.0, deltaTime, previousDeltaTime)) {
            val overlapDistance: Double = previousDeltaTime / deltaTime * NORMALIZED_RADIUS * 2.0
            transitionToTrue(previousDistance, 0.0, overlapDistance)
        } else 0.0

    protected fun calculateSmallCircleBonus(radius: Double) = 1.0 + 120.0 / radius.pow(2)

    private fun calculateReadingDensity(previousBaseFlow: Double, previousJumpDistance: Double) =
        (1.0 - previousBaseFlow * 0.75) * (1.0 + previousBaseFlow * 0.5 * previousJumpDistance / NORMALIZED_RADIUS)

    private fun calculateFlashlightMultiplier(fl: Boolean, rawJumpDistance: Double, radius: Double) =
        if(!fl) 1.0 else 1.0 + 0.3 * transitionToTrue(rawJumpDistance, PLAYFIELD_Y / 4.0, radius)

    private fun calculateHighApproachRateMultiplier(preempt: Double) = 1.0 + (-tanh((preempt - 325.0) / 30.0) + 1.0) / 15.0
}