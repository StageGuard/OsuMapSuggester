@file:Suppress("PackageName")

package me.stageguard.obms.algorithm.`pp+`.skill

import me.stageguard.obms.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.algorithm.beatmap.ModCombination

class RawAimSkill4PPPlus(mods: ModCombination) : AimSkill4PPPlus(mods) {
    override fun calculateAimValue(current: DifficultyObject4PPPlus): Double {
        return calculateFlowAimValue(current) + calculateJumpAimValue(current)
    }
}