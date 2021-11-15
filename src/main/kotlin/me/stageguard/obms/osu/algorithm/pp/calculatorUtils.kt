package me.stageguard.obms.osu.algorithm.pp

import me.stageguard.obms.osu.algorithm.pp.skill.AimSkill
import me.stageguard.obms.osu.algorithm.pp.skill.SpeedSkill
import me.stageguard.obms.osu.processor.beatmap.*
import java.util.*
import kotlin.math.*

const val OSU_OD_MAX = 20.0
const val OSU_OD_AVG = 50.0
const val OSU_OD_MIN = 80.0
const val OSU_AR_MAX = 450.0
const val OSU_AR_AVG = 1200.0
const val OSU_AR_MIN = 1800.0
const val SECTION_LEN = 400.0
const val OBJECT_RADIUS = 64.0
const val NORMALIZED_RADIUS = 50.0
const val DIFFICULTY_MULTIPLIER = 0.0675
const val STACK_DISTANCE = 3.0
const val MIN_DELTA_TIME = 25.0
const val MAXIMUM_SLIDER_RADIUS = NORMALIZED_RADIUS * 2.4f
const val ASSUMED_SLIDER_RADIUS = NORMALIZED_RADIUS * 1.8f

fun Beatmap.calculateDifficultyAttributes(
    mods: ModCombination,
    passedObjects: Optional<Int> = Optional.empty(),
    useOutdatedAlgorithm: Boolean = false
) : DifficultyAttributes {
    val take = passedObjects.orElse(hitObjects.size)
    val mapAttributesWithMod = attribute.withMod(mods)
    val hitWindow = floor(difficultyRange(mapAttributesWithMod.overallDifficulty, OSU_OD_MAX, OSU_OD_AVG, OSU_OD_MIN)) / mapAttributesWithMod.clockRate
    val od = (OSU_OD_MIN - hitWindow) / 6.0

    val initialAttributes = DifficultyAttributes(
        stars = 0.0, approachRate = mapAttributesWithMod.approachRate,
        circleSize = mapAttributesWithMod.circleSize, hpDrain = mapAttributesWithMod.hpDrainRate,
        overallDifficulty = od, aimStrain = 0.0, sliderFactor = 0.0, speedStrain = 0.0,
        maxCombo = 0, nCircles = nCircles, nSliders = nSliders, nSpinners = nSpinners
    )

    if(take < 2) return initialAttributes

    val sectionLength = SECTION_LEN * mapAttributesWithMod.clockRate
    val scale = (1.0 - 0.7 * (mapAttributesWithMod.circleSize - 5.0) / 5.0) / 2.0
    val radius = OBJECT_RADIUS * scale
    var scalingFactor = NORMALIZED_RADIUS / radius
    if (radius < 30.0) {
        val smallCircleBonus = min(30.0 - radius, 5.0) / 50.0
        scalingFactor *= 1.0 + smallCircleBonus
    }
    val sliderState = SliderState(this)

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
            attributes = initialAttributes,
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

    val skills = listOf(AimSkill(mods), AimSkill(mods, false), SpeedSkill(mods, hitWindow))

    var currentSectionEnd = ceil(hitObjects[0].startTime / sectionLength) * sectionLength
    var prevPrev: Optional<OsuStdObject> = Optional.empty()
    var prev = hitObjectMappedIterator.next()

    var curr = hitObjectMappedIterator.next()
    var hDifficultyPoint = DifficultyObject(
        base = curr,
        prev = prev,
        prevPrev = prevPrev,
        clockRate = mapAttributesWithMod.clockRate,
        scalingFactor = scalingFactor
    )

    while (hDifficultyPoint.base.time > currentSectionEnd) {
        currentSectionEnd += sectionLength
    }

    skills.forEach { it.process(hDifficultyPoint) }

    prevPrev = Optional.of(prev)
    prev = curr

    while (hitObjectMappedIterator.hasNext()) {
        curr = hitObjectMappedIterator.next()
        hDifficultyPoint = DifficultyObject(
            base = curr,
            prev = prev,
            prevPrev = prevPrev,
            clockRate = mapAttributesWithMod.clockRate,
            scalingFactor = scalingFactor
        )
        while (hDifficultyPoint.base.time > currentSectionEnd) {
            skills.forEach {
                it.saveCurrentPeak()
                it.startNewSectionFrom(currentSectionEnd)
            }
            currentSectionEnd += sectionLength
        }
        skills.forEach { it.process(hDifficultyPoint) }

        prevPrev = Optional.of(prev)
        prev = curr
    }

    skills.forEach { it.saveCurrentPeak() }

    val (aimStrain, aimNoSlidersStrain, speedStrain) = skills.map {
        sqrt(it.difficultyValue(useOutdatedAlgorithm)) * DIFFICULTY_MULTIPLIER
    }

    val baseAimPerformance: Double = (5 * 1.0.coerceAtLeast(aimStrain / 0.0675) - 4).pow(3) / 100000
    val sliderFactor = if (baseAimPerformance > 0) aimNoSlidersStrain / aimStrain else 1.0
    val baseSpeedPerformance: Double = (5 * 1.0.coerceAtLeast(speedStrain / 0.0675) - 4).pow(3) / 100000
    val basePerformance: Double =
        (baseAimPerformance.pow(1.1) + baseSpeedPerformance.pow(1.1)).pow(1 / 1.1)
    val starRating: Double = if (basePerformance > 0.00001)
        Math.cbrt(1.12) * 0.027 * (Math.cbrt(100000 / 2.0.pow(1 / 1.1) * basePerformance) + 4) else 0.0

    return initialAttributes.also {
        it.stars = starRating
        it.aimStrain = aimStrain
        it.sliderFactor = sliderFactor
        it.speedStrain = speedStrain
    }
}

const val stackDistance = 3.0

fun List<OsuStdObject>.stacking(stackThreshold: Double) {
    var extendedStartIndex = 0
    val extendedEndIndex = size - 1

    for (i in (1..extendedEndIndex).reversed()) {
        var n = i

        var objectI = get(i)
        if(objectI.stackHeight != 0.0 || objectI.isSpinner) continue

        if(objectI.isCircle) {
            while (--n >= 0) {
                val objectN = get(n)
                if(objectN.isSpinner) continue

                val endTime = objectN.endTime

                if (objectI.time - endTime > stackThreshold) break

                if (n < extendedStartIndex) {
                    get(n).stackHeight = 0.0
                    extendedStartIndex = n
                }

                if(objectN.isSlider && objectN.endPosition.distance(objectI.position) < stackDistance) {
                    val offset = objectI.stackHeight - objectN.stackHeight + 1

                    for(j in n + 1..i) {
                        val objectJ = get(j)
                        if(objectN.endPosition.distance(objectJ.position) < stackDistance) {
                            get(j).stackHeight -= offset
                        }
                    }
                    break
                }

                if(objectN.position.distance(objectI.position) < stackDistance) {
                    get(n).stackHeight = objectI.stackHeight + 1
                    objectI = objectN
                }
            }
        } else if(objectI.isSlider) {
            while (--n >= 0) {
                val objectN = get(n)
                if(objectN.isSpinner) continue

                if (objectI.time - objectN.time > stackThreshold) break

                if(objectN.endPosition.distance(objectI.position) < stackDistance) {
                    get(n).stackHeight = objectI.stackHeight + 1
                    objectI = objectN
                }
            }
        }
    }
}

fun List<OsuStdObject>.oldStacking(stackThreshold: Double) {
    for (i in 0 until size) {
        if (get(i).stackHeight != 0.0 && !get(i).isSlider) {
            continue
        }

        var startTime = get(i).endTime
        val endPos = get(i).endPosition

        var sliderStack = 0.0

        for (j in i + 1 until size) {
            if (get(j).time - stackThreshold > startTime) {
                break
            }

            if (get(j).position.distance(get(i).position) < STACK_DISTANCE) {
                get(i).stackHeight += 1.0
                startTime = get(j).endTime
            } else if (get(j).position.distance(endPos) < STACK_DISTANCE) {
                sliderStack += 1.0
                get(j).stackHeight -= sliderStack
                startTime = get(j).endTime
            }
        }
    }
}



fun difficultyRange(value: Double, max: Double, avg: Double, min: Double) = if (value > 5.0) {
    avg + (max - avg) * (value - 5.0) / 5.0
} else if (value < 5.0) {
    avg - (avg - min) * (5.0 - value) / 5.0
} else { avg }