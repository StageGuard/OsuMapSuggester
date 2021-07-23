package me.stageguard.obms.algorithm.beatmap

import kotlin.math.max
import kotlin.math.min

@Suppress("PrivatePropertyName")
class BeatmapAttribute(
    var approachRate: Double,
    var overallDifficulty: Double,
    var circleSize: Double,
    var hpDrainRate: Double,
    var clockRate: Double = 1.0,
) {
    private val AR0_MS: Double = 1800.0
    private val AR5_MS: Double = 1200.0
    private val AR10_MS: Double = 450.0
    private val AR_MS_STEP_1: Double = (AR0_MS - AR5_MS) / 5.0
    private val AR_MS_STEP_2: Double = (AR5_MS - AR10_MS) / 5.0

    fun withMod(mods: ModCombination) :  BeatmapAttribute {
        if(!mods.isScoreMode()) return this

        val speed = mods.speed()
        val multiplier = mods.odArHpMultiplier()

        return BeatmapAttribute(
            clockRate = speed,
            approachRate = kotlin.run {
                val ar = approachRate * multiplier
                val arMillionSecond = min(AR0_MS, max(AR10_MS, if(ar <= 5.0) {
                    AR0_MS - AR_MS_STEP_1 * ar
                } else {
                    AR5_MS - AR_MS_STEP_2 * (ar - 5.0)
                })) / speed
                if (arMillionSecond > AR5_MS) {
                    (AR0_MS - arMillionSecond) / AR_MS_STEP_1
                } else {
                    5.0 + (AR5_MS - arMillionSecond) / AR_MS_STEP_2
                }
            },
            overallDifficulty = min(10.0, overallDifficulty * multiplier),
            circleSize = min(10.0, when {
                mods.hr() -> circleSize * 1.3
                mods.ez() -> circleSize * 0.5
                else -> circleSize
            }),
            hpDrainRate = min(10.0, hpDrainRate * multiplier)
        )
    }
}