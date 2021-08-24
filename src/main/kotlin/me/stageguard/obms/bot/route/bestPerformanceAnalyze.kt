package me.stageguard.obms.bot.route

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runInterruptible
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.algorithm.pp.PPCalculator
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.BestPerformanceDetail
import me.stageguard.obms.utils.InferredEitherOrISE
import me.stageguard.obms.utils.ValueOrISE
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.toMessageChain
import me.stageguard.obms.utils.Either.Companion.leftOrNull
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.Either.Companion.right
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
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
            val analyzeDetailType: AnalyzeDetailType,
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

enum class AnalyzeDetailType {
    IfFullCombo,
    OutdatedAlgorithm
}

@OptIn(ObsoleteCoroutinesApi::class)
suspend fun orderScores(
    scores: Pair<List<ScoreDTO>, List<ScoreDTO>?>,
    analyzeDetail: Boolean = false,
    analyzeType: AnalyzeDetailType = AnalyzeDetailType.IfFullCombo,
    rangeToAnalyze: IntRange = 0..25
) : ValueOrISE<OrderResult> = scores.second.let { secList ->
    if(secList == null) {
        if(!analyzeDetail) {
            InferredEitherOrISE(OrderResult(scores.first.mapIndexed { i, it -> OrderResult.Entry.Default(i, it) }))
        } else {
            val atomicInt = atomic(0)
            val calculatedList = mutableListOf<OrderResult.Entry.DetailAnalyze>()
            scores.first.asFlow().flowOn(newSingleThreadContext("PPCalculationFlow")).map { score ->
                val idx = atomicInt.getAndIncrement()
                if(idx in rangeToAnalyze) {
                    val beatmap = BeatmapCache.getBeatmap(score.beatmap!!.id)
                    beatmap.rightOrNull?.run {
                        val recalculatedPp = PPCalculator.of(beatmap.right)
                            .accuracy(score.accuracy * 100.0)
                            .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                            .mods(score.mods.parseMods()).run {
                                when(analyzeType) {
                                    AnalyzeDetailType.IfFullCombo -> this
                                    AnalyzeDetailType.OutdatedAlgorithm -> {
                                        outdatedAlgorithm()
                                            .misses(score.statistics.countMiss)
                                            .combo(score.maxCombo)
                                            .n100(score.statistics.count100)
                                            .n50(score.statistics.count50)
                                    }
                                }
                            }.calculate()
                        return@map OrderResult.Entry.DetailAnalyze(
                            analyzeDetailType = analyzeType,
                            rankChange = 0,
                            drawLine = idx, // now drawLine is as original rank
                            recalculatedPp = recalculatedPp.total,
                            recalculatedWeightedPp = 0.0,
                            isRecalculated = true,
                            score = score
                        )
                    } ?: throw throw IllegalStateException("CALCULATE_ERROR:${beatmap.leftOrNull}")
                } else { // not in recalculate range, will be filtered later
                    OrderResult.Entry.DetailAnalyze(
                        analyzeDetailType = analyzeType,
                        rankChange = 0,
                        drawLine = idx, // now drawLine is as original rank
                        recalculatedPp = score.pp!!,
                        recalculatedWeightedPp = 0.0,
                        isRecalculated = false,
                        score = score
                    )
                }
            }.toList(calculatedList)
            calculatedList.asSequence().sortedByDescending {
                it.recalculatedPp
            }.mapIndexed { idx, it ->
                OrderResult.Entry.DetailAnalyze(
                    analyzeDetailType = analyzeType,
                    rankChange = it.drawLine - idx,
                    drawLine = idx, // now drawLine is as original rank
                    recalculatedPp = it.recalculatedPp,
                    recalculatedWeightedPp = it.recalculatedPp * 0.95.pow(idx),
                    isRecalculated = it.isRecalculated,
                    score = it.score
                )
            }.filter { it.isRecalculated }.mapIndexed { idx, it ->
                OrderResult.Entry.DetailAnalyze(
                    analyzeDetailType = analyzeType,
                    rankChange = it.rankChange,
                    drawLine = idx, // now drawLine is as original rank
                    recalculatedPp = it.recalculatedPp,
                    recalculatedWeightedPp = it.recalculatedWeightedPp,
                    score = it.score,
                    isRecalculated = it.isRecalculated,
                )
            }.toList().run {
                InferredEitherOrISE(OrderResult(this))
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
        var currentBottomPP = combined.first().pp!!
        resultList.add(OrderResult.Entry.Versus(leftUserId == currentId, currentRow, combined.first()))
        combined.drop(1).forEach {
            when {
                it.pp!! + diffInOneLine < currentBottomPP -> {
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
        InferredEitherOrISE(OrderResult(resultList))
    }
}

suspend fun GroupMessageEvent.processOrderResultAndSend(orderResult: OrderResult) {
    val surfaceOutput = BestPerformanceDetail.drawBestPerformancesImage(orderResult)
    val bytes = surfaceOutput.bytes(EncodedImageFormat.PNG)
    val externalResource = bytes.toExternalResource("png")
    val image = group.uploadImage(externalResource)
    runInterruptible { externalResource.close() }
    atReply(image.toMessageChain())
}

fun List<String>.parseMods() = map {
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
}.ifEmpty { listOf(Mod.None) }

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

        val myBpScores = OsuWebApi.userScore(user = sender.id, type = "best", limit = limit, offset = offset)
        val targetBpScores = OsuWebApi.userScore(user = target.target, type = "best", limit = limit, offset = offset)

        myBpScores.onRight { my ->
            targetBpScores.onRight { target ->
                orderScores(my to target).onRight {
                    processOrderResultAndSend(it)
                }.onLeft {
                    atReply("处理数据时发生了异常: ${parseExceptions(it)}")
                }
            }.onLeft {
                atReply("从服务器获取对方的 Best Performance 信息时发生了异常: ${parseExceptions(it)}")
            }
        }.onLeft {
            atReply("从服务器获取你的 Best Performance 信息时发生了异常: ${parseExceptions(it)}")
        }
    }

    startsWith(".bpa") {
        //parse message
        var (limit, offset) = 25 to 0

        var msg = message.filterIsInstance<PlainText>().joinToString("").substringAfter(".bpa").trim()
        var analyzeDetail = false
        var analyzeDetailType = AnalyzeDetailType.IfFullCombo

        if (msg.startsWith("fc")) {
            msg = msg.substringAfter("fc").trim()
            analyzeDetailType = AnalyzeDetailType.IfFullCombo
            analyzeDetail = true
        } else if (msg.startsWith("old")) {
            msg = msg.substringAfter("old").trim()
            analyzeDetailType = AnalyzeDetailType.OutdatedAlgorithm
            analyzeDetail = true
        }

        regex.matchEntire(msg)?.groupValues?.run {
            limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
            offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
        }

        if(analyzeDetail) {
            if(analyzeDetailType == AnalyzeDetailType.IfFullCombo) {
                atReply("正在以 Full Combo 重新计算你 Best Performance 中 ${offset + 1} 到 ${offset + limit} 的成绩...")
            } else if(analyzeDetailType == AnalyzeDetailType.OutdatedAlgorithm) {
                atReply("正在计算 Best Performance 中 ${offset + 1} 到 ${offset + limit} 的 PP 削减...")
            }
        }

        OsuWebApi.userScore(
            user = sender.id, type = "best",
            limit = if(analyzeDetail) 100 else limit, offset = if(analyzeDetail) 0 else offset
        ).onRight { list ->
            orderScores(
                list to null, analyzeDetail,
                analyzeDetailType, offset..(limit + offset)
            ).onRight {
                processOrderResultAndSend(it)
            }.onLeft {
                atReply("处理数据时发生了异常: ${parseExceptions(it)}")
            }
        }.onLeft {
            atReply("从服务器获取你的 Best Performance 信息时发生了异常: ${parseExceptions(it)}")
        }
    }
}