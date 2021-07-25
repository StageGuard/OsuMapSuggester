package me.stageguard.obms.bot.route

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.algorithm.beatmap.Mod
import me.stageguard.obms.algorithm.pp.PPCalculator
import me.stageguard.obms.api.osu.OsuWebApi
import me.stageguard.obms.api.osu.dto.ScoreDTO
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.cache.BeatmapPool
import me.stageguard.obms.graph.item.drawBestPerformancesImage
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.export
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.pow

val regex = Regex("(\\d+)[\\-_→](\\d+)")

const val diffInOneLine = 3.0

data class OrderResult(
    val scores: List<Entry>
) {
    sealed class Entry(
        open val score: ScoreDTO,
        open val drawLine: Int
    ) {
        class DetailAnalyze(
            var rankChange: Int,
            drawLine: Int,
            val recalculatedPp: Double,
            val recalculatedWeightedPp: Double,
            score: ScoreDTO
        ) : Entry(score, drawLine)
        class Default(
            drawLine: Int, score: ScoreDTO
        ) : Entry(score, drawLine)
        class Versus(
            val isLeft: Boolean, drawLine: Int, score: ScoreDTO
        ) : Entry(score, drawLine)
    }
}

suspend fun orderScores(
    scores: Pair<List<ScoreDTO>, List<ScoreDTO>?>,
    analyzeDetail: Boolean = false
) : Result<OrderResult> = scores.second.let { secList ->
    if(secList == null) {
        if(!analyzeDetail) {
            Result.success(OrderResult(scores.first.mapIndexed { i, it -> OrderResult.Entry.Default(i, it) }))
        } else {
            scores.first.mapIndexed { idx, score ->
                val beatmap = BeatmapPool.getBeatmap(score.beatmap!!.id)

                val recalculatedPp = PPCalculator.of(beatmap.getOrElse {
                    return@let Result.failure(IllegalStateException("CALCULATE_ERROR:${beatmap.exceptionOrNull()}"))
                })
                    .accuracy(score.accuracy * 100.0)
                    .passedObjects(score.statistics.count100 + score.statistics.count300 + score.statistics.count50)
                    .mods(score.mods.map {
                    when(it) {
                        "EZ" -> Mod.Easy
                        "NF" -> Mod.NoFail
                        "HT" -> Mod.HalfTime
                        "HR" -> Mod.HardRock
                        "SD" -> Mod.SuddenDeath
                        "DT" -> Mod.DoubleTime
                        "HD" -> Mod.Hidden
                        "FL" -> Mod.Flashlight
                        else -> Mod.None //scorev2 cannot appears in best performance
                    }
                }.ifEmpty { listOf(Mod.None) }).calculate()

                OrderResult.Entry.DetailAnalyze(
                    rankChange = 0,
                    drawLine = idx, // now drawLine is as original rank
                    recalculatedPp = recalculatedPp.total,
                    recalculatedWeightedPp = 0.0,
                    score = score
                )
            }.sortedByDescending { it.recalculatedPp }.mapIndexed { idx, it ->
                OrderResult.Entry.DetailAnalyze(
                    rankChange = it.drawLine - idx,
                    drawLine = idx, // now drawLine is as original rank
                    recalculatedPp = it.recalculatedPp,
                    recalculatedWeightedPp = it.recalculatedPp * 0.95.pow(idx),
                    score = it.score
                )
            }.run {
                Result.success(OrderResult(this))
            }
        }
    } else {
        val resultList = mutableListOf<OrderResult.Entry.Versus>()
        val combined = mutableListOf<ScoreDTO>().also {
            it.addAll(scores.first)
            it.addAll(secList)
        }.toList().sortedByDescending { it.pp }
        val leftUserId = scores.first.first().userId
        var currentRowItemCount = 1
        var currentRow = 0
        var currentId = combined.first().userId
        var currentBottomPP = combined.first().pp
        resultList.add(OrderResult.Entry.Versus(leftUserId == currentId, currentRow, combined.first()))
        combined.drop(1).forEach {
            when {
                it.pp + diffInOneLine < currentBottomPP -> {
                    currentRow ++
                    currentRowItemCount = 1
                }
                currentId == it.userId -> {
                    currentRow ++
                    currentRowItemCount = 1
                }
                currentRowItemCount == 2 -> {
                    currentRow ++
                    currentRowItemCount = 1
                }
                else -> {
                    currentRowItemCount ++
                }
            }
            resultList.add(OrderResult.Entry.Versus(leftUserId == it.userId, currentRow, it))
            currentId = it.userId
            currentBottomPP = it.pp
        }
        Result.success(OrderResult(resultList))
    }
}

suspend fun GroupMessageEvent.processData(orderResult: OrderResult) {
    val output = drawBestPerformancesImage(orderResult)
    val outputFile = OsuMapSuggester.dataFolder.absolutePath + "${File.separator}img${File.separator}${
        LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toString() + (100..999).random().toString() + sender.id.toString()
    }.png"
    output.export(outputFile, EncodedImageFormat.PNG)
    val externalResource = File(outputFile).toExternalResource("png")
    val image = group.uploadImage(externalResource)
    externalResource.close()
    group.sendMessage(image)
}

@Suppress("SpellCheckingInspection")
fun GroupMessageSubscribersBuilder.bestPerformanceAnalyze() {
    startsWith(".bpvs") {
        //parse message
        var (limit, offset) = 25 to 0
        val target: At = message.filterIsInstance<At>().run {
            if(isEmpty()) {
                atReply("Please specify a target player to compare.")
                return@startsWith
            } else first()
        }

        if(target.target == sender.id) {
            atReply("Cannot compare with yourself!")
            return@startsWith
        }

        regex.matchEntire(
            message.filterIsInstance<PlainText>().joinToString("").substringAfter(".bpvs").trim()
        )?.groupValues?.run {
            limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
            offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
        }

        val orderResult = orderScores(
            when(val myBpScores = OsuWebApi.userScore(user = sender.id, type = "best", limit = limit, offset = offset)) {
                is Either.Left -> myBpScores.value
                is Either.Right -> {
                    atReply("Cannot fetch your best performance scores: ${myBpScores.value}")
                    return@startsWith
                }
            } to when(val myBpScores = OsuWebApi.userScore(user = target.target, type = "best", limit = limit, offset = offset)) {
                is Either.Left -> myBpScores.value
                is Either.Right -> {
                    atReply("Cannot fetch target's best performance scores: ${myBpScores.value}")
                    return@startsWith
                }
            }
        )
        orderResult.onSuccess {
            processData(it)
        }.onFailure {
            atReply("Cannot process score data: $it")
        }
    }

    startsWith(".bpa") {
        //parse message
        var (limit, offset) = 25 to 0

        var msg = message.filterIsInstance<PlainText>().joinToString("").substringAfter(".bpa").trim()
        val analyzeDetail = if (msg.startsWith("fc")) {
            msg = msg.substringAfter("fc").trim()
            true
        } else false

        if(analyzeDetail) atReply("Recalculating all performance point of your best perforamnces, this my take a while...")

        regex.matchEntire(msg)?.groupValues?.run {
            limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
            offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
        }

        val orderResult = orderScores(
            when(val myBpScores = OsuWebApi.userScore(user = sender.id, type = "best", limit = limit, offset = offset)) {
                is Either.Left -> myBpScores.value
                is Either.Right -> {
                    atReply("Cannot fetch your best performance scores: ${myBpScores.value}")
                    return@startsWith
                }
            } to null, analyzeDetail)
        orderResult.onSuccess {
            processData(it)
        }.onFailure {
            atReply("Cannot process score data: $it")
        }
    }
}