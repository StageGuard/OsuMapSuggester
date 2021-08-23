package me.stageguard.obms.osu.processor.replay

import me.stageguard.obms.osu.processor.beatmap.HitObjectPosition
import me.stageguard.obms.osu.processor.beatmap.ModCombination

data class ReplayFrame(
    val timeDiff: Int,
    val time: Int,
    var position: HitObjectPosition, //for flip
    val keys: List<Key>,
    var currentHit : Int = 0
)

data class LifeFrame(
    val time: Int,
    val percentage: Double
)

data class ReplayData(
    val gameMode : Int,
    var fileFormat : Int,
    val mapHash : String,
    val player : String,
    val replayHash : String,
    val n300 : Int,
    val n100 : Int,
    val n50 : Int,
    val nMiss : Int,
    val totalScore : Int,
    val maxCombo : Int,
    val isPerfect : Boolean,
    val mods : ModCombination,
    val seed : Int,
    val lifeFrames : List<LifeFrame>,
    val replayFrames : List<ReplayFrame>
)