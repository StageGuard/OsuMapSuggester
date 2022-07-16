package me.stageguard.obms.osu.algorithm

import me.stageguard.obms.osu.processor.beatmap.Mod

interface PerformanceCalculator<R> {
    fun mods(vararg mods: Mod) : PerformanceCalculator<R>
    fun mods(mods: List<Mod>) : PerformanceCalculator<R>
    fun combo(cb: Int) : PerformanceCalculator<R>
    fun n300(n : Int) : PerformanceCalculator<R>
    fun n100(n: Int) : PerformanceCalculator<R>
    fun n50(n: Int) : PerformanceCalculator<R>
    fun misses(n: Int) : PerformanceCalculator<R>
    fun passedObjects(n: Int) : PerformanceCalculator<R>
    fun accuracy(acc: Double) : PerformanceCalculator<R>
    fun calculate() : R
}
