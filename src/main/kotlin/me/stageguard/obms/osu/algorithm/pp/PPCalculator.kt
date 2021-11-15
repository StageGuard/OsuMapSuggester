package me.stageguard.obms.osu.algorithm.pp

import me.stageguard.obms.osu.algorithm.AbstractPerformanceCalculator
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import java.util.*
import kotlin.math.*
import kotlin.properties.Delegates

open class PPCalculator protected constructor(
    beatmap: Beatmap
) : AbstractPerformanceCalculator<DifficultyAttributes, PPResult<DifficultyAttributes>>(beatmap) {
    var effectiveMissCount by Delegates.notNull<Int>()

    private fun calculateAim(totalHits: Int) : Double {
        val attributes = this.attributes.get()
        // TD penalty
        val rawAim = attributes.aimStrain.run {
            if(this@PPCalculator.mods.td()) this.pow(0.8) else this
        }

        var aimValue = (5.0 * max(rawAim / 0.0675, 1.0) - 4.0).pow(3) / 100_000.0

        // Longer maps are worth more
        val lenBonus = 0.95 + 0.4 * min(totalHits / 2000.0, 1.0) +
                (if(totalHits > 2000.0) 0.5 * log10(totalHits / 2000.0) else 0.0)
        aimValue *= lenBonus

        // Penalize misses
        if (effectiveMissCount > 0) {
            aimValue *= 0.97 * (1.0 - (effectiveMissCount.toDouble() / totalHits).pow(0.775)).pow(effectiveMissCount)
        }

        // Combo scaling
        combo.filter { attributes.maxCombo > 0 }.ifPresent { combo ->
            aimValue *= min((combo.toDouble() / attributes.maxCombo.toDouble()).pow(0.8), 1.0)
        }

        // AR bonus
        var approachRateFactor = 0.0
        if (attributes.approachRate > 10.33) {
            approachRateFactor += attributes.approachRate - 10.33
        } else if (attributes.approachRate < 8.0) {
            approachRateFactor += 0.1 * (8.0 - attributes.approachRate)
        }
        /*aimValue *= 1.0 + min(approachRateFactor, approachRateFactor * totalHits / 1000.0)

        val approachRateTotalHitsFactor: Double = 1.0 / (1.0 + exp(-(0.007 * (totalHits - 400))))
        val approachRateBonus = 1.0 + (0.03 + 0.37 * approachRateTotalHitsFactor) * approachRateFactor*/
        aimValue *= 1.0 + approachRateFactor * lenBonus

        // HD bonus
        if (mods.hd()) {
            aimValue *= 1.0 + 0.04 * (12.0 - attributes.approachRate)
        }

        // flashlight skill have moved to a new strain skill
        /*var flashlightBonus = 1.0
        if (mods.fl()) {
            flashlightBonus *= 1.0 +
                0.35 * min(totalHits / 200.0, 1.0) +
                    if (totalHits > 200)
                        0.3 * min(1.0, (totalHits - 200) / 300.0) +
                        if (totalHits > 500) (totalHits - 500) / 1200.0 else 0.0
                    else 0.0
        }

        aimValue *= max(approachRateBonus, flashlightBonus)*/

        val estimateDifficultSliders = attributes.nSliders * 0.15
        if (attributes.nSliders > 0 && combo.isPresent) {
            val estimateSliderEndsDropped = (n50.get() + n100.get() + nMisses)
                    .coerceAtMost(attributes.maxCombo - combo.get()).toDouble()
                    .coerceIn(0.0, estimateDifficultSliders)
            val sliderNerfFactor = (1.0 - attributes.sliderFactor) *
                    (1.0 - estimateSliderEndsDropped / estimateDifficultSliders).pow(3) +
                    attributes.sliderFactor
            aimValue *= sliderNerfFactor
        }

        // Scale with accuracy
        aimValue *= acc.get()
        aimValue *= 0.98 + attributes.overallDifficulty.pow(2) / 2500.0

        return aimValue
    }

    private fun calculateSpeed(totalHits: Int) : Double {
        val attributes = this.attributes.get()

        var speedValue = (5.0 * (attributes.speedStrain / 0.0675).coerceAtLeast(1.0) - 4.0).pow(3) / 100_000.0

        // Longer maps are worth more
        val lenBonus = 0.95 + 0.4 * (totalHits / 2000.0).coerceAtMost(1.0) +
                (if(totalHits > 2000.0) 0.5 * log10(totalHits / 2000.0) else 0.0)
        speedValue *= lenBonus

        // Penalize misses
        if (effectiveMissCount > 0) {
            speedValue *= 0.97 * (1.0 - (effectiveMissCount.toDouble() / totalHits).pow(0.775)).pow(
                effectiveMissCount.toDouble().pow(.875)
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

    private fun calculateAccuracy(totalHits: Int) : Double {
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

    private fun calculateEffectiveMissCount(): Int {
        var comboBasedMissCount = 0.0
        with(attributes.get()) {
            if (nSliders > 0) {
                val fullComboThreshold = maxCombo - 0.1 * nSliders
                if (combo.isPresent && combo.get() < fullComboThreshold) comboBasedMissCount =
                    fullComboThreshold / combo.get().toDouble().coerceAtLeast(1.0)
            }
        }
        comboBasedMissCount = comboBasedMissCount.coerceAtMost(totalHits.toDouble())
        return nMisses.coerceAtLeast(floor(comboBasedMissCount).toInt())
    }

    override fun calculate() : PPResult<DifficultyAttributes> {
        if(this.attributes.isEmpty) {
            this.attributes = Optional.of(
                beatmap.calculateDifficultyAttributes(mods, passedObjects, useOutdatedAlgorithm)
            )
        }
        assertHitResults()

        effectiveMissCount = calculateEffectiveMissCount()

        var multiplier = 1.12

        // NF penalty
        if (mods.nf()) {
            multiplier *= max(1.0 - 0.02 * nMisses.toDouble(), 0.9)
        }

        // SO penalty
        if (mods.so()) {
            multiplier *= 1.0 - (attributes.get().nSpinners.toDouble() / totalHits).pow(0.85)
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
            attributes = attributes.get()
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
    val attributes: ATTR? = null
) {
    override fun toString(): String {
        return "PPResult(total=$total, aim=$aim, speed=$speed, accuracy=$accuracy, attributes=$attributes)"
    }
}