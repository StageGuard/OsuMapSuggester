package me.stageguard.obms.osu.algorithm.pp

import me.stageguard.obms.osu.processor.beatmap.OsuStdObject
import me.stageguard.obms.osu.processor.beatmap.OsuStdObjectType
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.properties.Delegates

open class DifficultyObject constructor(
    val base: OsuStdObject,
    prev: OsuStdObject,
    prevPrev: Optional<OsuStdObject>,
    clockRate: Double,
    scalingFactor: Double,
) {
    var jumpDistance by Delegates.notNull<Double>()
    var angle : Optional<Double> = Optional.empty()
    var delta by Delegates.notNull<Double>()
    var strainTime by Delegates.notNull<Double>()
    var movementTime by Delegates.notNull<Double>()
    var movementDistance by Delegates.notNull<Double>()
    var travelTime = 0.0
    var travelDistance = 0.0

    init {
        delta = (base.time - prev.time) / clockRate
        strainTime = delta.coerceAtLeast(MIN_DELTA_TIME)
        jumpDistance = ((base.position - prev.lazyEndPosition) * scalingFactor).length()

        if (prev.isSlider) {
            travelDistance = prev.travelDist
            travelTime = (prev.travelTime / clockRate).coerceAtLeast(MIN_DELTA_TIME)
            movementTime = (strainTime - travelTime).coerceAtLeast(MIN_DELTA_TIME)

            val tailJumpDistance = (base.position - prev.endPosition).length() * scalingFactor
            movementDistance = min(
                jumpDistance - (MAXIMUM_SLIDER_RADIUS - ASSUMED_SLIDER_RADIUS),
                tailJumpDistance - MAXIMUM_SLIDER_RADIUS
            ).coerceAtLeast(0.0)
        } else {
            movementTime = strainTime
            movementDistance = jumpDistance
        }

        angle = kotlin.run a@ {
            val obj = prevPrev.orElseGet { null } ?: return@a Optional.empty()
            if (obj.isSpinner) return@a Optional.empty()

            val prevPrevCursorPos = obj.lazyEndPosition

            val v1 = prevPrevCursorPos - prev.position
            val v2 = base.position - prev.lazyEndPosition

            val dot = v1.dotMultiply(v2)
            val det = v1.x * v2.y - v1.y * v2.x

            return@a Optional.of(abs(atan2(det, dot)))
        }
    }
}