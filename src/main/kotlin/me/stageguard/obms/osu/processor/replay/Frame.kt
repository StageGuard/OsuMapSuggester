package me.stageguard.obms.osu.processor.replay

import me.stageguard.obms.osu.processor.beatmap.HitObject

class HitFrame(
    frame: ReplayFrame,
    val hitObject: HitObject,
    keys: List<Key>
) : ClickFrame(frame, keys) {
    var hitPointPercentage: Pair<Double, Double> = 0.0 to 0.0
    val timingDistribution = frame.time - hitObject.time
}

open class ClickFrame(
    val frame: ReplayFrame,
    val keys: List<Key>
)