package me.stageguard.obms.bot.route

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.newSingleThreadContext
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
import net.mamoe.mirai.message.data.toMessageChain
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
            val isRecalculated: Boolean,
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

@OptIn(ObsoleteCoroutinesApi::class)
suspend fun orderScores(
    scores: Pair<List<ScoreDTO>, List<ScoreDTO>?>,
    analyzeDetail: Boolean = false,
    rangeToRecalculate: IntRange = 0..25,
) : Either<OrderResult, IllegalStateException> = scores.second.let { secList ->
    if(secList == null) {
        if(!analyzeDetail) {
            Either.Left(OrderResult(scores.first.mapIndexed { i, it -> OrderResult.Entry.Default(i, it) }))
        } else {
            val atomicInt = atomic(0)
            val calculatedList = mutableListOf<OrderResult.Entry.DetailAnalyze>()
            scores.first.asFlow().flowOn(newSingleThreadContext("PPCalculationFlow")).map { score ->
                val idx = atomicInt.getAndIncrement()
                if(idx in rangeToRecalculate) {
                    when(val beatmap = BeatmapPool.getBeatmap(score.beatmap!!.id)) {
                        is Either.Left -> {
                            val recalculatedPp = PPCalculator.of(beatmap.value)
                                .accuracy(score.accuracy * 100.0)
                                .passedObjects(score.statistics.count100 + score.statistics.count300 + score.statistics.count50)
                                .mods(score.mods.map {
                                    when(it) {
                                        "EZ" -> Mod.Easy
                                        "NF" -> Mod.NoFail
                                        "HT" -> Mod.HalfTime
                                        "HR" -> Mod.HardRock
                                        "SD" -> Mod.SuddenDeath
                                        "DT", "NC" -> Mod.DoubleTime
                                        "HD" -> Mod.Hidden
                                        "FL" -> Mod.Flashlight
                                        "SO" -> Mod.SpunOut
                                        else -> Mod.None //scorev2 cannot appears in best performance
                                    }
                                }.ifEmpty { listOf(Mod.None) }).calculate()

                            OrderResult.Entry.DetailAnalyze(
                                rankChange = 0,
                                drawLine = idx, // now drawLine is as original rank
                                recalculatedPp = recalculatedPp.total,
                                recalculatedWeightedPp = 0.0,
                                isRecalculated = true,
                                score = score
                            )
                        }
                        is Either.Right -> {
                            throw IllegalStateException("CALCULATE_ERROR:${beatmap.value}")
                        }
                    }
                } else {
                    OrderResult.Entry.DetailAnalyze(
                        rankChange = 0,
                        drawLine = idx, // now drawLine is as original rank
                        recalculatedPp = score.pp,
                        recalculatedWeightedPp = 0.0,
                        isRecalculated = false,
                        score = score
                    )
                }
            }.toCollection(calculatedList)
            calculatedList.asSequence().sortedByDescending {
                it.recalculatedPp
            }.mapIndexed { idx, it ->
                OrderResult.Entry.DetailAnalyze(
                    rankChange = it.drawLine - idx,
                    drawLine = idx, // now drawLine is as original rank
                    recalculatedPp = it.recalculatedPp,
                    recalculatedWeightedPp = it.recalculatedPp * 0.95.pow(idx),
                    isRecalculated = it.isRecalculated,
                    score = it.score
                )
            }.filterIndexed { _, it -> it.isRecalculated }.mapIndexed { idx, it ->
                OrderResult.Entry.DetailAnalyze(
                    rankChange = it.rankChange,
                    drawLine = idx, // now drawLine is as original rank
                    recalculatedPp = it.recalculatedPp,
                    recalculatedWeightedPp = it.recalculatedWeightedPp,
                    score = it.score,
                    isRecalculated = it.isRecalculated,
                )
            }.toList().run {
                Either.Left(OrderResult(this))
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
        Either.Left(OrderResult(resultList))
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
    atReply(image.toMessageChain())
}

@Suppress("SpellCheckingInspection")
fun GroupMessageSubscribersBuilder.bestPerformanceAnalyze() {
    startsWith(".bpvs") {
        //parse message
        var (limit, offset) = 25 to 0
        val target: At = message.filterIsInstance<At>().run {
            if(isEmpty()) {
                atReply("请指定一个玩家来对比。")
                return@startsWith
            } else first()
        }

        if(target.target == sender.id) {
            atReply("无法与你自己对比！")
            return@startsWith
        }

        regex.matchEntire(
            message.filterIsInstance<PlainText>().joinToString("").substringAfter(".bpvs").trim()
        )?.groupValues?.run {
            limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
            offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
        }

        when(val orderResult = orderScores(
            when(val myBpScores = OsuWebApi.userScore(user = sender.id, type = "best", limit = limit, offset = offset)) {
                is Either.Left -> myBpScores.value
                is Either.Right -> {
                    atReply("从服务器获取你的 Best Performance 信息时发生了异常: ${myBpScores.value}")
                    return@startsWith
                }
            } to when(val myBpScores = OsuWebApi.userScore(user = target.target, type = "best", limit = limit, offset = offset)) {
                is Either.Left -> myBpScores.value
                is Either.Right -> {
                    atReply("从服务器获取对方的 Best Performance 信息时发生了异常: ${myBpScores.value}")
                    return@startsWith
                }
            }
        )) {
            is Either.Left -> processData(orderResult.value)
            is Either.Right -> atReply("处理数据时发生了异常: ${orderResult.value}")
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

        regex.matchEntire(msg)?.groupValues?.run {
            limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
            offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
        }

        if(analyzeDetail) atReply("正在重新计算你 Best Performance 中 ${offset + 1} 到 ${offset + limit} 的成绩...")

        when(val orderResult = orderScores(
            when(val myBpScores = OsuWebApi.userScore(
                user = sender.id, type = "best",
                limit = if(analyzeDetail) 100 else limit, offset = if(analyzeDetail) 0 else offset
            )) {
                is Either.Left -> myBpScores.value
                is Either.Right -> {
                    atReply("从服务器获取你的 Best Performance 信息时发生了异常: ${myBpScores.value}")
                    return@startsWith
                }
            } to null, analyzeDetail, offset..(limit + offset))
        ) {
            is Either.Left -> processData(orderResult.value)
            is Either.Right -> atReply("处理数据时发生了异常: ${orderResult.value}")
        }
    }
}