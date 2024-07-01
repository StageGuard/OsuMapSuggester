package me.stageguard.obms.bot.route

import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.enums.MsgTypeEnum
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.MessageRoute.routeLock
import me.stageguard.obms.bot.calculatorProcessorDispatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.cache.BeatmapCache
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.BestPerformanceDetailDraw
import me.stageguard.obms.osu.algorithm.ppnative.PPCalculatorNative
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import me.stageguard.obms.utils.Either.Companion.leftOrNull
import me.stageguard.obms.utils.Either.Companion.right
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import io.github.humbleui.skija.EncodedImageFormat
import jakarta.annotation.Resource
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.bot.MessageRoute.atReplyText
import me.stageguard.obms.bot.refactoredExceptionCatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Base64
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

@Shiro
@Component
open class BestPerformance {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Resource
    private lateinit var osuWebApi: OsuWebApi
    @Resource
    private lateinit var beatmapCache: BeatmapCache

    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".bpvs", "。bpvs"])
    fun versus(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch(refactoredExceptionCatcher) {
            logger.info("Processing best performance versus data of ${sender.userId}.")
            val currentTimestamp = System.currentTimeMillis()
            //parse message
            var (limit, offset) = 25 to 0

            val target: Long? = arrayMsg.filter { it.type == MsgTypeEnum.at }.run {
                if(isEmpty()) {
                    atReplyText("请指定一个玩家来对比。")
                    return@launch
                } else first()
            }.data["user_id"]?.toLong()

            if (target == null) {
                atReplyText("无法解析被对比玩家信息")
                return@launch
            }
            if(target == sender.userId) {
                atReplyText("无法与你自己对比！")
                return@launch
            }

            regex.matchEntire(
                arrayMsg.filter { it.type == MsgTypeEnum.text }.joinToString("").substringAfter("bpvs").trim()
            )?.groupValues?.run {
                limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
                offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
            }

            val myBpScores = osuWebApi.userScore(user = sender.userId, type = "best", limit = limit, offset = offset).rightOrThrowLeft()
            val targetBpScores = osuWebApi.userScore(user = target, type = "best", limit = limit, offset = offset).rightOrThrowLeft()

            val timeDiff = System.currentTimeMillis() - currentTimestamp
            logger.info("Finished processing best performance versus data data of ${sender.userId}, took $timeDiff milliseconds.")
            processOrderResultAndSend(orderScores(myBpScores to targetBpScores).rightOrThrowLeft())
        }
    }

    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".bpa", "。bpa"])
    fun analyze(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch(refactoredExceptionCatcher) {
            logger.info("Processing best performance analytics data of ${sender.userId}.")
            val currentTimestamp = System.currentTimeMillis()
            //parse message
            var (limit, offset) = 25 to 0

            var msg = arrayMsg.filter { it.type == MsgTypeEnum.text }.joinToString("").substringAfter("bpvs").trim()
            var analyzeDetail = false
            var analyzeDetailType = AnalyzeDetailType.IfFullCombo

            if (msg.startsWith("fc")) {
                msg = msg.substringAfter("fc").trim()
                analyzeDetail = true
            }

            regex.matchEntire(msg)?.groupValues?.run {
                limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
                offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
            }

            if(analyzeDetail) {
                atReplyText("正在以 Full Combo 重新计算你 Best Performance 中 ${offset + 1} 到 ${offset + limit} 的成绩...")
            }

            val list = osuWebApi.userScore(
                user = sender.userId, type = "best",
                limit = if(analyzeDetail) 100 else limit, offset = if(analyzeDetail) 0 else offset
            ).rightOrThrowLeft()

            val ordered = orderScores(
                list to null, analyzeDetail,
                analyzeDetailType, offset..limit + offset
            ).rightOrThrowLeft()

            val timeDiff = System.currentTimeMillis() - currentTimestamp
            logger.info("Finished processing best performance analytics data of ${sender.userId}, took $timeDiff milliseconds.")
            processOrderResultAndSend(ordered)
        }
    }



    @OptIn(ObsoleteCoroutinesApi::class)
    suspend fun orderScores(
        scores: Pair<List<ScoreDTO>, List<ScoreDTO>?>,
        analyzeDetail: Boolean = false,
        analyzeType: AnalyzeDetailType = AnalyzeDetailType.IfFullCombo,
        rangeToAnalyze: IntRange = 0..25
    ) : OptionalValue<OrderResult> {
        logger.info("Ordering scores...")
        val secList = scores.second
        return if(secList == null) {
            if(!analyzeDetail) {
                InferredOptionalValue(OrderResult(scores.first.mapIndexed { i, it -> OrderResult.Entry.Default(i, it) }))
            } else withContext(calculatorProcessorDispatcher) {
                val atomicInt = atomic(0)
                val calculatedList = mutableListOf<OrderResult.Entry.DetailAnalyze>()
                scores.first.asFlow().map { score ->
                    val idx = atomicInt.getAndIncrement()
                    if(idx in rangeToAnalyze) {
                        val beatmap = beatmapCache.getBeatmapFile(score.beatmap!!.id)
                        beatmap.rightOrNull?.run {
                            val recalculatedPp = PPCalculatorNative.of(beatmap.right.absolutePath)
                                .accuracy(score.accuracy * 100.0)
                                .passedObjects(score.statistics.count300 + score.statistics.count100 + score.statistics.count50)
                                .mods(score.mods.parseMods()).run {
                                    when(analyzeType) {
                                        AnalyzeDetailType.IfFullCombo -> this
                                        AnalyzeDetailType.OutdatedAlgorithm -> {
                                            this.misses(score.statistics.countMiss)
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
                    InferredOptionalValue(OrderResult(this))
                }
            }
        } else withContext(calculatorProcessorDispatcher) {
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
            InferredOptionalValue(OrderResult(resultList))
        }
    }

    suspend fun GroupMessageEvent.processOrderResultAndSend(orderResult: OrderResult) {
        logger.info("Generating image BestPerformancePanel of ${sender.userId}.")
        val currTimestamp = System.currentTimeMillis()
        val bytes = withContext(Dispatchers.IO) {
            BestPerformanceDetailDraw.drawBestPerformancesImage(orderResult).bytes(EncodedImageFormat.PNG)
        }
        val timeDiff = System.currentTimeMillis() - currTimestamp
        logger.info("Finished generating image BestPerformancePanel of ${sender.userId}, took $timeDiff milliseconds.")

        val imageBase64String = Base64.getEncoder().encodeToString(bytes)
        atReply { img("base64://$imageBase64String") }
    }
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