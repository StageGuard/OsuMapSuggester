@file:Suppress("PackageName")

package me.stageguard.obms.algorithm.`pp+`.skill

import me.stageguard.obms.algorithm.`pp+`.DifficultyObject4PPPlus
import me.stageguard.obms.algorithm.beatmap.ModCombination

@Suppress("PrivatePropertyName")
class StaminaSkill4PPPlus(mods: ModCombination) : SpeedSkill4PPPlus(mods) {
    override val SPEED_SKILL_MULTIPLIER: Double = super.SPEED_SKILL_MULTIPLIER * 0.3
    private val SPEED_STRAIN_DECAY_BASE: Double = 0.45

    override val strainDecayBase: Double
        get() = SPEED_STRAIN_DECAY_BASE
    override val skillMultiplier: Double
        get() = SPEED_SKILL_MULTIPLIER

    override fun strainValueOf(current: DifficultyObject4PPPlus): Double {

        val ms = current.lastTwoStrainTime / 2

        val tapValue = 2 / (ms -20)
        val streamValue = 1 / (ms - 20)

        return (1 - current.flow) * tapValue + current.flow * streamValue
    }
}