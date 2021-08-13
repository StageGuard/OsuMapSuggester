@file:Suppress("unused")

package me.stageguard.obms.osu.processor.beatmap

class ModCombination private constructor(private val value: Int) {

    fun isDoubleTimeOrHalfTime() = value and (Mod.HalfTime.value or Mod.DoubleTime.value) > 0
    fun isScoreMode() = value and (Mod.HalfTime.value or Mod.DoubleTime.value or Mod.HardRock.value or Mod.Easy.value) > 0
    fun speed() = when {
        value and Mod.DoubleTime.value > 0 -> 1.5
        value and Mod.HalfTime.value > 0 -> 0.75
        else -> 1.0
    }
    fun odArHpMultiplier() = when {
        value and Mod.HardRock.value > 0 -> 1.4
        value and Mod.Easy.value > 0 -> 0.5
        else -> 1.0
    }

    fun nf() = value and Mod.NoFail.value > 0
    fun ez() = value and Mod.Easy.value > 0
    fun td() = value and Mod.TouchDevice.value > 0
    fun hd() = value and Mod.Hidden.value > 0
    fun hr() = value and Mod.HardRock.value > 0
    fun dt() = value and Mod.DoubleTime.value > 0
    fun nc() = value and Mod.NightCore.value > 0
    fun rx() = value and Mod.Relax.value > 0
    fun ht() = value and Mod.HalfTime.value > 0
    fun fl() = value and Mod.Flashlight.value > 0
    fun so() = value and Mod.SpunOut.value > 0
    fun ap() = value and Mod.Perfect.value > 0
    fun v2() = value and Mod.ScoreV2.value > 0

    companion object {
        fun of(vararg mods: Mod) = ModCombination(
            if(mods.size == 1) mods.single().value else mods.drop(1).fold(mods.first().value) { r, v -> r or v.value }
        )
        fun of(mods: List<Mod>) = ModCombination(
            if(mods.size == 1) mods.single().value else mods.drop(1).fold(mods.first().value) { r, v -> r or v.value }
        )
        fun ofRaw(mods: Int) = ModCombination(mods)
    }
}

sealed class Mod(val value: Int) {
    object None : Mod(0)
    object NoFail : Mod(1)
    object Easy : Mod(2)
    object TouchDevice : Mod(4)
    object Hidden : Mod(8)
    object HardRock : Mod(16)
    object SuddenDeath : Mod(32)
    object DoubleTime : Mod(64)
    object Relax : Mod(128)
    object HalfTime : Mod(256)
    object NightCore : Mod(512)
    object Flashlight : Mod(1024)
    object Autoplay : Mod(2048)
    object SpunOut : Mod(4096)
    object Relax2 : Mod(8192)
    object Perfect : Mod(16384)
    object Key4 : Mod(32768)
    object Key5 : Mod(65536)
    object Key6 : Mod(131072)
    object Key7 : Mod(262144)
    object Key8 : Mod(524288)
    object FadeIn : Mod(1048576)
    object Random : Mod(2097152)
    object Cinema : Mod(4194304)
    object Target : Mod(8388608)
    object Key9 : Mod(16777216)
    object KeyCoop : Mod(33554432)
    object Key1 : Mod(67108864)
    object Key3 : Mod(134217728)
    object Key2 : Mod(268435456)
    object ScoreV2 : Mod(536870912)
    object Mirror : Mod(1073741824)
}