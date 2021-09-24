package me.stageguard.obms.bot.route

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.bot.graphicProcessorDispatcher
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.*
import me.stageguard.obms.frontend.route.IMPORT_BEATMAP_PATH
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.MapSuggester
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.script.ScriptContext
import me.stageguard.obms.script.synthetic.wrapped.ConvenientToolsForBeatmapSkill
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringBooleanWrapped
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringComparableNumberWrapped
import me.stageguard.obms.utils.Either.Companion.ifRight
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
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

val beatmapMatcherPattern = Regex("[来|搞]一?[张|点](.+)(?:[谱|铺]面?|图)", RegexOption.IGNORE_CASE)
fun GroupMessageSubscribersBuilder.suggesterTrigger() {
    routeLock(finding(beatmapMatcherPattern)) { content ->
        try {
            val trigger = beatmapMatcherPattern.find(content)!!.groupValues[1]

            val recommendedDifficulty = OsuWebApi.searchBeatmapSet(
                sender.id, "13df7m40t6ynm23f4g07ym"
            ).ifRight { it.recommendedDifficulty } ?: 0.0

            val selected: AtomicRef<Pair<BeatmapType, BeatmapSkill>?> = atomic(null)
            var matchedAnyRuleset = false
            Database.query { db ->
                val allRuleset = db.sequenceOf(BeatmapTypeTable).toList()
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

                    val searchedBeatmap = db.sequenceOf(BeatmapSkillTable).filter { btColumn ->
                        try {
                            ScriptContext.evaluateAndGetResult<ColumnDeclaringBooleanWrapped>(
                                currentRuleset.condition, properties = mapOf(
                                    //tool function
                                    "contains" to ScriptContext.createJSFunctionFromKJvmStatic("contains",
                                        ConvenientToolsForBeatmapSkill::contains
                                    ),
                                    //variable
                                    "recommendStar" to recommendedDifficulty,
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
                val additionalTip = "Additional tip, but not implemented now."

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
                })
            } else if(matchedAnyRuleset) {
                atReply("未找到任何符合条件的谱面。")
            } else {
                atReply("未匹配任何推荐规则，请输入 \".ruleset all\" 查看所有匹配规则。")
            }


        } catch (ex: Exception) {
            atReply("获取谱面推荐时发生了错误：${parseExceptions(ex)}")
        }
    }
}