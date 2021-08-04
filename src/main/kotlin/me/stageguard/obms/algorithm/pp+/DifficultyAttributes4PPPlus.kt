@file:Suppress("PackageName")

package me.stageguard.obms.algorithm.`pp+`

import me.stageguard.obms.algorithm.pp.DifficultyAttributes

class DifficultyAttributes4PPPlus(
    stars: Double,
    approachRate: Double,
    overallDifficulty: Double,
    speedStrain: Double,
    aimStrain: Double,
    var jumpAimStrain: Double,
    var flowAimStrain: Double,
    var precisionStrain: Double,
    var staminaStrain: Double,
    var accuracyStrain: Double,
    maxCombo: Int,
    nCircles: Int,
    nSpinners: Int
) : DifficultyAttributes(
    stars, approachRate, overallDifficulty,
    speedStrain, aimStrain, maxCombo, nCircles, nSpinners
)