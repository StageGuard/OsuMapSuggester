@file:Suppress("PackageName")

package me.stageguard.obms.osu.algorithm.`pp+`.skill

import me.stageguard.obms.osu.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.osu.processor.beatmap.ModCombination

class JumpSkill4PPPlus(mods: ModCombination) : AimSkill4PPPlus(mods) {
    override fun strainValueOf(current: DifficultyObject4PPPlus): Double {
        val jumpAim = calculateJumpAimValue(current)

        val aimValue =  jumpAim * calculateSmallCircleBonus(current.base.radius)
        val readingMultiplier = calculateReadingMultiplier(current)

        return aimValue * readingMultiplier
    }
}