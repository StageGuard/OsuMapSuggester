@file:Suppress("PackageName")

package me.stageguard.obms.algorithm.`pp+`.skill

import me.stageguard.obms.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.algorithm.beatmap.ModCombination

class JumpSkill4PPPlus(mods: ModCombination) : AimSkill4PPPlus(mods) {
    override fun strainValueOf(current: DifficultyObject4PPPlus): Double {
        val jumpAim = calculateJumpAimValue(current)

        val aimValue =  jumpAim * calculateSmallCircleBonus(current.base.radius)
        val readingMultiplier = calculateReadingMultiplier(current)

        return aimValue * readingMultiplier
    }
}