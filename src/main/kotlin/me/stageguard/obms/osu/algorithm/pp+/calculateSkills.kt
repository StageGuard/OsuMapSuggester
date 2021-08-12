@file:Suppress("PackageName")

package me.stageguard.obms.osu.algorithm.`pp+`

import me.stageguard.obms.osu.algorithm.`pp+`.skill.*
import me.stageguard.obms.osu.algorithm.pp.*
import me.stageguard.obms.osu.processor.beatmap.*
import java.util.*
import kotlin.math.*

@Suppress("DuplicatedCode")
fun Beatmap.calculateSkills(
    mods: ModCombination,
    passedObjects: Optional<Int> = Optional.empty()
) : SkillAttributes {
    val take = passedObjects.orElse(hitObjects.size)
    val mapAttributesWithMod = attribute.withMod(mods)

    val skillAttributes = SkillAttributes(
        stars = 0.0,
        approachRate = mapAttributesWithMod.approachRate,
        overallDifficulty = mapAttributesWithMod.overallDifficulty,
        maxCombo = 0, nCircles = this.nCircles, nSpinners = this.nSpinners,
        speedStrain = 0.0,
        aimStrain = 0.0,
        jumpAimStrain = 0.0,
        flowAimStrain = 0.0,
        precisionStrain = 0.0,
        staminaStrain = 0.0,
        accuracyStrain = 0.0
    )

    if(take < 2) return skillAttributes

    val sectionLength = SECTION_LEN * mapAttributesWithMod.clockRate
    val scale = (1.0 - 0.7 * (mapAttributesWithMod.circleSize - 5.0) / 5.0) / 2.0
    val radius = OBJECT_RADIUS * scale
    var scalingFactor = NORMALIZED_RADIUS / radius
    if (radius < 30.0) {
        val smallCircleBonus = min(30.0 - radius, 5.0) / 50.0
        scalingFactor *= 1.0 + smallCircleBonus
    }
    val sliderState = SliderState(this)
    val ticksBuf = mutableListOf<Double>()

    val stackThreshold = difficultyRange(mapAttributesWithMod.approachRate.let {
        if(mods.hr()) it * 1.4 else if(mods.ez()) it * 0.5 else it
    }, OSU_AR_MAX, OSU_AR_AVG, OSU_AR_MIN) * stackLeniency

    val hitObjectMappedIterator = hitObjects.take(take).map { ho ->
        OsuStdObject(
            h = ho,
            beatmap = this,
            mods = mods,
            radius = radius,
            scalingFactor = scalingFactor,
            ticks = ticksBuf,
            attributes = skillAttributes,
            sliderState = sliderState
        )
    }.filterNot { it.stackHeight == -1.0 }.also {
        if (version >= 6) {
            it.stacking(stackThreshold)
        } else {
            it.oldStacking(stackThreshold)
        }
    }.map {
        val stackOffset = it.stackHeight * scale * -6.4
        it.position += HitObjectPosition(stackOffset, stackOffset)
        it
    }.iterator()

    val aimSkill = AimSkill4PPPlus(mods)
    val jumpAimSkill = JumpSkill4PPPlus(mods)
    val flowAimSkill = FlowSkill4PPPlus(mods)
    val rawAimSkill = RawAimSkill4PPPlus(mods)
    val speedSkill = SpeedSkill4PPPlus(mods)
    val staminaSkill = StaminaSkill4PPPlus(mods)
    val rhythmComplexity = RhythmComplexity4PPPlus(mods)

    var currentSectionEnd = ceil(hitObjects[0].startTime / sectionLength) * sectionLength
    var prevPrev: Optional<OsuStdObject> = Optional.empty()
    var prevPrevDifficultyObject: Optional<DifficultyObject4PPPlus> = Optional.empty()
    var prev = hitObjectMappedIterator.next()
    var prevDifficultyObject: Optional<DifficultyObject4PPPlus> = Optional.empty()
    var prevVals: Optional<Pair<Double, Double>> = Optional.empty()

    var curr = hitObjectMappedIterator.next()
    var hDifficultyPoint = DifficultyObject4PPPlus(
        base = curr,
        prev = prev,
        prevVals = prevVals,
        prevPrev = prevPrev,
        clockRate = mapAttributesWithMod.clockRate,
        scalingFactor = scalingFactor,
        prevDifficultyObject = prevDifficultyObject,
        prevPrevDifficultyObject = prevPrevDifficultyObject
    )

    while (hDifficultyPoint.base.time > currentSectionEnd) {
        currentSectionEnd += sectionLength
    }

    aimSkill.process(hDifficultyPoint)
    jumpAimSkill.process(hDifficultyPoint)
    flowAimSkill.process(hDifficultyPoint)
    rawAimSkill.process(hDifficultyPoint)
    staminaSkill.process(hDifficultyPoint)
    speedSkill.process(hDifficultyPoint)
    rhythmComplexity.process(hDifficultyPoint)

    prevPrev = Optional.of(prev)
    prevVals = Optional.of(hDifficultyPoint.run { jumpDist to strainTime })
    prevDifficultyObject = Optional.of(hDifficultyPoint)
    prev = curr

    while (hitObjectMappedIterator.hasNext()) {
        curr = hitObjectMappedIterator.next()
        hDifficultyPoint = DifficultyObject4PPPlus(
            base = curr,
            prev = prev,
            prevVals = prevVals,
            prevPrev = prevPrev,
            clockRate = mapAttributesWithMod.clockRate,
            scalingFactor = scalingFactor,
            prevDifficultyObject = prevDifficultyObject,
            prevPrevDifficultyObject = prevPrevDifficultyObject
        )
        while (hDifficultyPoint.base.time > currentSectionEnd) {
            aimSkill.saveCurrentPeak()
            aimSkill.startNewSectionFrom(currentSectionEnd)
            jumpAimSkill.saveCurrentPeak()
            jumpAimSkill.startNewSectionFrom(currentSectionEnd)
            flowAimSkill.saveCurrentPeak()
            flowAimSkill.startNewSectionFrom(currentSectionEnd)
            staminaSkill.saveCurrentPeak()
            staminaSkill.startNewSectionFrom(currentSectionEnd)
            rawAimSkill.saveCurrentPeak()
            rawAimSkill.startNewSectionFrom(currentSectionEnd)
            speedSkill.saveCurrentPeak()
            speedSkill.startNewSectionFrom(currentSectionEnd)
            currentSectionEnd += sectionLength
        }

        aimSkill.process(hDifficultyPoint)
        jumpAimSkill.process(hDifficultyPoint)
        flowAimSkill.process(hDifficultyPoint)
        rawAimSkill.process(hDifficultyPoint)
        staminaSkill.process(hDifficultyPoint)
        speedSkill.process(hDifficultyPoint)
        rhythmComplexity.process(hDifficultyPoint)

        prevPrev = Optional.of(prev)
        prevPrevDifficultyObject = prevDifficultyObject
        prevVals = Optional.of(hDifficultyPoint.run { jumpDist to strainTime })
        prev = curr
        prevDifficultyObject = Optional.of(hDifficultyPoint)
    }

    aimSkill.saveCurrentPeak()
    jumpAimSkill.saveCurrentPeak()
    flowAimSkill.saveCurrentPeak()
    staminaSkill.saveCurrentPeak()
    rawAimSkill.saveCurrentPeak()
    speedSkill.saveCurrentPeak()

    val aimStrain = sqrt(aimSkill.difficultyValue(true)) * DIFFICULTY_MULTIPLIER
    val jumpAimStrain = sqrt(jumpAimSkill.difficultyValue(true)) * DIFFICULTY_MULTIPLIER
    val flowAimStrain = sqrt(flowAimSkill.difficultyValue(true)) * DIFFICULTY_MULTIPLIER
    val speedStrain = sqrt(speedSkill.difficultyValue(true)) * DIFFICULTY_MULTIPLIER
    val staminaStrain = sqrt(staminaSkill.difficultyValue(true)) * DIFFICULTY_MULTIPLIER
    val precisionStrain = sqrt(
        0.0.coerceAtLeast(
            aimSkill.difficultyValue(true) - rawAimSkill.difficultyValue(true)
        )
    ) * DIFFICULTY_MULTIPLIER
    val accuracyStrain = rhythmComplexity.difficultyValue(true)
    val starRating = (aimStrain.pow(3) + speedStrain.coerceAtLeast(staminaStrain).pow(3)).pow(1 / 3.0) * 1.6

    return skillAttributes.also {
        it.stars = starRating
        it.aimStrain = aimStrain
        it.jumpAimStrain = jumpAimStrain
        it.flowAimStrain = flowAimStrain
        it.precisionStrain = precisionStrain
        it.staminaStrain = staminaStrain
        it.accuracyStrain = accuracyStrain
        it.speedStrain = speedStrain
    }
}