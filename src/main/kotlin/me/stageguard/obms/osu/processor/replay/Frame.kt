package me.stageguard.obms.osu.processor.replay

import me.stageguard.obms.osu.processor.beatmap.HitObject

data class HitFrame(
    val frame: ReplayFrame,
    val hitObject: HitObject,
    val keys: List<Key>,
    var hitPointPercentage: Pair<Double, Double> = 0.0 to 0.0
)

data class ClickFrame(
    val frame: ReplayFrame,
    val keys: List<Key>
)