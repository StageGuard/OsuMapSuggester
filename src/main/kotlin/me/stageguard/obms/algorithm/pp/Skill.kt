@file:Suppress("PrivatePropertyName")

package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.beatmap.ModCombination
import me.stageguard.obms.utils.lerp
import java.util.*
import kotlin.math.*

@Suppress("PrivatePropertyName")
abstract class Skill<DO : DifficultyObject>(val mods: ModCombination) {
    private val DECAY_WEIGHT: Double = 0.9
    private val REDUCED_SECTION_COUNT: Int = 10
    private val REDUCED_STRAIN_BASELINE: Double = 0.75
    private val DIFFICULTY_MULTIPLIER: Double = 1.06

    private var currentStrain: Double = 1.0
    private var currentSectionPeak: Double = 1.0
    private val strainPeaks: MutableList<Double> = mutableListOf()
    private var prevTime: Optional<Double> = Optional.empty()

    abstract val strainDecayBase: Double
    abstract val skillMultiplier: Double

    open fun difficultyValue(useOutdatedAlgorithm: Boolean = false) = run {
        var difficulty = 0.0
        var weight = 1.0

        strainPeaks.sortDescending()

        if(!useOutdatedAlgorithm) {
            for (i in 0 until strainPeaks.size.coerceAtMost(REDUCED_SECTION_COUNT)) {
                val scale: Double =
                    log10(lerp(1.0, 10.0, max(0.0, min(i.toDouble() / REDUCED_SECTION_COUNT, 1.0))))
                strainPeaks[i] *= lerp(REDUCED_STRAIN_BASELINE, 1.0, scale)
            }

            strainPeaks.sortDescending()
        }

        for (strain in strainPeaks) {
            difficulty += strain * weight
            weight *= DECAY_WEIGHT
        }

        if(useOutdatedAlgorithm) difficulty else difficulty * DIFFICULTY_MULTIPLIER
    }

    fun saveCurrentPeak() {
        strainPeaks.add(currentSectionPeak)
    }

    fun startNewSectionFrom(time: Double) {
        currentSectionPeak = peakStrain(time - prevTime.get())
    }

    open fun process(current: DO) {
        currentStrain *= strainDecay(current.delta)
        currentStrain += strainValueOf(current) * skillMultiplier
        currentSectionPeak = max(currentSectionPeak, currentStrain)
        prevTime = Optional.of(current.base.time)
    }

    private fun strainDecay(ms: Double) = strainDecayBase.pow(ms / 1000.0)

    private fun peakStrain(deltaTime: Double) = currentStrain * strainDecay(deltaTime)

    abstract fun strainValueOf(current: DO) : Double

    @Suppress("NOTHING_TO_INLINE")
    inline fun applyDiminishingExp(value: Double) = value.pow(0.99)
}