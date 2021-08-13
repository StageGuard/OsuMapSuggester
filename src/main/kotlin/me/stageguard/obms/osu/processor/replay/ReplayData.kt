package me.stageguard.obms.osu.processor.replay

data class ReplayFrame(
    val timeDiff: Int,
    val time: Int,
    val x: Double,
    val y: Double,
    val keys: List<Keys>
)

data class LifeFrame(
    val time: Int,
    val percentage: Double
)