package me.stageguard.obms.utils

import me.stageguard.obms.algorithm.beatmap.HitObjectPosition
import kotlin.math.abs

@Suppress("NOTHING_TO_INLINE")
inline fun isValidLinearPoint(points: List<HitObjectPosition>) : Boolean {
    for ((index, value) in points.drop(1).zip(points.drop(2)).withIndex()) {
        if(index % 2 == 0 && value.first != value.second) {
            return false
        }
    }
    return true
}

@Suppress("NOTHING_TO_INLINE")
inline fun isLinearPoints(p0: HitObjectPosition, p1: HitObjectPosition, p2: HitObjectPosition) =
    abs((p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)) <= 1.19209290e-07

@Suppress("NOTHING_TO_INLINE")
inline fun pointAtDistance(points: List<HitObjectPosition>, dist: Double) : HitObjectPosition {
    if(points.size < 2) {
        return HitObjectPosition.zero()
    } else if (abs(dist) <= 1.19209290e-07) {
        return points[0]
    }
    var currentDist = 0.0
    var newDist = 0.0
    for((current, next) in points.zip(points.drop(1))) {
        newDist = (current - next).length()
        currentDist += newDist
        if(dist < currentDist) {
            val remainingDist = dist - (currentDist - newDist)
            return if(abs(remainingDist) <= 1.19209290e-07) {
                current
            } else {
                current + (next - current) * (remainingDist / newDist)
            }
        }
    }
    val remainingDist = dist - (currentDist - newDist)
    val preLast = points[points.lastIndex - 1]
    val last = points.last()

    return preLast + (last - preLast) * (remainingDist / newDist)
}