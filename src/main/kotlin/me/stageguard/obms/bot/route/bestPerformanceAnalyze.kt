package me.stageguard.obms.bot.route

import me.stageguard.obms.api.osu.OsuWebApi
import me.stageguard.obms.bot.MessageRoute.atReply
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder

fun GroupMessageSubscribersBuilder.bestPerformanceAnalyze() {
    startsWith("./bpa") {
        val bpScoresResult = OsuWebApi.userScore(user = sender.id, type = "best", limit = 10)
        if(bpScoresResult.isSuccess) {
            val bpScores = bpScoresResult.getOrNull()!!
            val bpString = bpScores.joinToString("\n") {
                buildString {
                    append(it.beatmapset?.title ?: "Unknown Title")
                    append("[${it.beatmap?.version ?: "Unknown Version"}]")
                    append("(${it.beatmap?.difficultyRating ?: "?.??"} ⭐)")
                    append(" → ")
                    append("${it.pp}PP")
                }
            }
            atReply("Your 10 best performance: \n$bpString")
        } else {
            atReply("Cannot analyze your best performance scores: ${bpScoresResult.exceptionOrNull()}")
        }
    }
}