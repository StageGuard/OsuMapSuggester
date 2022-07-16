package me.stageguard.obms.osu.algorithm

import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.osu.algorithm.pp.DifficultyAttributes
import me.stageguard.obms.osu.algorithm.pp.PPResult
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

abstract class AbstractJvmPerformanceCalculator
<DO : DifficultyAttributes, R : PPResult<DO>>(val beatmap: Beatmap) : PerformanceCalculator<R> {
    protected var attributes: Optional<DO> = Optional.empty()
    protected var mods: ModCombination = ModCombination.of(Mod.None)
    protected var combo: Optional<Int> = Optional.empty()
    protected var acc: Optional<Double> = Optional.empty()

    protected var n300: Optional<Int> = Optional.empty()
    protected var n100: Optional<Int> = Optional.empty()
    protected var n50: Optional<Int> = Optional.empty()
    protected var nMisses: Int = 0
    protected var passedObjects: Optional<Int> = Optional.empty()
    protected var useOutdatedAlgorithm: Boolean = false

    protected val totalHits get() = kotlin.run {
        val nObjects = this.passedObjects.orElse(this.beatmap.hitObjects.size)
        min(this.n300.orElse(0) + this.n100.orElse(0) + this.n50.orElse(0) + this.nMisses, nObjects)
    }


    @Suppress("unused") override fun mods(vararg mods: Mod) = this.also { this.mods = ModCombination.of(*mods) }
    @Suppress("unused") override fun mods(mods: List<Mod>) = this.also { this.mods = ModCombination.of(mods) }
    @Suppress("unused") override fun combo(cb: Int) = this.also { this.combo = Optional.of(cb) }
    @Suppress("unused") override fun n300(n : Int) = this.also { this.n300 = Optional.of(n) }
    @Suppress("unused") override fun n100(n: Int) = this.also { this.n100 = Optional.of(n) }
    @Suppress("unused") override fun n50(n: Int) = this.also { this.n50 = Optional.of(n) }
    @Suppress("unused") override fun misses(n: Int) = this.also { this.nMisses = n }
    @Suppress("unused") override fun passedObjects(n: Int) = this.also { this.passedObjects = Optional.of(n) }
    @Suppress("unused") fun outdatedAlgorithm() = this.also { this.useOutdatedAlgorithm = true }
    @Suppress("unused") override fun accuracy(acc: Double) = this.also {
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

    protected fun assertHitResults() {
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
                if(this@AbstractJvmPerformanceCalculator.n300.isEmpty)
                    this@AbstractJvmPerformanceCalculator.n300 = Optional.of(this)
                this@AbstractJvmPerformanceCalculator.n300.get()
            }

            val n100 = 0.run {
                if(this@AbstractJvmPerformanceCalculator.n100.isEmpty)
                    this@AbstractJvmPerformanceCalculator.n100 = Optional.of(this)
                this@AbstractJvmPerformanceCalculator.n100.get()
            }

            val n50 = 0.run {
                if(this@AbstractJvmPerformanceCalculator.n50.isEmpty)
                    this@AbstractJvmPerformanceCalculator.n50 = Optional.of(this)
                this@AbstractJvmPerformanceCalculator.n50.get()
            }

            val numerator = n300 * 6 + n100 * 2 + n50
            this.acc = Optional.of(numerator.toDouble() / nObjects.toDouble() / 6.0)
        }
    }

    abstract override fun calculate() : R
}
