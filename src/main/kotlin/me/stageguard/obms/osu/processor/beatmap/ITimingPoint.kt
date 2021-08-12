package me.stageguard.obms.osu.processor.beatmap

interface ITimingPoint : Comparable<ITimingPoint> {
    val time: Double
    override fun compareTo(other: ITimingPoint): Int = (time - other.time).toInt()
}