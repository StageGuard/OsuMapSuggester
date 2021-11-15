package me.stageguard.obms.osu.algorithm.pp

open class DifficultyAttributes(
    var stars: Double,
    var approachRate: Double,
    var overallDifficulty: Double,
    var hpDrain: Double,
    var circleSize: Double,
    var aimStrain: Double,
    var sliderFactor: Double,
    var speedStrain: Double,
    var maxCombo: Int,
    var nCircles: Int,
    var nSliders: Int,
    var nSpinners: Int
) {
    override fun toString(): String {
        return "DifficultyAttributes(stars=$stars, approachRate=$approachRate, overallDifficulty=$overallDifficulty, speedStrain=$speedStrain, aimStrain=$aimStrain, maxCombo=$maxCombo, nCircles=$nCircles, nSpinners=$nSpinners)"
    }
}