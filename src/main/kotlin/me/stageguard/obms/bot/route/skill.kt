package me.stageguard.obms.bot.route

import me.stageguard.obms.osu.algorithm.`pp+`.PPPlusCalculator
import me.stageguard.obms.osu.algorithm.`pp+`.PPPlusResult
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.cache.BeatmapPool
import me.stageguard.obms.utils.Either
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import kotlin.math.pow

fun GroupMessageSubscribersBuilder.skill() {
    startsWith(".skill") {
        val scores =  when(val myBpScores = OsuWebApi.userScore(user = sender.id, type = "best", limit = 100)) {
            is Either.Left -> myBpScores.value
            is Either.Right -> {
                atReply("从服务器获取你的 Best Performance 信息时发生了异常: ${parseExceptions(myBpScores.value)}")
                return@startsWith
            }
        }
        val result = scores.map { score ->
            val beatmap = when(val beatmap = BeatmapPool.getBeatmap(score.beatmap!!.id)) {
                is Either.Left -> beatmap.value
                is Either.Right -> {
                    atReply("解析 Beatmap ${score.beatmap.id} 时发生了异常: ${parseExceptions(beatmap.value)}")
                    return@startsWith
                }
            }
            PPPlusCalculator.of(beatmap)
                .accuracy(score.accuracy * 100.0)
                .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                .mods(score.mods.parseMods())
                .combo(score.maxCombo)
                .n300(score.statistics.count300)
                .n100(score.statistics.count100)
                .n50(score.statistics.count50)
                .misses(score.statistics.countMiss)
                .calculate()

        }.foldIndexed(
            PPPlusResult(
            total = 0.0, aim = 0.0, jumpAim = 0.0, flowAim = 0.0,
            speed = 0.0, accuracy = 0.0, stamina = 0.0, precision = 0.0
        )
        ) { idx, last, cur ->
            PPPlusResult(
                total = last.total + cur.total * 0.95.pow(idx),
                aim = last.aim + cur.aim * 0.95.pow(idx),
                jumpAim = last.jumpAim + cur.jumpAim * 0.95.pow(idx),
                flowAim = last.flowAim + cur.flowAim * 0.95.pow(idx),
                speed = last.speed + cur.speed * 0.95.pow(idx),
                accuracy = last.accuracy + cur.accuracy * 0.95.pow(idx),
                stamina = last.stamina + cur.stamina * 0.95.pow(idx),
                precision = last.precision + cur.precision * 0.95.pow(idx)
            )
        }
        atReply("""
            
            Aim         ${result.aim}
            Aim(Jump)   ${result.jumpAim}
            Aim(Flow)   ${result.flowAim}
            Speed       ${result.speed}
            Accuracy    ${result.accuracy}
            Stamina     ${result.stamina}
            Precision   ${result.precision}
        """.trimIndent())
    }
}