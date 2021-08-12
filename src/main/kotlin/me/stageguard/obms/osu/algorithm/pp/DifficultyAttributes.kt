package me.stageguard.obms.osu.algorithm.pp

open class DifficultyAttributes(
    var stars: Double,
    var approachRate: Double,
    var overallDifficulty: Double,
    var speedStrain: Double,
    var aimStrain: Double,
    var maxCombo: Int,
    var nCircles: Int,
    var nSpinners: Int
) {
    override fun toString(): String {
        return "DifficultyAttributes(stars=$stars, approachRate=$approachRate, overallDifficulty=$overallDifficulty, speedStrain=$speedStrain, aimStrain=$aimStrain, maxCombo=$maxCombo, nCircles=$nCircles, nSpinners=$nSpinners)"
    }
}