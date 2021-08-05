package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.AbstractPerformanceCalculator
import me.stageguard.obms.algorithm.beatmap.Beatmap
import me.stageguard.obms.algorithm.beatmap.ModCombination
import me.stageguard.obms.algorithm.beatmap.Mod
import java.util.*
import kotlin.math.*

open class PPCalculator protected constructor(
    beatmap: Beatmap
) : AbstractPerformanceCalculator<DifficultyAttributes, PPResult<DifficultyAttributes>>(beatmap) {
    private fun calculateAim(totalHits: Double) : Double {
        val attributes = this.attributes.get()
        // TD penalty
        val rawAim = attributes.aimStrain.run {
            if(this@PPCalculator.mods.td()) this.pow(0.8) else this
        }

        var aimValue = (5.0 * max(rawAim / 0.0675, 1.0) - 4.0).pow(3) / 100_000.0

        // Longer maps are worth more
        val lenBonus = 0.95 + 0.4 * min(totalHits / 2000.0, 1.0) +
                (if(totalHits > 2000.0) 1.0 else 0.0) * 0.5 * log10(totalHits / 2000.0)
        aimValue *= lenBonus

        // Penalize misses
        if (this.nMisses > 0) {
            aimValue *= 0.97 * (1.0 - (this.nMisses.toDouble() / totalHits).pow(0.775)).pow(this.nMisses)
        }

        // Combo scaling
        this.combo.filter { attributes.maxCombo > 0 }.ifPresent { combo ->
            aimValue *= min((combo.toDouble() / attributes.maxCombo.toDouble()).pow(0.8), 1.0)
        }

        // AR bonus
        var approachRateFactor = 0.0
        if (attributes.approachRate > 10.33) {
            approachRateFactor += attributes.approachRate - 10.33
        } else if (attributes.approachRate < 8.0) {
            approachRateFactor += 0.025 * (8.0 - attributes.approachRate)
        }
        //aimValue *= 1.0 + min(approachRateFactor, approachRateFactor * totalHits / 1000.0)

        val approachRateTotalHitsFactor: Double = 1.0 / (1.0 + exp(-(0.007 * (totalHits - 400))))
        val approachRateBonus = 1.0 + (0.03 + 0.37 * approachRateTotalHitsFactor) * approachRateFactor

        // HD bonus
        if (this.mods.hd()) {
            aimValue *= 1.0 + 0.04 * (12.0 - attributes.approachRate)
        }

        // FL bonus
        var flashlightBonus = 1.0
        if (this.mods.fl()) {
            flashlightBonus *= 1.0 +
                0.35 * min(totalHits / 200.0, 1.0) +
                    (if (totalHits > 200)
                        (0.3 * min(1.0, (totalHits - 200) / 300.0) + ( if (totalHits > 500)
                                (totalHits - 500) / 1200.0
                        else 0.0))
                    else 0.0)
        }

        aimValue *= max(approachRateBonus, flashlightBonus)

        // Scale with accuracy
        aimValue *= 0.5 + this.acc.get() / 2.0
        aimValue *= 0.98 + attributes.overallDifficulty * attributes.overallDifficulty / 2500.0

        return aimValue
    }

    private fun calculateSpeed(totalHits: Double) : Double {
        val attributes = this.attributes.get()

        var speedValue = (5.0 * 1.0.coerceAtLeast(attributes.speedStrain / 0.0675) - 4.0).pow(3.0) / 100000.0

        // Longer maps are worth more
        val lenBonus = 0.95 + 0.4 * (totalHits / 2000.0).coerceAtMost(1.0) +
                (if(totalHits > 2000.0) 1.0 else 0.0) * 0.5 * log10(totalHits / 2000.0)
        speedValue *= lenBonus

        // Penalize misses
        if (this.nMisses > 0) {
            speedValue *= 0.97 * (1 - (this.nMisses.toDouble() / totalHits).pow(0.775)).pow(
                this.nMisses.toDouble().pow(.875)
            )
        }

        // Combo scaling
        this.combo.filter { attributes.maxCombo > 0 }.ifPresent { combo ->
            speedValue *= (combo.toDouble().pow(0.8) / attributes.maxCombo.toDouble().pow(0.8)).coerceAtMost(1.0)
        }

        // AR bonus
        var approachRateFactor = 0.0
        if (attributes.approachRate > 10.33) {
            approachRateFactor = 0.4 * (attributes.approachRate - 10.33)
        }
        val approachRateTotalHitsFactor: Double = 1.0 / (1.0 + exp(-(0.007 * (totalHits - 400))))
        speedValue *= 1.0 + (0.03 + 0.37 * approachRateTotalHitsFactor) * approachRateFactor


        // HD bonus
        if (this.mods.hd()) {
            speedValue *= 1.0 + 0.04 * (12.0 - attributes.approachRate)
        }

        // Scale the speed value with accuracy and OD
        speedValue *= (0.95 + attributes.overallDifficulty.pow(2.0) / 750) * this.acc.get()
            .pow((14.5 - attributes.overallDifficulty.coerceAtLeast(8.0)) / 2)
        // Scale the speed value with # of 50s to punish double tapping.
        speedValue *= 0.98.pow(
            (if (this.n50.orElse(0) < totalHits / 500.0) 0 else this.n50.orElse(0) - totalHits / 500.0)
                .toDouble()
        )

        return speedValue
    }

    private fun calculateAccuracy(totalHits: Double) : Double {
        val attributes = this.attributes.get()
        val nCircles = attributes.nCircles.toDouble()
        val n300 = this.n300.orElse(0).toDouble()
        val n100 = this.n100.orElse(0).toDouble()
        val n50 = this.n50.orElse(0).toDouble()

        val betterAccPercentage = (if (nCircles > 0.0) 1.0 else 0.0) *
            max(((n300 - (totalHits - nCircles)) * 6.0 + n100 * 2.0 + n50) / (nCircles * 6.0), 0.0)

        var accValue = 1.52163.pow(attributes.overallDifficulty) * betterAccPercentage.pow(24) * 2.83

        // Bonus for many hitcircles
        accValue *= min((nCircles / 1000.0).pow(0.3), 1.15)

        // HD bonus
        if (this.mods.hd()) accValue *= 1.08

        // FL bonus
        if (this.mods.fl()) accValue *= 1.02

        return accValue
    }

    override fun calculate() : PPResult<DifficultyAttributes> {
        if(this.attributes.isEmpty) {
            this.attributes = Optional.of(
                beatmap.calculateDifficultyAttributes(this.mods, this.passedObjects, this.useOutdatedAlgorithm)
            )
        }
        assertHitResults()

        val totalHits = this.totalHits.toDouble()
        var multiplier = 1.12

        // NF penalty
        if (this.mods.nf()) {
            multiplier *= max(1.0 - 0.02 * this.nMisses.toDouble(), 0.9)
        }

        // SO penalty
        if (this.mods.so()) {
            multiplier *= 1.0 - (this.attributes.get().nSpinners.toDouble() / totalHits).pow(0.85)
        }

        val aimValue = calculateAim(totalHits)
        val speedValue = calculateSpeed(totalHits)
        val accValue = calculateAccuracy(totalHits)

        val pp = (aimValue.pow(1.1) + speedValue.pow(1.1) + accValue.pow(1.1)).pow(1.0 / 1.1) * multiplier

        return PPResult(
            total = pp,
            aim = aimValue,
            speed = speedValue,
            accuracy = accValue,
            attributes = this.attributes.get()
        )
    }

    companion object {
        fun of(beatmap: Beatmap) = PPCalculator(beatmap)
    }

}

open class PPResult<ATTR : DifficultyAttributes>(
    val total: Double,
    val aim: Double,
    val speed: Double,
    val accuracy: Double,
    val attributes: ATTR
) {
    override fun toString(): String {
        return "PPResult(total=$total, aim=$aim, speed=$speed, accuracy=$accuracy, attributes=$attributes)"
    }
}