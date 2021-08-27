package me.stageguard.obms

import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.processor.beatmap.DifficultyPoint
import me.stageguard.obms.osu.processor.beatmap.SliderState
import me.stageguard.obms.osu.processor.beatmap.TimingPoint
import kotlin.test.assertEquals

suspend fun main() {
    val beatmap = Beatmap.buildBeatmap {
        timingPoints = mutableListOf(
            TimingPoint(time = 1.0, beatLength = 10.0),
            TimingPoint(time = 3.0, beatLength = 20.0),
            TimingPoint(time = 4.0, beatLength = 30.0)
        )
        difficultyPoints = mutableListOf(
            DifficultyPoint(time = 2.0, speedMultiplier = 15.0),
            DifficultyPoint(time = 5.0, speedMultiplier = 45.0)
        )
    }
    val state = SliderState(beatmap)

    state.update(2.0)
    assertEquals(10.0, state.beatLength)

    state.update(3.0)
    assertEquals(20.0, state.beatLength)
    assertEquals(1.0, state.speedMultiply)

    state.update(5.0)
    assertEquals(30.0, state.beatLength)
    assertEquals(45.0, state.speedMultiply)

}