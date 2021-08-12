@file:Suppress("PackageName")

package me.stageguard.obms.osu.algorithm.`pp+`.skill

import me.stageguard.obms.osu.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.osu.processor.beatmap.ModCombination

class RawAimSkill4PPPlus(mods: ModCombination) : AimSkill4PPPlus(mods) {
    override fun strainValueOf(current: DifficultyObject4PPPlus): Double {
        val rawAimValue = calculateFlowAimValue(current) + calculateJumpAimValue(current)
        val readingMultiplier = calculateReadingMultiplier(current)

        return rawAimValue * readingMultiplier
    }
}