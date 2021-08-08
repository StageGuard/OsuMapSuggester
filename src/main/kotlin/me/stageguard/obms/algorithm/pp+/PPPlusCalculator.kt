package me.stageguard.obms.algorithm.`pp+`

import me.stageguard.obms.algorithm.AbstractPerformanceCalculator
import me.stageguard.obms.algorithm.beatmap.Beatmap
import me.stageguard.obms.algorithm.beatmap.ModCombination
import me.stageguard.obms.algorithm.pp.PPResult
import me.stageguard.obms.utils.betaInvCDF
import me.stageguard.obms.utils.normalInvCDF
import java.util.*
import kotlin.math.*

class PPPlusCalculator private constructor(
    beatmap: Beatmap
) : AbstractPerformanceCalculator<SkillAttributes, PPPlusResult>(beatmap) {

    private fun calculateSkillValue(skillDiff: Double) = skillDiff.pow(3) * 3.9

    private fun calculateNormalisedHitError(od: Double, objectCount: Int, circleCount: Int, count300: Int) : OptionalDouble {
        val circle300Count = count300 - (objectCount - circleCount)
        if (circle300Count <= 0)
            return OptionalDouble.empty()

        var probability = betaInvCDF(circle300Count.toDouble(), 1.0 + circleCount - circle300Count, 0.2)

        probability += (1 - probability) / 2
        val zValue = normalInvCDF(0.0, 1.0, probability)

        val hitWindow = 79.5 - od * 6
        return OptionalDouble.of(hitWindow / zValue)
    }

    private fun calculateMissWeight(misses: Int) = 0.97.pow(misses)

    private fun calculateAimWeight(
        missWeight: Double, normalizedHitError: OptionalDouble,
        combo: Int, maxCombo: Int, objectCount: Int, visualMods: ModCombination
    ) : Double {
        val accuracyWeight = if (normalizedHitError.isPresent) 0.995.pow(normalizedHitError.asDouble) * 1.04 else 0.0
        val comboWeight = combo.toDouble().pow(0.8) / maxCombo.toDouble().pow(0.8)
        val flashlightLengthWeight = if(visualMods.fl()) 1 + atan(objectCount / 2000.0) else 1.0

        return accuracyWeight * comboWeight * missWeight * flashlightLengthWeight
    }

    private fun calculateSpeedWeight(
        missWeight: Double, normalizedHitError: OptionalDouble,
        combo: Int, maxCombo: Int
    ) : Double {
        val accuracyWeight = if (normalizedHitError.isPresent) 0.985.pow(normalizedHitError.asDouble) * 1.12 else 0.0
        val comboWeight = combo.toDouble().pow(0.4) / maxCombo.toDouble().pow(0.4)

        return accuracyWeight * comboWeight * missWeight
    }

    private fun calculateAccuracyWeight(circleCount: Int, visualMods: ModCombination) : Double {
        val lengthWeight = tanh((circleCount + 400) / 1050.0) * 1.2

        var modWeight = 1.0
        if(visualMods.hd()) {
            modWeight *= 1.02
        } else if(visualMods.fl()) {
            modWeight *= 1.04
        }

        return lengthWeight * modWeight
    }

    private fun calculateAccuracyValue(normalizedHitError: OptionalDouble) =
        if(normalizedHitError.isPresent) 560 * 0.85.pow(normalizedHitError.asDouble) else 0.0

    override fun calculate() : PPPlusResult {
        if(this.attributes.isEmpty) {
            this.attributes = Optional.of(
                beatmap.calculateSkills(this.mods, this.passedObjects)
            )
        }
        assertHitResults()
        var multiplier = 1.12

        // NF penalty
        if (this.mods.nf()) {
            multiplier *= max(1.0 - 0.02 * this.nMisses.toDouble(), 0.9)
        }

        // SO penalty
        if (this.mods.so()) {
            multiplier *= 1.0 - (this.attributes.get().nSpinners.toDouble() / totalHits).pow(0.85)
        }

        val normalisedHitError: OptionalDouble = calculateNormalisedHitError(
            this.attributes.get().overallDifficulty,
            totalHits, this.attributes.get().nCircles, this.n300.get()
        )
        val missWeight = calculateMissWeight(this.nMisses)
        val aimWeight = calculateAimWeight(
            missWeight, normalisedHitError, this.combo.orElse(this.attributes.get().maxCombo), this.attributes.get().maxCombo,
            totalHits, this.mods
        )
        val speedWeight = calculateSpeedWeight(
            missWeight, normalisedHitError, this.combo.orElse(this.attributes.get().maxCombo),
            this.attributes.get().maxCombo
        )
        val accuracyWeight = calculateAccuracyWeight(this.attributes.get().nCircles, this.mods)

        val aimValue = calculateSkillValue(this.attributes.get().aimStrain) * aimWeight
        val jumpAimValue = calculateSkillValue(this.attributes.get().jumpAimStrain) * aimWeight
        val flowAimValue = calculateSkillValue(this.attributes.get().flowAimStrain) * aimWeight
        val precisionValue = calculateSkillValue(this.attributes.get().precisionStrain) * aimWeight
        val speedValue = calculateSkillValue(this.attributes.get().speedStrain) * speedWeight
        val staminaValue = calculateSkillValue(this.attributes.get().staminaStrain) * speedWeight
        val accuracyValue: Double =
            calculateAccuracyValue(normalisedHitError) * this.attributes.get().accuracyStrain * accuracyWeight

        val pp = (aimValue.pow(1.1) +
                max(speedValue, staminaValue).pow(1.1) +
                accuracyValue.pow(1.1)).pow(1.0 / 1.1) * multiplier

        return PPPlusResult(
            total = pp,
            aim = aimValue,
            jumpAim = jumpAimValue,
            flowAim = flowAimValue,
            speed = speedValue,
            accuracy = accuracyValue,
            stamina = staminaValue,
            precision = precisionValue,
            attributes = this.attributes.get()
        )
    }

    companion object {
        fun of(beatmap: Beatmap) = PPPlusCalculator(beatmap)
    }

}

class PPPlusResult(
    total: Double,
    aim: Double,
    val jumpAim: Double,
    val flowAim: Double,
    speed: Double,
    accuracy: Double,
    val stamina: Double,
    val precision: Double,
    attributes: SkillAttributes? = null
) : PPResult<SkillAttributes>(
    total, aim, speed, accuracy, attributes
) {
    override fun toString(): String {
        return "PPPlusResult(aim=$aim, jumpAim=$jumpAim, flowAim=$flowAim, speed=$speed, accuracy=$accuracy, stamina=$stamina, precision=$precision)"
    }
}