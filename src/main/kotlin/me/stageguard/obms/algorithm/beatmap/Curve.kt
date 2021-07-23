package me.stageguard.obms.algorithm.beatmap

import me.stageguard.obms.utils.pointAtDistance as staticPointAtDistance

sealed class Curve {
    class Bezier(val point: Points) : Curve()
    class Catmull(val point: Points) : Curve()
    class Linear(val points: List<HitObjectPosition>) : Curve()
    class Perfect(
        val origin: HitObjectPosition,
        val center: HitObjectPosition,
        val radius: Double
    ) : Curve()
}

sealed class Points {
    class Single(val position: HitObjectPosition) : Points()
    class Multi(val positions: List<HitObjectPosition>) : Points()

    fun pointAtDistance(dist: Double) = when(this) {
        is Multi -> staticPointAtDistance(positions, dist)
        is Single -> position
    }
}