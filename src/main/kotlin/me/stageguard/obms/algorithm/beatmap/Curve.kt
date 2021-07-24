package me.stageguard.obms.algorithm.beatmap

import me.stageguard.obms.utils.cpn
import me.stageguard.obms.utils.getCircumCircle
import me.stageguard.obms.utils.isLeft
import me.stageguard.obms.utils.rotate
import kotlin.math.max
import kotlin.math.pow
import me.stageguard.obms.utils.pointAtDistance as staticPointAtDistance

sealed class Curve {
    class Bezier(val point: Points) : Curve() {
        override fun toString(): String {
            return "Bezier(point=$point)"
        }
    }
    class Catmull(val point: Points) : Curve() {
        override fun toString(): String {
            return "Catmull(point=$point)"
        }
    }
    class Linear(val points: List<HitObjectPosition>) : Curve() {
        override fun toString(): String {
            return "Linear(points=$points)"
        }
    }
    class Perfect(
        val origin: HitObjectPosition,
        val center: HitObjectPosition,
        val radius: Double
    ) : Curve() {
        override fun toString(): String {
            return "Perfect(origin=$origin, center=$center, radius=$radius)"
        }
    }

    @Suppress("FunctionName")
    companion object {
        private const val BEZIER_TOLERANCE = 0.25
        private const val CATMULL_DETAIL = 50.0

        fun newCurve(points: List<HitObjectPosition>, kind: SliderPathType) : Curve =
            when(kind) {
                SliderPathType.Bezier -> bezier(points)
                SliderPathType.Catmull -> catmull(points)
                SliderPathType.PerfectCurve -> perfect(points)
                SliderPathType.Linear -> Linear(points)
                SliderPathType.Unknown ->
                    throw IllegalStateException("Unknown curve type, not one of Bezier, Catmull, PerfectCure and Linear.")
            }

        private fun bezier(points: List<HitObjectPosition>) : Bezier {
            if(points.size == 1) {
                return Bezier(Points.Single(points.single()))
            }
            var start = 0
            val result = mutableListOf<HitObjectPosition>()

            fun _bezier(internalPoints: List<HitObjectPosition>) {
                val step = max(0.01, BEZIER_TOLERANCE / internalPoints.size.toDouble())
                var i = 0.0
                val n = internalPoints.size - 1

                while (i < 1.0 + step) {
                    val point = internalPoints.mapIndexed { index, hitObjectPosition ->
                        index to hitObjectPosition
                    }.fold(HitObjectPosition.zero()) { p, pa ->
                        p + pa.second * cpn(pa.first, n) * (1.0 - i).pow((n - pa.first).toDouble()) * i.pow(pa.first.toDouble())
                    }
                    result.add(point)
                    i += step
                }
            }

            for((end, pair) in (1..Int.MAX_VALUE).zip(points.zip(points.drop(1)))) {
                if (end - start > 1 && pair.first == pair.second) {
                    _bezier(points.subList(start, end))
                    start = end
                }
            }
            _bezier(points.subList(start, points.lastIndex + 1))
            return Bezier(Points.Multi(result))
        }

        private fun catmull(points: List<HitObjectPosition>) : Catmull {
            val length = points.size

            if(length == 1) {
                return Catmull(Points.Single(points.single()))
            }

            val result = mutableListOf<HitObjectPosition>()

            @Suppress("DuplicatedCode")
            fun catmullPoints(
                v1: HitObjectPosition, v2: HitObjectPosition,
                v3: HitObjectPosition, v4: HitObjectPosition
            ) {
                var c = 0.0

                val x1 = 2.0 * v1.x
                val x2 = -v1.x + v3.x
                val x3 = 2.0 * v1.x - 5.0 * v2.x + 4.0 * v3.x - v4.x
                val x4 = -v1.x + 3.0 * (v2.x - v3.x) + v4.x

                val y1 = 2.0 * v1.y
                val y2 = -v1.y + v3.y
                val y3 = 2.0 * v1.y - 5.0 * v2.y + 4.0 * v3.y - v4.y
                val y4 = -v1.y + 3.0 * (v2.y - v3.y) + v4.y

                do {
                    var t1 = c / CATMULL_DETAIL
                    var t2 = t1 * t1
                    var t3 = t2 * t1

                    result.add(HitObjectPosition (
                        x = 0.5 * (x1 + x2 * t1 + x3 * t2 + x4 * t3),
                        y = 0.5 * (y1 + y2 * t1 + y3 * t2 + y4 * t3),
                    ))

                    t1 = (c + 1.0) / CATMULL_DETAIL
                    t2 = t1 * t1
                    t3 = t2 * t1

                    result.add(HitObjectPosition (
                        x = 0.5 * (x1 + x2 * t1 + x3 * t2 + x4 * t3),
                        y = 0.5 * (y1 + y2 * t1 + y3 * t2 + y4 * t3),
                    ))

                    c += 1.0
                } while (c < CATMULL_DETAIL)
            }

            val v1 = points[0]
            val v2 = points[0]
            var v3 = points.getOrElse(1) { v2 }
            var v4 = points.getOrElse(2) { v3 * 2.0 - v2 }

            catmullPoints(v1, v2, v3, v4)

            for((i, pair) in points.zip(points.drop(1)).mapIndexed { index, pair -> index + 2 to pair }) {
                v3 = points.getOrElse(i) { pair.second * 2.0 - pair.first }
                v4 = points.getOrElse(i + 1) { v3 * 2.0 - pair.second }

                catmullPoints(v1, v2, v3, v4)
            }
            return Catmull(Points.Multi(result))
        }

        private fun perfect(points: List<HitObjectPosition>) : Perfect {
            val (a, b, c) = points
            var (center, radius) = getCircumCircle(a, b, c)
            radius *= (if (!isLeft(a, b, c)) 1 else 0).toInt() * 2 - 1
            return Perfect(origin = a, center = center, radius = radius)
        }

    }

    fun pointAtDistance(dist: Double) : HitObjectPosition = when(this) {
        is Bezier -> point.pointAtDistance(dist)
        is Catmull -> point.pointAtDistance(dist)
        is Linear -> staticPointAtDistance(points, dist)
        is Perfect -> rotate(center, origin, dist / radius)
    }
}

sealed class Points {
    class Single(val position: HitObjectPosition) : Points() {
        override fun toString(): String {
            return "Single(position=$position)"
        }
    }
    class Multi(val positions: List<HitObjectPosition>) : Points() {
        override fun toString(): String {
            return "Multi(positions=$positions)"
        }
    }

    fun pointAtDistance(dist: Double) = when(this) {
        is Multi -> staticPointAtDistance(positions, dist)
        is Single -> position
    }
}