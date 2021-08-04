package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.beatmap.OsuStdObject
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.properties.Delegates

class DifficultyObject constructor(
    val base: OsuStdObject,
    prev: OsuStdObject,
    prevVals: Optional<Pair<Double, Double>>, // (jump_dist, strain_time)
    prevPrev: Optional<OsuStdObject>,
    clockRate: Double,
    scalingFactor: Double,
) {
    val prev: Optional<Pair<Double, Double>> = prevVals
    var jumpDist by Delegates.notNull<Double>()
    var travelDist by Delegates.notNull<Double>()
    var angle : Optional<Double> = Optional.empty()
    var delta by Delegates.notNull<Double>()
    var strainTime by Delegates.notNull<Double>()

    init {
        val delta = (base.time - prev.time) / clockRate
        val strainTime = max(delta, 50.0)

        val pos = base.position // stacked position
        val travelDist = prev.travelDist
        val prevCursorPos = prev.lazyEndPosition

        val jumpDist = if (base.isSpinner) { 0.0 } else { ((pos - prevCursorPos) * scalingFactor).length() }

        val angle = prevPrev.map {
            val prevPrevCursorPos = it.lazyEndPosition

            val v1 = prevPrevCursorPos - prev.position
            val v2 = pos - prevCursorPos

            val dot = v1.dotMultiply(v2)
            val det = v1.x * v2.y - v1.y * v2.x

            abs(atan2(det, dot))
        }

        this.jumpDist = jumpDist
        this.travelDist = travelDist
        this.angle = angle
        this.delta = delta
        this.strainTime = strainTime
    }
}