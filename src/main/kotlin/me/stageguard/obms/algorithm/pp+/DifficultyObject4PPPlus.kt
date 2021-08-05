@file:Suppress("PackageName")

package me.stageguard.obms.algorithm.`pp+`

import me.stageguard.obms.algorithm.beatmap.OsuStdObject
import me.stageguard.obms.algorithm.beatmap.OsuStdObjectType
import me.stageguard.obms.algorithm.pp.DifficultyObject
import me.stageguard.obms.algorithm.pp.NORMALIZED_RADIUS
import me.stageguard.obms.utils.isRatioEqualLess
import me.stageguard.obms.utils.isRoughlyEqual
import me.stageguard.obms.utils.transitionToFalse
import me.stageguard.obms.utils.transitionToTrue
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.properties.Delegates

@Suppress("SelfReferenceConstructorParameter")
class DifficultyObject4PPPlus(
    base: OsuStdObject, prev: OsuStdObject,
    prevVals: Optional<Pair<Double, Double>>, prevPrev: Optional<OsuStdObject>,
    val prevDifficultyObject: Optional<DifficultyObject4PPPlus>,
    val prevPrevDifficultyObject: Optional<DifficultyObject4PPPlus>,
    clockRate: Double, scalingFactor: Double
) : DifficultyObject(base, prev, prevVals, prevPrev, clockRate, scalingFactor) {
    var rawJumpDist by Delegates.notNull<Double>()
    var preempt by Delegates.notNull<Double>()
    var angleLeniency = 0.0
    var baseFlow by Delegates.notNull<Double>()
    var flow by Delegates.notNull<Double>()
    var lastTwoStrainTime by Delegates.notNull<Double>()
    var gapTime by Delegates.notNull<Double>()

    private val streamBpm: Double
        get() = 15000.0 / strainTime

    init {
        this.preempt = base.timePreempt / clockRate

        this.lastTwoStrainTime = if (prevPrev.isEmpty) 100.0 else {
            100.0.coerceAtLeast((base.time - prevPrev.get().time) / clockRate)
        }

        this.rawJumpDist = if (base.isSpinner) 0.0 else {
            (base.position - prev.lazyEndPosition).length()
        }

        this.gapTime = when (prev.kind) {
            is OsuStdObjectType.Circle -> strainTime
            is OsuStdObjectType.Slider -> 50.0.coerceAtLeast((base.time - prev.endTime) / clockRate)
            is OsuStdObjectType.Spinner -> 50.0.coerceAtLeast((base.time - prev.endTime) / clockRate)
            else -> -1.0
        }

        this.baseFlow = calculateBaseFlow()
        this.flow = calculateFlow()
    }

    private fun calculateBaseFlow(): Double {
        if (prevDifficultyObject.isEmpty || isRatioEqualLess(0.667, strainTime, prevDifficultyObject.get().strainTime))
            return calculateSpeedFlow() * calculateDistanceFlow() // No angle checks for the first actual note of the stream.

        if (isRoughlyEqual(strainTime, prevDifficultyObject.get().strainTime))
            return calculateSpeedFlow() * calculateDistanceFlow(calculateAngleScalingFactor(angle))

        return 0.0
    }

    private fun calculateSpeedFlow() = transitionToTrue(streamBpm, 90.0, 30.0)

    private fun calculateDistanceFlow(angleScalingFactor: Double = 1.0): Double {
        val distanceOffset = (tanh((streamBpm - 140) / 20) + 2) * NORMALIZED_RADIUS
        return transitionToFalse(jumpDist, distanceOffset * angleScalingFactor, distanceOffset)
    }

    private fun calculateAngleScalingFactor(angle: Optional<Double>) = if (angle.isPresent) {
        val angleScalingFactor = (-sin(cos(angle.get()) * Math.PI / 2) + 3) / 4
        angleScalingFactor + (1 - angleScalingFactor) * prevDifficultyObject.get().angleLeniency
    } else 0.5

    private fun calculateFlow(): Double {
        if (prevDifficultyObject.isEmpty)
            return baseFlow

        val irregularFlow = calculateIrregularFlow()
        angleLeniency = (1 - baseFlow) * irregularFlow

        return baseFlow.coerceAtLeast(irregularFlow)
    }

    private fun calculateIrregularFlow(): Double {
        var irregularFlow = calculateExtendedDistanceFlow()

        if (isRoughlyEqual(strainTime, prevDifficultyObject.get().strainTime))
            irregularFlow *= prevDifficultyObject.get().baseFlow
        else
            irregularFlow = 0.0

        prevPrevDifficultyObject.ifPresent {
            if (isRoughlyEqual(strainTime, it.strainTime))
                irregularFlow *= it.baseFlow
            else
                irregularFlow = 0.0
        }

        return irregularFlow
    }

    private fun calculateExtendedDistanceFlow(): Double {
        val distanceOffset = (tanh((streamBpm - 140) / 20) * 1.75 + 2.75) * NORMALIZED_RADIUS
        return transitionToFalse(jumpDist, distanceOffset, distanceOffset)
    }
}