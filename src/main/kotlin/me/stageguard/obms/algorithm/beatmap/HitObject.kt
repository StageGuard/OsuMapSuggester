package me.stageguard.obms.algorithm.beatmap

import kotlin.math.sqrt

data class HitObject(
    val pos: HitObjectPosition,
    val startTime: Double,
    val kind: HitObjectType,
    val sound: Int,
) : ITimingPoint {
    override val time: Double = startTime
    val endTime get() = when(kind) {
        is HitObjectType.Circle, is HitObjectType.Slider -> startTime
        is HitObjectType.Spinner -> kind.endTime
        is HitObjectType.Hold -> kind.endTime
    }
    fun isCircle() = kind is HitObjectType.Circle
    fun isSlider() = kind is HitObjectType.Slider
    fun isSpinner() = kind is HitObjectType.Spinner
}

sealed class HitObjectType {
    object Circle : HitObjectType() {
        override fun toString(): String {
            return "HitObjectType.Circle"
        }
    }
    class Slider(
        val pixelLength: Double,
        val repeatTimes: Int,
        val curvePoints: List<HitObjectPosition>,
        val pathType: SliderPathType
    ) : HitObjectType() {
        override fun toString(): String {
            return "HitObjectType.Slider(pixelLength=$pixelLength, repeatTimes=$repeatTimes, curvePoints=$curvePoints, pathType=$pathType)"
        }
    }
    class Spinner(val endTime: Double) : HitObjectType() {
        override fun toString(): String {
            return "HitObjectType.Spinner(endTime=$endTime)"
        }
    }
    class Hold(val endTime: Double) : HitObjectType() {
        override fun toString(): String {
            return "HitObjectType.Hold(endTime=$endTime)"
        }
    }
}

enum class SliderPathType {
    Catmull,
    Bezier,
    Linear,
    PerfectCurve,
    Unknown;
    companion object {
        fun parse(input: String) = when(input) {
            "L" -> Linear
            "C" -> Catmull
            "B" -> Bezier
            "P" -> PerfectCurve
            else -> Unknown
        }
    }
}

data class HitObjectPosition(val x: Double, val y: Double) {
    fun lengthSquared() = dotMultiply(this)
    fun length() = sqrt(lengthSquared())
    fun dotMultiply(other: HitObjectPosition) = Math.fma(x, other.x, y * other.y)
    fun distance(other: HitObjectPosition) = (this - other).length()
    fun normalize() = this / length()
    operator fun plus(other: HitObjectPosition) = HitObjectPosition(x + other.x, y + other.y)
    operator fun minus(other: HitObjectPosition) = HitObjectPosition(x - other.x, y - other.y)
    operator fun times(bTimes: Double) = HitObjectPosition(x * bTimes, y * bTimes)
    operator fun div(bDiv: Double) : HitObjectPosition {
        require(bDiv > 0) { "Illegal right value: bDiv <= 0" }
        return HitObjectPosition(x / bDiv, y / bDiv)
    }
    companion object {
        fun zero() = HitObjectPosition(0.toDouble(), 0.toDouble())
    }
}