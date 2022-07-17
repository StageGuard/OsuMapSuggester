package me.stageguard.obms.bot.route

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.bot.graphicProcessorDispatcher
import me.stageguard.obms.bot.route.SuggestedBeatmapCache.SUGBMPInfo
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.*
import me.stageguard.obms.frontend.route.IMPORT_BEATMAP_PATH
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.MapSuggester
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.script.ScriptEnvironHost
import me.stageguard.obms.script.ScriptTimeoutException
import me.stageguard.obms.script.synthetic.wrapped.ConvenientToolsForBeatmapSkill
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringBooleanWrapped
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringComparableNumberWrapped
import me.stageguard.obms.utils.Either.Companion.ifRight
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.sourceIds
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import io.github.humbleui.skija.EncodedImageFormat
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.entity.*
import org.mozilla.javascript.EcmaError
import kotlin.random.Random

typealias Wrapper<T> = ColumnDeclaringComparableNumberWrapped<T>

fun getRandomWithWeight(weightList: List<Int>) : Int {
    val sum = weightList.sum()
    val possibility = weightList.map { it * 1.0 / sum }

    var random = Random.Default.nextDouble()

    possibility.forEachIndexed { idx, it ->
        random -= it
        if(random <= 0) return idx
    }
    return possibility.lastIndex
}

object SuggestedBeatmapCache {
    val cache : HashMap<Int, SUGBMPInfo> = hashMapOf()

    class SUGBMPInfo(val groupId: Long, val rulesetId: Int, val bid: Int)
}

val beatmapMatcherPattern = Regex("[来|搞]一?[张|点](.+)(?:[谱|铺]面?|图)", RegexOption.IGNORE_CASE)
fun GroupMessageSubscribersBuilder.suggesterTrigger() {
    routeLock(finding(beatmapMatcherPattern)) { content ->
        val trigger = beatmapMatcherPattern.find(content)!!.groupValues[1]

        val recommendedDifficulty = OsuWebApi.searchBeatmapSet(
            sender.id, "13df7m40t6ynm23f4g07ym"
        ).ifRight { it.recommendedDifficulty } ?: 0.0

        val selected: AtomicRef<Pair<Ruleset, BeatmapSkill>?> = atomic(null)
        var matchedAnyRuleset = false
        Database.query { db ->
            val allRuleset = db.sequenceOf(RulesetCollection).toList()
            //匹配 matchers，即关键词触发
            val matchedRuleset = allRuleset.filter { bt ->
                bt.enabled == 1 && bt.triggers
                    .split(";")
                    .map { t -> Regex(t, RegexOption.IGNORE_CASE) }
                    .any { r -> r.matches(trigger) }
            }.sortedByDescending { it.priority }

            val priorityList = matchedRuleset.map { it.priority }.toMutableList()

            while (
                selected.value == null &&
                !priorityList.all { it == 0 } &&
                matchedRuleset.isNotEmpty()
            ) { kotlin.run returnPoint@ {
                matchedAnyRuleset = true
                val rulesetIndex = getRandomWithWeight(priorityList)
                val currentRuleset = matchedRuleset[rulesetIndex]

                val triggerMatchRuleset = lazy r@ {
                    currentRuleset.triggers.split(";").mapIndexed { idx, t ->
                        val matchResult = Regex(t, RegexOption.IGNORE_CASE).matchEntire(trigger)
                        if(matchResult != null) {
                            return@r idx to matchResult.groupValues
                        }
                    }
                    return@r -1 to listOf<String>()
                }

                val searchedBeatmap = db.sequenceOf(BeatmapSkillTable).filter { btColumn ->
                    try {
                        ScriptEnvironHost.evaluateAndGetResult<ColumnDeclaringBooleanWrapped>(
                            currentRuleset.expression, properties = mapOf(
                                //tool function
                                "contains" to ScriptEnvironHost.createJSFunctionFromKJvmStatic("contains",
                                    ConvenientToolsForBeatmapSkill::contains
                                ),
                                //variable
                                "recommendStar" to recommendedDifficulty,
                                "matchIndex" to triggerMatchRuleset.value.first,
                                "matchGroup" to triggerMatchRuleset.value.second.drop(1),
                                //column
                                "bid" to Wrapper(btColumn.bid),
                                "star" to Wrapper(btColumn.stars),
                                "bpm" to Wrapper(btColumn.bpm),
                                "length" to Wrapper(btColumn.length),
                                "ar" to Wrapper(btColumn.approachingRate),
                                "od" to Wrapper(btColumn.overallDifficulty),
                                "cs" to Wrapper(btColumn.circleSize),
                                "hp" to Wrapper(btColumn.hpDrain),
                                "jump" to Wrapper(btColumn.jumpAimStrain),
                                "flow" to Wrapper(btColumn.flowAimStrain),
                                "speed" to Wrapper(btColumn.speedStrain),
                                "stamina" to Wrapper(btColumn.staminaStrain),
                                "precision" to Wrapper(btColumn.precisionStrain),
                                "accuracy" to Wrapper(btColumn.rhythmComplexity)
                            )
                        ).unwrap()
                    } catch (ex: EcmaError) {
                        atReply(" " + """
                                An error occurred when executing condition expression: 
                                $ex
                                Please contact this ruleset creator for more information.
                                Ruleset info: id=${currentRuleset.id}, creator qq: ${currentRuleset.author}
                            """.trimIndent().deserializeMiraiCode())
                        currentRuleset.lastError = ex.toString()
                        currentRuleset.enabled = 0
                        currentRuleset.priority --
                        currentRuleset.flushChanges()
                        priorityList[rulesetIndex] = 0 //发生错误，不再匹配这个规则
                        return@returnPoint
                    } catch (ex: ClassCastException) {
                        atReply(" " + """
                                    Return type of this condition expression is not ColumnDeclaring<Boolean>.
                                    Please contact this ruleset creator for more information.
                                    Ruleset info: id=${currentRuleset.id}, creator qq: ${currentRuleset.author}
                                """.trimIndent().deserializeMiraiCode())
                        currentRuleset.lastError = "Return type of this condition expression is not ColumnDeclaring<Boolean>."
                        currentRuleset.enabled = 0
                        currentRuleset.priority --
                        currentRuleset.flushChanges()
                        priorityList[rulesetIndex] = 0 //发生错误，不再匹配这个规则
                        return@returnPoint
                    } catch (ex: ScriptTimeoutException) {
                        atReply(" " + """
                                    Script execution timeout waiting for ${ex.limit} ms.
                                    Please contact this ruleset creator for more information.
                                    Ruleset info: id=${currentRuleset.id}, creator qq: ${currentRuleset.author}
                                """.trimIndent().deserializeMiraiCode())
                        currentRuleset.lastError = "Evaluation timeout waiting for 2000ms"
                        currentRuleset.enabled = 0
                        currentRuleset.priority --
                        currentRuleset.flushChanges()
                        priorityList[rulesetIndex] = 0 //发生错误，不再匹配这个规则
                        return@returnPoint
                    }
                }.toList()

                if(searchedBeatmap.isNotEmpty()) { //找到谱面，结束 while 循环
                    selected.compareAndSet(null, currentRuleset to searchedBeatmap.random())
                } else { //未找到谱面，不再匹配这个规则
                    priorityList[rulesetIndex] = 0
                }
            } }
        }

        if(selected.value != null) {
            val r = selected.value!!
            val beatmapInfo = OsuWebApi.getBeatmap(sender.id, r.second.bid)
            val additionalTip = Database.query { db ->
                db.sequenceOf(BeatmapCommentTable).find {
                    it.rulesetId eq r.first.id and (it.bid eq r.second.bid)
                } ?.content ?: ""
            } ?: ""

            val bytes = withContext(graphicProcessorDispatcher) {
                MapSuggester.drawRecommendBeatmapCard(
                    beatmapInfo, r.first, r.second, additionalTip
                ).bytes(EncodedImageFormat.PNG)
            }
            val externalResource = bytes.toExternalResource("png")
            val image = group.uploadImage(externalResource)
            runInterruptible { externalResource.close() }
            atReply(buildMessageChain {
                add(image)
                add("\n谱面链接: https://osu.ppy.sh/")
                beatmapInfo.ifRight {
                    add("beatmapsets/${it.beatmapset!!.id}#osu/${it.id}")
                } ?: add("b/${r.second.bid}")
                add("\nosu!direct: ")
                add("${PluginConfig.osuAuth.authCallbackBaseUrl}/$IMPORT_BEATMAP_PATH/${r.second.bid}")
                add("\n如果你(不)赞赏这条谱面规则，可对这条谱面推荐回复 \"+\"(\"-\")，它将有更大(小)的概率推荐给其他人。")
            }).also { receipt ->
                SuggestedBeatmapCache.cache[receipt.sourceIds.sum()] = SUGBMPInfo(
                    groupId = group.id, rulesetId = r.first.id, bid = r.second.bid
                )
            }
        } else if(matchedAnyRuleset) {
            atReply("未找到任何符合条件的谱面。")
        } else {
            atReply("未匹配任何推荐规则，请输入 \".ruleset all\" 查看所有匹配规则。")
        }
    }

    routeLock(startsWith("+") or startsWith("-")) {
        val quote = message[QuoteReply] ?: return@routeLock

        val msgCache = SuggestedBeatmapCache.cache[quote.source.ids.sum()]
        val att = message.findIsInstance<PlainText>()!!.content.take(1)

        if(msgCache != null && msgCache.groupId == group.id) {
            Database.query { db ->
                val existComment = db.sequenceOf(RulesetCommentTable).find {
                    it.rulesetId eq msgCache.rulesetId and (it.commenterQq eq sender.id)
                }
                val targetRuleset = db.sequenceOf(RulesetCollection).find { it.id eq msgCache.rulesetId }

                if (targetRuleset != null) {
                    if(existComment != null) {
                        if(existComment.positive == 1 && att == "-") {
                            targetRuleset.priority -= 2
                            existComment.positive = 0
                            targetRuleset.flushChanges()
                            existComment.flushChanges()
                            atReply("评价完成。")
                        } else if(existComment.positive == 0 && att == "+") {
                            targetRuleset.priority += 2
                            existComment.positive = 1
                            targetRuleset.flushChanges()
                            existComment.flushChanges()
                            atReply("评价完成。")
                        } else {
                            atReply("不可重复评价谱面规则。")
                        }
                    } else {
                        targetRuleset.priority += if (att == "+") 1 else -1
                        RulesetCommentTable.insert(RulesetComment {
                            rulesetId = msgCache.rulesetId
                            commenterQq = sender.id
                            positive = if (att == "+") 1 else 0
                        })
                        targetRuleset.flushChanges()
                        atReply("评价完成。")
                    }
                } else {
                    atReply("谱面规则不存在，可能已经被删除。")
                }
                Unit
            }
        }
    }
}
