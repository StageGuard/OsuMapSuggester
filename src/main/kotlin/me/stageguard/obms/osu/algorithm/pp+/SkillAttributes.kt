@file:Suppress("PackageName")

package me.stageguard.obms.osu.algorithm.`pp+`

import me.stageguard.obms.osu.algorithm.pp.DifficultyAttributes

class SkillAttributes(
    stars: Double,
    approachRate: Double,
    overallDifficulty: Double,
    hpDrain: Double,
    circleSize: Double,
    speedStrain: Double,
    aimStrain: Double,
    var jumpAimStrain: Double,
    var flowAimStrain: Double,
    var precisionStrain: Double,
    var staminaStrain: Double,
    var accuracyStrain: Double,
    maxCombo: Int,
    nCircles: Int,
    nSliders: Int,
    nSpinners: Int
) : DifficultyAttributes(
    stars, approachRate, overallDifficulty, hpDrain, circleSize,
    aimStrain, 1.0, speedStrain, maxCombo, nCircles, nSliders, nSpinners
) {
    override fun toString(): String {
        return "SkillAttributes(\n" +
                "\taimStrain=$aimStrain, \n" +
                "\tspeedStrain=$speedStrain, \n" +
                "\tjumpAimStrain=$jumpAimStrain, \n" +
                "\tflowAimStrain=$flowAimStrain, \n" +
                "\tprecisionStrain=$precisionStrain, \n" +
                "\tstaminaStrain=$staminaStrain, \n" +
                "\taccuracyStrain=$accuracyStrain\n)"
    }
}