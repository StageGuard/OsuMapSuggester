package me.stageguard.obms.algorithm.beatmap

import me.stageguard.obms.algorithm.pp.DifficultyAttributes
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

class OsuStdObject constructor(
    h: HitObject,
    beatmap: Beatmap,
    radius: Double,
    scalingFactor: Double,
    ticks: MutableList<Double>,
    attributes: DifficultyAttributes,
    sliderState: SliderState,
) {
    var time by Delegates.notNull<Double>()
    lateinit var position: HitObjectPosition
    var stackHeight by Delegates.notNull<Double>()
    lateinit var kind: OsuStdObjectType

    init {
        attributes.maxCombo ++
        var stackHeight = 0.0

        when(h.kind) {
            is HitObjectType.Circle -> {
                time = h.startTime
                position = h.pos
                kind = OsuStdObjectType.Circle
                this.stackHeight = stackHeight
            }
            is HitObjectType.Slider -> {
                var lazyEndPosition = h.pos
                var travelDist = 0.0

                sliderState.update(h.startTime)

                val approxFollowCircleRadius = radius * 3.0;
                var tickDistance = 100.0 * beatmap.sliderMultiplier / beatmap.sliderTickRate

                if(beatmap.version >= 8) {
                    tickDistance /= min(1000.0, max(10.0, 100.0 / sliderState.speedMultiply)) / 100.0
                }

                val duration = h.kind.repeatTimes.toDouble() * sliderState.beatLength * h.kind.pixelLength / (beatmap.sliderMultiplier * sliderState.speedMultiply) / 100.0;
                val spanDuration = duration / h.kind.repeatTimes.toDouble()
            }
        }
    }
}

sealed class OsuStdObjectType {
    object Circle : OsuStdObjectType()
    class Slider(
        val endTime: Double, val endPosition: HitObjectPosition,
        val lazyEndPosition: HitObjectPosition, val travelDist: Double
    ) : OsuStdObjectType()
    class Spinner(val endTime: Double) : OsuStdObjectType()
}