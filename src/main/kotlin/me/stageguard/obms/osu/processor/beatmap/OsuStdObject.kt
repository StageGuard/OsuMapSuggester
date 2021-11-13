package me.stageguard.obms.osu.processor.beatmap

import me.stageguard.obms.osu.algorithm.pp.*
import me.stageguard.obms.utils.concatenate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.properties.Delegates

const val LEGACY_LAST_TICK_OFFSET = 36.0

@Suppress("PrivatePropertyName")
class OsuStdObject constructor(
    h: HitObject,
    beatmap: Beatmap,
    mods: ModCombination,
    var radius: Double,
    scalingFactor: Double,
    attributes: DifficultyAttributes,
    sliderState: SliderState,
) {
    var time by Delegates.notNull<Double>()
    lateinit var position: HitObjectPosition
    var stackHeight by Delegates.notNull<Double>()
    var kind: OsuStdObjectType = OsuStdObjectType.Hold
    var timePreempt by Delegates.notNull<Double>()

    val travelDist get() = when(val kind = kind) {
        is OsuStdObjectType.Slider -> kind.lazyTravelDistance
        else -> 0.0
    }

    val travelTime get() = when(val kind = kind) {
        is OsuStdObjectType.Slider -> kind.lazyTravelTime
        else -> 0.0
    }

    val endPosition get() = when(val kind = kind) {
        is OsuStdObjectType.Circle -> position
        is OsuStdObjectType.Slider -> kind.endPosition
        is OsuStdObjectType.Spinner -> position
        is OsuStdObjectType.Hold -> HitObjectPosition(-1.0, -1.0)
    }

    val endTime get() = when(val kind = kind) {
        is OsuStdObjectType.Circle -> time
        is OsuStdObjectType.Slider -> kind.endTime
        is OsuStdObjectType.Spinner -> kind.endTime
        is OsuStdObjectType.Hold -> -1.0
    }

    val lazyEndPosition get() = when(val kind = kind) {
        is OsuStdObjectType.Circle -> position
        is OsuStdObjectType.Slider -> kind.lazyEndPosition
        is OsuStdObjectType.Spinner -> position
        is OsuStdObjectType.Hold -> HitObjectPosition(-1.0, -1.0)
    }

    val isCircle get() = kind is OsuStdObjectType.Circle
    val isSlider get() = kind is OsuStdObjectType.Slider
    val isSpinner get() = kind is OsuStdObjectType.Spinner

    init {
        attributes.maxCombo ++
        val stackHeight = 0.0

        timePreempt = difficultyRange(attributes.approachRate.let {
            if(mods.hr()) it * 1.4 else if(mods.ez()) it * 0.5 else it
        }, OSU_AR_MAX, OSU_AR_AVG, OSU_AR_MIN)

        when(h.kind) {
            is HitObjectType.Circle -> {
                this.time = h.startTime
                this.position = h.pos
                this.kind = OsuStdObjectType.Circle
                this.stackHeight = stackHeight
            }
            is HitObjectType.Slider -> {
                sliderState.update(h.startTime)

                var tickDistance = 100.0 * beatmap.sliderMultiplier / beatmap.sliderTickRate
                if (beatmap.version >= 8) tickDistance /= (100.0 / sliderState.speedMultiply).coerceIn(
                    10.0,
                    1000.0
                ) / 100.0

                val duration =
                    h.kind.repeatTimes * sliderState.beatLength * h.kind.pixelLength / (beatmap.sliderMultiplier * sliderState.speedMultiply) / 100.0
                val spanDuration = duration / h.kind.repeatTimes.toDouble()

                val curve = Curve.newCurve(h.kind.curvePoints, h.kind.pathType)

                val computePosition = { time: Double ->
                    var progress = (time - h.startTime) / spanDuration
                    if (progress % 2.0 >= 1.0) progress = 1.0 - progress % 1.0 else progress %= 1.0
                    curve.pointAtDistance(h.kind.pixelLength * progress)
                }

                val headTick = curve.pointAtDistance(0.0)
                val tailTick = curve.pointAtDistance(h.kind.pixelLength)
                val midTicks = mutableListOf<HitObjectPosition>()

                var currentDistance = tickDistance
                val timeAdd = duration * (tickDistance / (h.kind.pixelLength * h.kind.repeatTimes.toDouble()))
                val target = h.kind.pixelLength - tickDistance / 8.0
                if (currentDistance < target) {
                    for (index in 1..Int.MAX_VALUE) {
                        val time = h.startTime + timeAdd * index
                        midTicks.add(computePosition(time))
                        currentDistance += tickDistance

                        if (currentDistance >= target) break
                    }
                }

                val nestedObjects = mutableListOf<HitObjectPosition>()

                // the first object of slider, this combo is accumulated at the first line of `init { }`
                nestedObjects.add(headTick)

                val isRepeatSlider = h.kind.repeatTimes > 1
                for (i in 1..h.kind.repeatTimes) {
                    midTicks.run {
                        if (i % 2 == 1) concatenate(tailTick) else reversed().concatenate(headTick)
                    }.forEach { t ->
                        nestedObjects.add(t)
                        attributes.maxCombo++
                    }
                }

                var lazyTravelDistance = 0.0
                val lazyTravelTime = duration - LEGACY_LAST_TICK_OFFSET
                var endTimeMin = lazyTravelTime / spanDuration
                if (endTimeMin % 2.0 >= 1.0) {
                    endTimeMin = 1.0 - endTimeMin % 1.0
                } else {
                    endTimeMin %= 1.0
                }

                var lazyEndPosition = curve.pointAtDistance(h.kind.pixelLength * endTimeMin)
                var currCursorPosition = h.pos

                nestedObjects.forEachIndexed { idx, pos ->
                    if (idx == 0) return@forEachIndexed // skip the first object
                    var currMovement = pos - currCursorPosition
                    var currMovementLength = scalingFactor * currMovement.length()

                    var requiredMovement = ASSUMED_SLIDER_RADIUS

                    if (idx == nestedObjects.lastIndex) {
                        val lazyMovement = lazyEndPosition - currCursorPosition

                        if (lazyMovement.length() < currMovement.length())
                            currMovement = lazyMovement

                        currMovementLength = scalingFactor * currMovement.length()
                    } else if (isRepeatSlider) {
                        requiredMovement = NORMALIZED_RADIUS
                    }

                    if (currMovementLength > requiredMovement) {
                        currCursorPosition += currMovement * ((currMovementLength - requiredMovement) / currMovementLength)
                        currMovementLength *= (currMovementLength - requiredMovement) / currMovementLength
                        lazyTravelDistance += currMovementLength
                    }

                    if (idx == nestedObjects.lastIndex)
                        lazyEndPosition = currCursorPosition
                }

                lazyTravelDistance *= (1 + (h.kind.repeatTimes - 1) / 2.5).pow(1.0 / 2.5)

                this.time = h.startTime
                this.position = h.pos
                this.stackHeight = stackHeight
                this.kind = OsuStdObjectType.Slider(
                    endTime = h.startTime + duration,
                    endPosition = nestedObjects.last(),
                    lazyEndPosition = lazyEndPosition,
                    lazyTravelDistance = lazyTravelDistance,
                    lazyTravelTime = lazyTravelTime
                )
            }
            is HitObjectType.Spinner -> {
                this.time = h.startTime
                this.position = h.pos
                this.stackHeight = stackHeight
                this.kind = OsuStdObjectType.Spinner(h.kind.endTime)
            }
            is HitObjectType.Hold -> {
                this.stackHeight = -1.0 //represent a Hold object
            }
        }
    }

    override fun toString(): String {
        return "OsuStdObject(time=$time, position=$position, stackHeight=$stackHeight, travelDist=$travelDist, endPosition=$endPosition, endTime=$endTime, lazyEndPosition=$lazyEndPosition, isCircle=$isCircle, isSlider=$isSlider, isSpinner=$isSpinner)"
    }
}

sealed class OsuStdObjectType {
    object Circle : OsuStdObjectType() {
        override fun toString(): String {
            return "Circle()"
        }
    }
    class Slider(
        val endTime: Double, val endPosition: HitObjectPosition,
        val lazyEndPosition: HitObjectPosition, val lazyTravelDistance: Double, val lazyTravelTime: Double,
    ) : OsuStdObjectType() {
        override fun toString(): String {
            return "Slider(endTime=$endTime, endPosition=$endPosition, lazyEndPosition=$lazyEndPosition, travelDist=$lazyTravelDistance, travelTime=$lazyTravelTime)"
        }
    }
    class Spinner(val endTime: Double) : OsuStdObjectType() {
        override fun toString(): String {
            return "Spinner(endTime=$endTime)"
        }
    }
    object Hold : OsuStdObjectType() {
        override fun toString(): String {
            return "Circle()"
        }
    }
}