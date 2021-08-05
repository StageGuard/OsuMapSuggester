@file:Suppress("PackageName")

package me.stageguard.obms.algorithm.`pp+`.skill

import me.stageguard.obms.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.algorithm.beatmap.ModCombination
import me.stageguard.obms.algorithm.pp.Skill
import kotlin.math.pow

@Suppress("PrivatePropertyName", "PropertyName")
open class SpeedSkill4PPPlus(mods: ModCombination) : Skill<DifficultyObject4PPPlus>(mods) {
    protected open val SPEED_SKILL_MULTIPLIER: Double = 2600.0
    private val SPEED_STRAIN_DECAY_BASE: Double = 0.1

    override val strainDecayBase: Double
        get() = SPEED_STRAIN_DECAY_BASE
    override val skillMultiplier: Double
        get() = SPEED_SKILL_MULTIPLIER

    override fun strainValueOf(current: DifficultyObject4PPPlus): Double {
        val ms = current.lastTwoStrainTime / 2

        val tapValue = 30 / (ms - 20).pow(2) + 2 / ms
        val streamValue = 12.5 / (ms - 20).pow(2) + 0.25 / ms + 0.005

        return (1 - current.flow) * tapValue + current.flow * streamValue
    }
}