@file:Suppress("PrivatePropertyName")

package me.stageguard.obms.osu.algorithm.pp

import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.lerp
import java.util.*
import kotlin.math.*

@Suppress("PrivatePropertyName")
abstract class Skill<DO : DifficultyObject>(val mods: ModCombination) {
    private val DECAY_WEIGHT: Double = 0.9

    open val reducedStrainBaseline: Double = 0.75
    open val reducedSectionCount: Int = 10
    open val difficultyMultiplier: Double = 1.06

    open var currentStrain: Double = 1.0
    private var currentSectionPeak: Double = 1.0
    private val strainPeaks: MutableList<Double> = mutableListOf()
    private var prevTime: Optional<Double> = Optional.empty()

    abstract val strainDecayBase: Double
    abstract val skillMultiplier: Double

    open val prevObjQueueCapacity = 2

    protected val prevObjQueue by lazy { MutableList<DO?>(prevObjQueueCapacity) { null } }
    val prevObj get() = prevObjQueue[0]
    val prevPrevObj get() = prevObjQueue[1]
    val isPrevQueueHasFirst2Objs get() = prevObjQueue[0] != null && prevObjQueue[1] != null
    val prevCount get() = prevObjQueue.count { it != null }

    private fun putPrevObj(obj: DO) {
        var idx = prevObjQueue.lastIndex
        while (idx > 0) {
            prevObjQueue[idx] = prevObjQueue[idx - 1]
            idx --
        }
        prevObjQueue[0] = obj
    }

    open fun difficultyValue(useOutdatedAlgorithm: Boolean = false) = run {
        var difficulty = 0.0
        var weight = 1.0

        strainPeaks.sortDescending()

        if(!useOutdatedAlgorithm) {
            for (i in 0 until strainPeaks.size.coerceAtMost(reducedSectionCount)) {
                val scale: Double =
                    log10(lerp(1.0, 10.0, max(0.0, min(i.toDouble() / reducedSectionCount, 1.0))))
                strainPeaks[i] *= lerp(reducedStrainBaseline, 1.0, scale)
            }

            strainPeaks.sortDescending()
        }

        for (strain in strainPeaks) {
            difficulty += strain * weight
            weight *= DECAY_WEIGHT
        }

        if(useOutdatedAlgorithm) difficulty else difficulty * difficultyMultiplier
    }

    fun saveCurrentPeak() {
        strainPeaks.add(currentSectionPeak)
    }

    fun startNewSectionFrom(time: Double) {
        currentSectionPeak = calculateInitialStrain(time)
    }

    open fun process(current: DO) {
        currentSectionPeak = max(currentSectionPeak, strainValueAt(current))
        prevTime = Optional.of(current.base.time)

        putPrevObj(current)
    }

    protected fun strainDecay(ms: Double) = strainDecayBase.pow(ms / 1000.0)

    private fun peakStrain(deltaTime: Double) = currentStrain * strainDecay(deltaTime)

    open fun strainValueAt(current: DO) : Double {
        currentStrain *= strainDecay(current.delta)
        currentStrain += strainValueOf(current) * skillMultiplier
        return currentStrain
    }
    open fun calculateInitialStrain(time: Double) = peakStrain(time - prevTime.get())

    abstract fun strainValueOf(current: DO) : Double

    @Suppress("NOTHING_TO_INLINE")
    inline fun applyDiminishingExp(value: Double) = value.pow(0.99)
}