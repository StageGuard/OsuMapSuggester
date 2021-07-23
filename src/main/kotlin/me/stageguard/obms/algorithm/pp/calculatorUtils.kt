package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.beatmap.Beatmap
import me.stageguard.obms.algorithm.beatmap.ModCombination
import me.stageguard.obms.algorithm.beatmap.SliderState
import java.util.*
import kotlin.math.floor
import kotlin.math.min
import kotlin.properties.Delegates

const val OSU_OD_MAX = 20.0
const val OSU_OD_AVG = 50.0
const val OSU_OD_MIN = 80.0
const val OSU_AR_MAX = 450.0
const val OSU_AR_AVG = 1200.0
const val OSU_AR_MIN = 1800.0
const val SECTION_LEN = 400.0
const val OBJECT_RADIUS = 64.0
const val NORMALIZED_RADIUS = 52.0

/*fun Beatmap.stars(mods: ModCombination, passedObjects: Optional<Int>) : DifficultyAttributes {
    val take = passedObjects.orElse(hitObjects.size)

    val mapAttributes = attribute.withMod(mods)
    val hitWindow = floor(difficultyRange(mapAttributes.overallDifficulty, OSU_OD_MAX, OSU_OD_AVG, OSU_OD_MIN)) / mapAttributes.clockRate
    val od = (OSU_OD_MIN - hitWindow) / 6.0

    val initialResult = DifficultyAttributes(
        stars = 0.0, approachRate = mapAttributes.approachRate,
        overallDifficulty = od, speedStrain = 0.0, aimStrain = 0.0,
        maxCombo = 0, nCircles = 0, nSpinners = 0
    )

    if(take < 2) {
        return initialResult
    } else {
        var rawApproachRate = attribute.approachRate.let {
            if(mods.hr()) it * 1.4 else if(mods.ez()) it * 0.5 else it
        }
        val timePreempt = difficultyRange(rawApproachRate, OSU_AR_MAX, OSU_AR_AVG, OSU_AR_MIN)

        val sectionLength = SECTION_LEN * mapAttributes.clockRate
        val scale = (1.0 - 0.7 * (mapAttributes.circleSize - 5.0) / 5.0) / 2.0
        val radius = OBJECT_RADIUS * scale
        var scalingFactor = NORMALIZED_RADIUS / radius
        if (radius < 30.0) {
            val smallCircleBonus = min(30.0 - radius, 5.0) / 50.0
            scalingFactor *= 1.0 + smallCircleBonus
        }
        val sliderState = SliderState(this)
        val ticksBuf = mutableListOf<>()
    }
}*/




fun difficultyRange(value: Double, max: Double, avg: Double, min: Double) = if (value > 5.0) {
    avg + (max - avg) * (value - 5.0) / 5.0
} else if (value < 5.0) {
    avg - (avg - min) * (5.0 - value) / 5.0
} else { avg }