package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.beatmap.Beatmap
import me.stageguard.obms.algorithm.beatmap.ModCombination
import me.stageguard.obms.algorithm.beatmap.Mod
import java.util.*
import kotlin.math.*

class PPCalculator private constructor(
    val beatmap: Beatmap
) {
    private var attributes: Optional<DifficultyAttributes> = Optional.empty()
    private var mods: ModCombination = ModCombination.of(Mod.None)
    private var combo: Optional<Int> = Optional.empty()
    private var acc: Optional<Double> = Optional.empty()

    private var n300: Optional<Int> = Optional.empty()
    private var n100: Optional<Int> = Optional.empty()
    private var n50: Optional<Int> = Optional.empty()
    private var nMisses: Int = 0
    private var passedObjects: Optional<Int> = Optional.empty()

    private val totalHits get() = kotlin.run {
        val nObjects = this.passedObjects.orElse(this.beatmap.hitObjects.size)
        min(this.n300.orElse(0) + this.n100.orElse(0) + this.n50.orElse(0) + this.nMisses, nObjects)
    }

    @Suppress("unused") fun attributes(attr: Optional<DifficultyAttributes>) = this.also {
        attr.ifPresent { this.attributes = Optional.of(it) }
    }
    @Suppress("unused") fun mods(vararg mods: Mod) = this.also { this.mods = ModCombination.of(*mods) }
    @Suppress("unused") fun combo(cb: Int) = this.also { this.combo = Optional.of(cb) }
    @Suppress("unused") fun n300(n : Int) = this.also { this.n300 = Optional.of(n) }
    @Suppress("unused") fun n100(n: Int) = this.also { this.n100 = Optional.of(n) }
    @Suppress("unused") fun n50(n: Int) = this.also { this.n50 = Optional.of(n) }
    @Suppress("unused") fun misses(n: Int) = this.also { this.nMisses = n }
    @Suppress("unused") fun passedObjects(n: Int) = this.also { this.passedObjects = Optional.of(n) }
    @Suppress("unused") fun accuracy(acc: Double) = this.also {
        val nObjects = this.passedObjects.orElse(beatmap.hitObjects.size)

        val accuracy = acc / 100.0

        if (this.n100.isPresent && this.n50.isPresent) {
            var n100 = this.n100.orElse(0)
            var n50 = this.n50.orElse(0)

            val placedPoints = 2 * n100 + n50 + this.nMisses
            val missingObjects = nObjects - n100 - n50 - this.nMisses
            val missingPoints = max(0, round(6.0 * accuracy * nObjects.toDouble()).toInt() - placedPoints)

            var n300 = min(missingObjects, missingPoints / 6)
            n50 += missingObjects - n300

            this.n50.filter { this.n100.isEmpty }.ifPresent { originalN50 ->
                val difference = n50 - originalN50
                val n = min(n300, difference / 4)

                n300 -= n
                n100 += 5 * n
                n50 -= 4 * n
            }

            this.n300 = Optional.of(n300)
            this.n100 = Optional.of(n100)
            this.n50 = Optional.of(n50)

        } else {
            val misses = min(this.nMisses, nObjects)
            val targetTotal = round(accuracy * nObjects.toDouble() * 6.0).toInt()
            val delta = targetTotal - (nObjects - misses)

            var n300 = delta / 5
            var n100 = min(delta % 5, nObjects - n300 - misses)
            var n50 = nObjects - n300 - n100 - misses

            val n = min(n300, n50 / 4)
            n300 -= n
            n100 += 5 * n
            n50 -= 4 * n

            this.n300 = Optional.of(n300)
            this.n100 = Optional.of(n100)
            this.n50 = Optional.of(n50)
        }
        this.acc = try {
            Optional.of((6 * this.n300.get() + 2 * this.n100.get() + this.n50.get()).toDouble() / (6 * nObjects).toDouble())
        } catch (ex: Exception) {
            Optional.empty<Double>()
        }
    }

    private fun assertHitResults() {
        if(this.acc.isEmpty) {
            val nObjects = this.passedObjects.orElse(this.beatmap.hitObjects.size)

            val remaining = max(0,
                max(0,
                    max(0,
                        max(0,
                            max(0, nObjects - this.n300.orElse(0))
                        ) - this.n100.orElse(0)
                    ) - this.n50.orElse(0)
                ) - this.nMisses
            )

            if(remaining > 0) {
                when {
                    this.n300.isEmpty -> {
                        this.n300 = Optional.of(remaining)
                    }
                    this.n100.isEmpty -> {
                        this.n100 = Optional.of(remaining)
                    }
                    this.n50.isEmpty -> {
                        this.n50 = Optional.of(remaining)
                    }
                    else -> {
                        this.n300 = Optional.of(this.n300.get() + remaining)
                    }
                }
            }

            val n300 = 0.run {
                if(this@PPCalculator.n300.isEmpty) this@PPCalculator.n300 = Optional.of(this)
                this@PPCalculator.n300.get()
            }

            val n100 = 0.run {
                if(this@PPCalculator.n100.isEmpty) this@PPCalculator.n100 = Optional.of(this)
                this@PPCalculator.n100.get()
            }

            val n50 = 0.run {
                if(this@PPCalculator.n50.isEmpty) this@PPCalculator.n50 = Optional.of(this)
                this@PPCalculator.n50.get()
            }

            val numerator = n300 * 6 + n100 * 2 + n50
            this.acc = Optional.of(numerator.toDouble() / nObjects.toDouble() / 6.0)
        }
    }

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
            aimValue *= 0.97 * (1.0 - (this.nMisses / totalHits).pow(0.775)).pow(this.nMisses)
        }

        // Combo scaling
        this.combo.filter { attributes.maxCombo > 0 }.ifPresent { combo ->
            aimValue *= min((combo.toDouble() / attributes.maxCombo.toDouble()).pow(0.8), 1.0)
        }

        // AR bonus
        var approachRateFactor = 0.0
        if (attributes.approachRate > 10.33) {
            approachRateFactor += 0.4 * (attributes.approachRate - 10.33)
        } else if (attributes.approachRate < 8.0) {
            approachRateFactor += 0.01 * (8.0 - attributes.approachRate)
        }
        aimValue *= 1.0 + min(approachRateFactor, approachRateFactor * totalHits / 1000.0)

        // HD bonus
        if (this.mods.hd()) {
            aimValue *= 1.0 + 0.04 * (12.0 - attributes.approachRate)
        }

        // FL bonus
        if (this.mods.fl()) {
            aimValue *= 1.0 +
                0.35 * min(totalHits / 200.0, 1.0) +
                (if(totalHits > 200) 1.0 else 0.0) * 0.3 * min((totalHits - 200.0) / 300.0, 1.0) +
                (if(totalHits > 500) 1.0 else 0.0) * (totalHits - 500.0) / 1200.0
        }

        // Scale with accuracy
        aimValue *= 0.5 + this.acc.get() / 2.0
        aimValue *= 0.98 + attributes.overallDifficulty * attributes.overallDifficulty / 2500.0

        return aimValue
    }

    private fun calculateSpeed(totalHits: Double) : Double {
        val attributes = this.attributes.get()

        var speedValue = (5.0 * max(attributes.speedStrain / 0.0675, 1.0) - 4.0).pow(3) / 100_000.0

        // Longer maps are worth more
        val lenBonus = 0.95 + 0.4 * min(totalHits / 2000.0, 1.0) +
                (if(totalHits > 2000.0) 1.0 else 0.0) * 0.5 * log10(totalHits / 2000.0)
        speedValue *= lenBonus

        // Penalize misses
        if (this.nMisses > 0) {
            speedValue *= 0.97 * (1.0 - (this.nMisses / totalHits).pow(0.775)).pow(this.nMisses).pow(0.875)
        }

        // Combo scaling
        this.combo.filter { attributes.maxCombo > 0 }.ifPresent { combo ->
            speedValue *= min((combo.toDouble() / attributes.maxCombo.toDouble()).pow(0.8), 1.0)
        }

        // AR bonus
        if (attributes.approachRate > 10.33) {
            val approachRateFactor = 0.4 * (attributes.approachRate - 10.33)
            speedValue *= 1.0 + min(approachRateFactor, approachRateFactor * totalHits / 1000.0)
        }

        // HD bonus
        if (this.mods.hd()) {
            speedValue *= 1.0 + 0.04 * (12.0 - attributes.approachRate)
        }

        // Scaling the speed value with accuracy and OD
        val overallDifficultyFactor = 0.95 + attributes.overallDifficulty.pow(2) / 750.0
        val accuracyFactor = this.acc.get().pow((14.5 - max(attributes.overallDifficulty,8.0)) / 2.0)
        speedValue *= overallDifficultyFactor * accuracyFactor

        // Penalize n50s
        speedValue *= 0.98.pow(max(this.n50.orElse(0).toDouble() - totalHits / 500.0, 0.0))

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

    fun calculate() : PPResult {
        if(this.attributes.isEmpty) {
            this.attributes = Optional.of(beatmap.stars(this.mods, this.passedObjects))
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
            accuracy = accValue
        )
    }

    companion object {
        fun of(beatmap: Beatmap) = PPCalculator(beatmap)
    }

}

data class PPResult(
    val total: Double,
    val aim: Double,
    val speed: Double,
    val accuracy: Double
)