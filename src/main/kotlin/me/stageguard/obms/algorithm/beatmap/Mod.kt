package me.stageguard.obms.algorithm.beatmap

class ModCombination(private val value: Int) {
    companion object Mod {
        const val NF: Int = 1 shl 0
        const val EZ: Int = 1 shl 1
        const val TD: Int = 1 shl 2
        const val HD: Int = 1 shl 3
        const val HR: Int = 1 shl 4
        const val DT: Int = 1 shl 6
        const val RX: Int = 1 shl 7
        const val HT: Int = 1 shl 8
        const val FL: Int = 1 shl 10
        const val SO: Int = 1 shl 12
        const val AP: Int = 1 shl 13
        const val V2: Int = 1 shl 29
    }

    fun isDoubleTimeOrHalfTime() = value and (HT or DT) > 0
    fun isScoreMode() = value and (HT or DT or HR or EZ) > 0
    fun speed() = when {
        value and DT > 0 -> 1.5
        value and HT > 0 -> 0.75
        else -> 1.0
    }
    fun odArHpMultiplier() = when {
        value and HR > 0 -> 1.4
        value and EZ > 0 -> 0.5
        else -> 1.0
    }

    fun nf() = value and NF > 0
    fun ez() = value and EZ > 0
    fun td() = value and TD > 0
    fun hd() = value and HD > 0
    fun hr() = value and HR > 0
    fun dt() = value and DT > 0
    fun rx() = value and RX > 0
    fun ht() = value and HT > 0
    fun fl() = value and FL > 0
    fun so() = value and SO > 0
    fun ap() = value and AP > 0
    fun v2() = value and V2 > 0
}