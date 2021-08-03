package me.stageguard.obms.utils

import me.stageguard.obms.algorithm.beatmap.HitObjectPosition
import kotlin.math.*

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
inline fun cpn(p: Int, n: Int) : Double {
    if (p < 0 || p > n) {
        return 0.0
    }

    val p2 = min(n - p, p)
    val diff = n - p2
    var out = 1.0

    for (i in 1..p2) {
        out *= (diff + i) / i
    }

    return out
}

@Suppress("NOTHING_TO_INLINE")
inline fun lerp(v0: Double, v1: Double, t: Double): Double {
    return (1 - t) * v0 + t * v1
}

@Suppress("NOTHING_TO_INLINE")
fun getCircumCircle(
    p0: HitObjectPosition,
    p1: HitObjectPosition,
    p2: HitObjectPosition
) : Pair<HitObjectPosition, Double> {
    val a = 2.0 * (p0.x * (p1.y - p2.y) - p0.y * (p1.x - p2.x) + p1.x * p2.y - p2.x * p1.y);

    val q0 = p0.lengthSquared()
    val q1 = p1.lengthSquared()
    val q2 = p2.lengthSquared()

    val cx = (q0 * (p1.y - p2.y) + q1 * (p2.y - p0.y) + q2 * (p0.y - p1.y)) / a
    val cy = (q0 * (p2.x - p1.x) + q1 * (p0.x - p2.x) + q2 * (p1.x - p0.x)) / a

    val r = hypot(cx - p0.x, cy - p0.y)

    return Pair(HitObjectPosition(cx, cy), r)
}

fun rotate(
    center: HitObjectPosition, origin: HitObjectPosition, theta: Double
) : HitObjectPosition {
    val (sin, cos) = sin(theta) to cos(theta)
    val diff = origin - center
    val offset = HitObjectPosition(
        x = cos * diff.x - sin * diff.y,
        y = sin * diff.x + cos * diff.y
    )
    return center + offset
}

fun isLeft(p0: HitObjectPosition, p1: HitObjectPosition, p2: HitObjectPosition) =
    ((p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)) < 0.0

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