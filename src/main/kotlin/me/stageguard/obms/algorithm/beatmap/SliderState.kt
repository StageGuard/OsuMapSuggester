package me.stageguard.obms.algorithm.beatmap

import java.util.*
import kotlin.properties.Delegates

class SliderState(beatmap: Beatmap) {
    val controlPoints: ControlPointIterator = ControlPointIterator(beatmap)
    lateinit var next: Optional<ControlPoint>
    var beatLength by Delegates.notNull<Double>()
    var speedMultiply by Delegates.notNull<Double>()

    init {
        val next = controlPoints.next()
        val (beatLength, speedMultiply) = if(next.isPresent) {
            when(val value = next.get()) {
                is ControlPoint.Timing -> value.beatLength to 1.0
                is ControlPoint.Difficulty -> 1000.0 to value.speedMultiply
            }
        } else { 1000.0 to 1.0 }
        this.beatLength = beatLength
        this.speedMultiply = speedMultiply
    }

    fun update(time: Double) {
        next.filter { cp -> time >= cp.time }.ifPresent {
            when(it) {
                is ControlPoint.Timing -> {
                    beatLength = it.beatLength
                    speedMultiply = 1.0
                }
                is ControlPoint.Difficulty -> {
                    speedMultiply = it.speedMultiply
                }
            }
            this.next = controlPoints.next()
        }
    }
}