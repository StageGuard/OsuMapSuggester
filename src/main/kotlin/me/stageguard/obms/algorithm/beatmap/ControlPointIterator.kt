package me.stageguard.obms.algorithm.beatmap

import java.util.*

class ControlPointIterator(beatmap: Beatmap) : Iterator<Optional<ControlPoint>> {
    val timingPoints = beatmap.timingPoints.iterator()
    val difficultyPoints = beatmap.difficultyPoints.iterator()
    var nextTiming : Optional<Pair<Double, Double>> =
        Optional.ofNullable(timingPoints.next().run { time to beatLength })
    var nextDifficulty : Optional<Pair<Double, Double>> =
        Optional.ofNullable(difficultyPoints.next().run { time to speedMultiplier })

    override fun hasNext(): Boolean = nextTiming.isEmpty && nextDifficulty.isEmpty

    override fun next(): Optional<ControlPoint> = if(nextTiming.isPresent && nextDifficulty.isPresent) {
        if(nextTiming.get().first <= nextDifficulty.get().first) {
            val (nextTimingTime, nextTimingBeatLength) = nextTiming.get()
            nextTiming = Optional.ofNullable(timingPoints.next().run { time to beatLength })
            Optional.of(ControlPoint.Timing(nextTimingTime, nextTimingBeatLength))
        } else {
            val (nextDifficultyTime, nextDifficultySpeedMultiply) = nextTiming.get()
            nextDifficulty = Optional.ofNullable(difficultyPoints.next().run { time to speedMultiplier })
            Optional.of(ControlPoint.Difficulty(nextDifficultyTime, nextDifficultySpeedMultiply))
        }
    } else if (nextTiming.isPresent && nextDifficulty.isEmpty) {
        val (nextTimingTime, nextTimingBeatLength) = nextTiming.get()
        nextTiming = Optional.ofNullable(timingPoints.next().run { time to beatLength })
        Optional.of(ControlPoint.Timing(nextTimingTime, nextTimingBeatLength))
    } else {
        Optional.empty<ControlPoint>()
    }

}

sealed class ControlPoint(val time: Double) {
    class Timing(time: Double, val beatLength: Double) : ControlPoint(time)
    class Difficulty(time: Double ,val speedMultiply: Double) : ControlPoint(time)

}