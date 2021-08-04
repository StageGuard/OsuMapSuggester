package me.stageguard.obms.algorithm.pp

import me.stageguard.obms.algorithm.beatmap.*
import me.stageguard.obms.algorithm.pp.skill.AimSkill
import me.stageguard.obms.algorithm.pp.skill.SpeedSkill
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
const val NORMALIZED_RADIUS = 52.0
const val DIFFICULTY_MULTIPLIER = 0.0675
const val STACK_DISTANCE = 3.0

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
        overallDifficulty = od, speedStrain = 0.0, aimStrain = 0.0,
        maxCombo = 0, nCircles = 0, nSpinners = 0
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

    val aim = AimSkill(mods)
    val speed = SpeedSkill(mods)

    var currentSectionEnd = ceil(hitObjects[0].startTime / sectionLength) * sectionLength
    var prevPrev: Optional<OsuStdObject> = Optional.empty()
    var prev = hitObjectMappedIterator.next()
    var prevVals: Optional<Pair<Double, Double>> = Optional.empty()

    var curr = hitObjectMappedIterator.next()
    var hDifficultyPoint = DifficultyObject(
        base = curr,
        prev = prev,
        prevVals = prevVals,
        prevPrev = prevPrev,
        clockRate = mapAttributesWithMod.clockRate,
        scalingFactor = scalingFactor
    )

    while (hDifficultyPoint.base.time > currentSectionEnd) {
        currentSectionEnd += sectionLength
    }

    aim.process(hDifficultyPoint)
    speed.process(hDifficultyPoint)

    prevPrev = Optional.of(prev)
    prevVals = Optional.of(hDifficultyPoint.run { jumpDist to strainTime })
    prev = curr

    while (hitObjectMappedIterator.hasNext()) {
        curr = hitObjectMappedIterator.next()
        hDifficultyPoint = DifficultyObject(
            base = curr,
            prev = prev,
            prevVals = prevVals,
            prevPrev = prevPrev,
            clockRate = mapAttributesWithMod.clockRate,
            scalingFactor = scalingFactor
        )
        while (hDifficultyPoint.base.time > currentSectionEnd) {
            aim.saveCurrentPeak()
            aim.startNewSectionFrom(currentSectionEnd)
            speed.saveCurrentPeak()
            speed.startNewSectionFrom(currentSectionEnd)
            currentSectionEnd += sectionLength
        }
        aim.process(hDifficultyPoint)
        speed.process(hDifficultyPoint)

        prevPrev = Optional.of(prev)
        prevVals = Optional.of(hDifficultyPoint.run { jumpDist to strainTime })
        prev = curr
    }

    aim.saveCurrentPeak()
    speed.saveCurrentPeak()

    val aimStrain = sqrt(aim.difficultyValue(useOutdatedAlgorithm)) * DIFFICULTY_MULTIPLIER
    val speedStrain = sqrt(speed.difficultyValue(useOutdatedAlgorithm)) * DIFFICULTY_MULTIPLIER

    return initialAttributes.also {
        it.nCircles = this.nCircles
        it.nSpinners = this.nSpinners
        it.stars = aimStrain + speedStrain + abs(aimStrain - speedStrain) / 2.0
        it.speedStrain = speedStrain
        it.aimStrain = aimStrain
    }
}

fun List<OsuStdObject>.stacking(stackThreshold: Double) {
    var extendedStartIndex = 0
    val extendedEndIndex = size - 1


    for (i in (1..extendedEndIndex).reversed()) {
        var n = i

        if (get(i).stackHeight != 0.0 || !get(i).isSlider) {
            continue
        }

        if (get(i).isCircle) {
            var outerIndex = i
            kotlin.run breakRepeat@ {
                repeat(Int.MAX_VALUE) {
                    n = if(n - 1 < 0) return@breakRepeat else n - 1

                    if (get(n).isSpinner) {
                        return@breakRepeat
                    } else if (n < extendedStartIndex) {
                        get(n).stackHeight = 0.0
                        extendedStartIndex = n
                    }

                    if (get(n).isSlider && get(n).endPosition.distance(get(outerIndex).position) < STACK_DISTANCE) {
                        val offset = get(outerIndex).stackHeight - get(n).stackHeight + 1.0

                        for (j in n + 1 until outerIndex) {
                            if (get(n).position.distance(get(j).position) < STACK_DISTANCE) {
                                get(j).stackHeight -= offset
                            }
                        }
                        return@breakRepeat
                    } else if (get(n).position.distance(get(outerIndex).position) < STACK_DISTANCE) {
                        get(n).stackHeight = get(outerIndex).stackHeight + 1.0
                        outerIndex = n
                    }
                }
            }
        } else if (get(i).isSlider) {
            var outerIndex = i
            kotlin.run breakRepeat@ {
                repeat(Int.MAX_VALUE) continueRepeat@ {
                    n = if(n - 1 < 0) return@breakRepeat else n - 1

                    if (get(n).isSpinner) {
                        return@continueRepeat
                    } else if (get(outerIndex).time - get(n).time > stackThreshold) {
                        return@breakRepeat
                    } else if (get(n).endPosition.distance(get(outerIndex).position) < STACK_DISTANCE) {
                        get(n).stackHeight = get(outerIndex).stackHeight + 1.0
                        outerIndex = n
                    }
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