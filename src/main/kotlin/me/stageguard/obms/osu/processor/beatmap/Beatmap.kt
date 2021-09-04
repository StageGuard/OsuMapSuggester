package me.stageguard.obms.osu.processor.beatmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.stageguard.obms.osu.processor.beatmap.SliderPathType.*
import me.stageguard.obms.utils.bomReader
import me.stageguard.obms.utils.isLinearPoints
import java.io.Reader
import me.stageguard.obms.utils.isValidLinearPoint
import java.io.File
import java.lang.IllegalStateException
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
class Beatmap private constructor(
    val version: Int,

    val nCircles: Int,
    val nSliders: Int,
    val nSpinners: Int,

    val approachRate: Double,
    val overallDifficulty: Double,
    val circleSize: Double,
    val hpDrainRate: Double,
    val sliderMultiplier: Double,
    val sliderTickRate: Double,

    val hitObjects: MutableList<HitObject>,
    val timingPoints: MutableList<TimingPoint>,
    val difficultyPoints: MutableList<DifficultyPoint>,

    val stackLeniency: Double
) {
    val attribute get() = BeatmapAttribute(approachRate, overallDifficulty, circleSize, hpDrainRate)
    companion object {
        private const val CIRCLE_FLAG = 1 shl 0
        private const val SLIDER_FLAG = 1 shl 1
        private const val SPINNER_FLAG = 1 shl 3
        private const val HOLD_FLAG = 1 shl 7

        private const val CURVE_POINT_THRESHOLD = 256
        private const val MAX_COORDINATE_VALUE = 131_072.0

        suspend fun parse(file: File) : Beatmap = parse(file.bomReader())

        suspend fun parse(reader: Reader) = buildBeatmap {
            val lines = withContext(Dispatchers.IO) { reader.readLines() }.filterNot {
                it.startsWith("//") || it.startsWith("_") || it.isEmpty() || it.isBlank()
            }.map {
                it.trim { c -> c == '﻿' || c.isWhitespace() }
            }
            lines.first {
                it.startsWith("osu file format v")
            }.run {
                version = substringAfter("osu file format v").toInt()
            }
            val sections = mutableListOf<Pair<String, Int>>().also { lines.forEachIndexed { index, s ->
                if(s.startsWith("[") && s.endsWith("]")) it.add(s.drop(1).dropLast(1) to index)
            } }
            sections.forEachIndexed { index, p ->
                val sectionRange = if(index == sections.lastIndex) {
                    lines.subList(p.second + 1, lines.lastIndex + 1)
                } else {
                    lines.subList(p.second + 1, sections[index + 1].second)
                }
                when(p.first) {
                    "General" -> {
                        sectionRange.map {
                            it.split(":").run { get(0).trim() to get(1).trim() }
                        }.forEach {
                            when(it.first) {
                                "StackLeniency" -> stackLeniency = it.second.toDouble()
                            }
                        }
                    }
                    "Difficulty" -> {
                        sectionRange.map {
                            it.split(":").run { get(0).trim() to get(1).trim() }
                        }.forEach {
                            when(it.first) {
                                "ApproachRate" -> approachRate = it.second.toDouble()
                                "OverallDifficulty" -> overallDifficulty = it.second.toDouble()
                                "CircleSize" -> circleSize = it.second.toDouble()
                                "HPDrainRate" -> hpDrainRate = it.second.toDouble()
                                "SliderMultiplier" -> sliderMultiplier = it.second.toDouble()
                                "SliderTickRate" -> sliderTickRate = it.second.toDouble()
                            }
                            //for old beatmap
                            if(approachRate == -1.0) approachRate = overallDifficulty
                        }
                    }
                    "TimingPoints" -> {
                        var unsortedTimings = false
                        var unsortedDifficulties = false

                        var prevDiff = 0.0
                        var prevTime = 0.0

                        sectionRange.map { li ->
                            li.split(",").map { it.trim() }
                        }.forEach {
                            val time = it[0].toDouble()
                            val beatLength = it[1].toDouble()
                            if(beatLength < 0) {
                                difficultyPoints.add(DifficultyPoint(time = time, speedMultiplier = -100.0 / beatLength))
                                if (time < prevDiff) {
                                    unsortedDifficulties = true
                                } else {
                                    prevDiff = time
                                }
                            } else {
                                timingPoints.add(TimingPoint(time = time, beatLength = beatLength))
                                if (time < prevTime) {
                                    unsortedTimings = true
                                } else {
                                    prevTime = time
                                }
                            }
                        }
                        //TODO("check is sort correct")
                        if(unsortedDifficulties) difficultyPoints.sortBy { it.time }
                        if(unsortedTimings) timingPoints.sortBy { it.time }
                    }
                    "HitObjects" -> {
                        var unsorted = false
                        var prevTime = 0.0

                        sectionRange.map { li ->
                            li.split(",").map { it.trim() }
                        }.forEach {
                            val position = HitObjectPosition(it[0].toDouble(), it[1].toDouble())
                            val time = it[2].toDouble().also { t ->
                                require(t.isFinite()) { "Hit object time is not finite." }
                            }
                            if (hitObjects.isEmpty() && time < prevTime) {
                                unsorted = true
                            }
                            val sound = it[4].toInt()
                            val primitive = it[3].toInt()
                            val hitObjectType = when {
                                primitive and CIRCLE_FLAG > 0 -> {
                                    nCircles ++
                                    HitObjectType.Circle
                                }
                                primitive and SLIDER_FLAG > 0 -> {
                                    nSliders ++
                                    val curvePoints = mutableListOf<HitObjectPosition>().also { cp ->
                                        cp.add(position)
                                    }
                                    val curveRawList = it[5].split("|")
                                    var pathType = SliderPathType.parse(curveRawList.first())
                                    curveRawList.drop(1).map { cp ->
                                        cp.split(":").run {
                                            HitObjectPosition(get(0).toDouble(), get(1).toDouble())
                                        }
                                    }.also { crl -> curvePoints.addAll(crl) }
                                    when {
                                        pathType == Linear && curvePoints.size % 2 == 0 -> {
                                            if(isValidLinearPoint(curvePoints)) {
                                                for(idx in (2 until curvePoints.size - 1).reversed()) {
                                                    if(idx % 2 == 0) curvePoints.removeAt(idx)
                                                }
                                            } else {
                                                pathType = Bezier
                                            }
                                        }
                                        pathType == PerfectCurve && curvePoints.size == 3 -> {
                                            if(isLinearPoints(curvePoints[0], curvePoints[1], curvePoints[2])) {
                                                pathType = Linear
                                            }
                                        }
                                        pathType == Catmull -> { }
                                        else -> pathType = Bezier
                                    }
                                    /*while(curvePoints.size > CURVE_POINT_THRESHOLD) {
                                        val last = curvePoints.last()
                                        val lastIndex = (curvePoints.size - 1) / 2
                                        for(idx in 1..lastIndex) {
                                            val a = curvePoints[2 * idx]
                                            curvePoints[2 * idx] = curvePoints[idx]
                                            curvePoints[idx] = a
                                        }
                                        curvePoints[lastIndex] = last
                                        curvePoints.take(lastIndex + 1)
                                    }*/
                                    if (curvePoints.isEmpty()) {
                                        HitObjectType.Circle
                                    } else {
                                        val repeats = min(it[6].toInt(), 9000)
                                        val pixelLength = min(max(it[7].toDouble(), 0.0), MAX_COORDINATE_VALUE)
                                        HitObjectType.Slider(
                                            pixelLength = pixelLength,
                                            repeatTimes = repeats,
                                            curvePoints = curvePoints.toList(),
                                            pathType = pathType
                                        )
                                    }
                                }
                                primitive and SPINNER_FLAG > 0 -> {
                                    nSpinners ++
                                    HitObjectType.Spinner(it[5].toDouble())
                                }
                                primitive and HOLD_FLAG > 0 -> {
                                    nSpinners ++
                                    val nextPair = it[5].split(":")[0].toDouble()
                                    HitObjectType.Hold(endTime = max(time, nextPair))
                                }
                                else -> {
                                    throw IllegalStateException("UnKnown HitObjectType.")
                                }
                            }
                            hitObjects.add(
                                HitObject(
                                pos = position,
                                startTime = time,
                                kind = hitObjectType,
                                sound = sound
                            )
                            )
                            prevTime = time
                        }
                        if(unsorted) hitObjects.sortBy { it.time }
                    }
                }
            }
        }

        @Suppress("FunctionName")
        suspend fun buildBeatmap(buildAction: suspend Builder.() -> Unit) = Builder().run {
            buildAction()
            build()
        }
    }

    class Builder {
        var version: Int = -1

        var nCircles: Int = 0
        var nSliders: Int = 0
        var nSpinners: Int = 0

        var approachRate: Double = -1.0
        var overallDifficulty: Double = -1.0
        var circleSize: Double = -1.0
        var hpDrainRate: Double = -1.0
        var sliderMultiplier: Double = -1.0
        var sliderTickRate: Double = -1.0

        var hitObjects: MutableList<HitObject> = mutableListOf()
        var timingPoints: MutableList<TimingPoint> = mutableListOf()
        var difficultyPoints: MutableList<DifficultyPoint> = mutableListOf()

        var stackLeniency: Double = -1.0

        internal fun build() : Beatmap {
            require(version > 0) { "Beatmap version is invalid." }
            require(approachRate >= 0 && overallDifficulty >= 0 && circleSize >= 0 && hpDrainRate >= 0 && sliderMultiplier >= 0 && sliderTickRate >= 0 ) {
                "Beatmap attribute is invalid."
            }
            require(hitObjects.isNotEmpty()) { "Beatmap body is empty." }
            require(stackLeniency >= 0) { "Beatmap stack leniency is invalid." }
            return Beatmap(
                version, nCircles, nSliders, nSpinners, approachRate,
                overallDifficulty, circleSize, hpDrainRate, sliderMultiplier,
                sliderTickRate, hitObjects, timingPoints, difficultyPoints, stackLeniency
            )
        }
    }

    override fun toString(): String {
        return "Beatmap(version=$version, nCircles=$nCircles, nSliders=$nSliders, nSpinners=$nSpinners, approachRate=$approachRate, overallDifficulty=$overallDifficulty, circleSize=$circleSize, hpDrainRate=$hpDrainRate, sliderMultiplier=$sliderMultiplier, sliderTickRate=$sliderTickRate, hitObjects=$hitObjects, timingPoints=$timingPoints, difficultyPoints=$difficultyPoints, stackLeniency=$stackLeniency, attribute=$attribute)"
    }

}



data class TimingPoint(override val time: Double, val beatLength: Double) : ITimingPoint
data class DifficultyPoint(override val time: Double, val speedMultiplier: Double) : ITimingPoint