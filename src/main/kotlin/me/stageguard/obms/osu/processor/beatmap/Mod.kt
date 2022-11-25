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

    fun toList() = mutableListOf<Mod>().also {
        if(nf()) it.add(Mod.NoFail)
        if(ez()) it.add(Mod.Easy)
        if(td()) it.add(Mod.TouchDevice)
        if(hd()) it.add(Mod.Hidden)
        if(hr()) it.add(Mod.HardRock)
        if(dt()) it.add(Mod.DoubleTime)
        if(nc()) it.add(Mod.NightCore)
        if(rx()) it.add(Mod.Relax)
        if(ht()) it.add(Mod.HalfTime)
        if(fl()) it.add(Mod.Flashlight)
        if(so()) it.add(Mod.SpunOut)
        if(ap()) it.add(Mod.Autoplay)
        if(v2()) it.add(Mod.ScoreV2)
        if(it.isEmpty()) it.add(Mod.None)
    }.toList()

    val rawValue get() = value

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

enum class Mod(val value: Int, val type: ModType, val iconCharacter: Int = -1) {
    None(0, ModType.None, 0xe005) {
        override fun toString() = "NM"
    },
    NoFail(1, ModType.DifficultyReduction, 0xe044) {
        override fun toString() = "NF"
    },
    Easy(2, ModType.DifficultyReduction, 0xe03e) {
        override fun toString() = "EZ"
    },
    TouchDevice(4, ModType.None) {
        override fun toString() = "TD"
    },
    Hidden(8, ModType.DifficultyIncrease, 0xe042) {
        override fun toString() = "HD"
    },
    HardRock(16, ModType.DifficultyIncrease, 0xe041) {
        override fun toString() = "HR"
    },
    SuddenDeath(32, ModType.DifficultyIncrease, 0xe047) {
        override fun toString() = "SD"
    },
    DoubleTime(64, ModType.DifficultyIncrease, 0xe03d) {
        override fun toString() = "DT"
    },
    Relax(128, ModType.Automation) {
        override fun toString() = "RX"
    },
    HalfTime(256, ModType.DifficultyReduction, 0xe040) {
        override fun toString() = "HT"
    },
    NightCore(512, ModType.DifficultyIncrease, 0xe043) {
        override fun toString() = "NC"
    },
    Flashlight(1024, ModType.DifficultyIncrease, 0xe03f) {
        override fun toString() = "FL"
    },
    Autoplay(2048, ModType.Automation) {
        override fun toString() = "AP"
    },
    SpunOut(4096, ModType.Automation, 0xe046) {
        override fun toString() = "SO"
    },
    Relax2(8192, ModType.Automation),
    Perfect(16384, ModType.DifficultyIncrease, 0xe049) {
        override fun toString() = "PF"
    },
    Key4(32768, ModType.None),
    Key5(65536, ModType.None),
    Key6(131072, ModType.None),
    Key7(262144, ModType.None),
    Key8(524288, ModType.None),
    FadeIn(1048576, ModType.None),
    Random(2097152, ModType.None),
    Cinema(4194304, ModType.None),
    Target(8388608, ModType.None),
    Key9(16777216, ModType.None),
    KeyCoop(33554432, ModType.None),
    Key1(67108864, ModType.None),
    Key3(134217728, ModType.None),
    Key2(268435456, ModType.None),
    ScoreV2(536870912, ModType.None) {
        override fun toString() = "V2"
    },
    Mirror(1073741824, ModType.None)
}

enum class ModType {
    DifficultyIncrease, DifficultyReduction, Automation, None
}