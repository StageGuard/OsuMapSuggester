package me.stageguard.obms.osu.processor.replay

import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.processor.beatmap.HitObject
import me.stageguard.obms.osu.processor.beatmap.HitObjectType
import kotlin.math.abs

class ReplayAnalyzer(
    private val beatmap: Beatmap, replayProcessor: ReplayProcessor
) {
    private val replay = replayProcessor.process(true)
    private val beatmapAttribute = beatmap.attribute.withMod(replay.mods)

    val circleRadius = 54.42 - 4.48 * beatmapAttribute.circleSize
    private val hitTimeWindow = -12 * beatmapAttribute.overallDifficulty + 259.5

    val hits : MutableList<HitFrame> = mutableListOf()
    val attemptedHits : MutableList<HitFrame> = mutableListOf()
    val extraHits: MutableList<ClickFrame> = mutableListOf()
    val missedNotes : MutableList<HitObject> = mutableListOf()
    val effortlessMissedNotes : MutableList<HitObject> = mutableListOf()
    val spinners = beatmap.hitObjects.filter { it.kind is HitObjectType.Spinner }

    init {
        associateHits()
    }

    private fun associateHits() {
        var currentFrameIdx = 0
        if(replay.mods.hr()) replay.flipObjects()

        var hitCount = 0

        beatmap.hitObjects.forEach continuePoint@ { note ->
            var noteHitFlag = false
            var noteAttemptedHitFlag = false

            if(note.isSpinner()) return@continuePoint

            kotlin.run breakPoint@ {
                (currentFrameIdx..replay.replayFrames.size).forEach { j ->
                    val frame = replay.replayFrames[j]
                    val lastKeys = if(j > 0) replay.replayFrames[j - 1].keys else listOf(Key.None)

                    val pressedKey = frame.keys.subtract(lastKeys).toList()

                    if(frame.time - note.time > hitTimeWindow) return@breakPoint

                    if(pressedKey.isNotEmpty() && !pressedKey.contains(Key.None) &&
                        abs(frame.time - note.startTime) <= hitTimeWindow
                    ) {
                        val distance = note.pos.distance(frame.position)
                        when {
                            distance <= circleRadius -> {
                                noteAttemptedHitFlag = true
                                hitCount ++
                                frame.currentHit = hitCount
                                noteHitFlag = true
                                hits.add(HitFrame(frame, note, pressedKey))
                                currentFrameIdx = j + 1
                                return@breakPoint
                            }
                            distance > 150 -> {
                                extraHits.add(ClickFrame(frame, pressedKey))
                            }
                            else -> {
                                noteAttemptedHitFlag = true
                                attemptedHits.add(HitFrame(frame, note, pressedKey))
                            }
                        }
                    }
                    if(pressedKey.isNotEmpty() && !pressedKey.contains(Key.None) &&
                        abs(frame.time - note.startTime) <= 3 * hitTimeWindow &&
                        note.pos.distance(frame.position) <= circleRadius
                    ) {
                        noteAttemptedHitFlag = true
                        attemptedHits.add(HitFrame(frame, note, pressedKey))
                    }
                    frame.currentHit = hitCount
                }
            }
            if(!noteHitFlag) {
                hitCount = 0
                missedNotes.add(note)
            }
            if(!noteAttemptedHitFlag) {
                hitCount = 0
                effortlessMissedNotes.add(note)
            }
        }
    }
}