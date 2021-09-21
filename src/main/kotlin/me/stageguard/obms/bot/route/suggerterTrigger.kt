package me.stageguard.obms.bot.route

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock
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
import me.stageguard.obms.script.synthetic.ConvenientToolsForBeatmapSkill
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringBooleanWrapped
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringComparableNumberWrapped
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.sctimetable.utils.QuitConversationExceptions
import me.stageguard.sctimetable.utils.exception
import me.stageguard.sctimetable.utils.finish
import me.stageguard.sctimetable.utils.interactiveConversation
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import org.ktorm.entity.*
import org.mozilla.javascript.EcmaError
import java.time.LocalDate

typealias Wrapper<T> = ColumnDeclaringComparableNumberWrapped<T>

val beatmapMatcherPattern = Regex("[来|搞]一?[张|点](.+)(?:[谱|铺]面?|图)")
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
                val allTypes = db.sequenceOf(BeatmapTypeTable).sortedBy { it.priority }.map { it }
                // first: 这个 variant 的第几个关键词， second: 这个关键词的正则表达式
                var triggerMatchers = Regex(String())
                //匹配 matchers，即关键词触发

                allTypes.asSequence().filter { bt ->
                    bt.enabled == 1 && bt.triggers.split(";").map { t -> Regex(t) }.any { r ->
                        r.matches(trigger).also { triggerMatchers = r }
                    }
                }.forEach filterEach@ { ruleset ->
                    matchedAnyRuleset = true
                    if (selected.value == null) {
                        val triggerMatchesGroup = triggerMatchers.find(trigger)?.groupValues
                        val searchedBeatmap = db.sequenceOf(BeatmapSkillTable).filter { btColumn ->
                            try {
                                ScriptContext.evaluateAndGetResult<ColumnDeclaringBooleanWrapped>(
                                    ruleset.condition, properties = mapOf(
                                        //tool function
                                        "contains" to ScriptContext.createJSFunctionFromKJvmStatic("contains",
                                            ConvenientToolsForBeatmapSkill::contains
                                        ),
                                        //variable
                                        "recommendStar" to recommendedDifficulty,
                                        "matchResult" to triggerMatchesGroup,
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
                                    Ruleset info: id=${ruleset.id}, creator qq: ${ruleset.author}
                                """.trimIndent().deserializeMiraiCode())
                                ruleset.lastError = ex.toString()
                                ruleset.enabled = 0
                                ruleset.flushChanges()
                                return@filterEach
                            } catch (ex: ClassCastException) {
                                atReply(" " + """
                                        Return type of this condition expression is not ColumnDeclaring<Boolean>.
                                        Please contact this ruleset creator for more information.
                                        Ruleset info: id=${ruleset.id}, creator qq: ${ruleset.author}
                                    """.trimIndent().deserializeMiraiCode())
                                ruleset.lastError = "Return type of this condition expression is not ColumnDeclaring<Boolean>."
                                ruleset.enabled = 0
                                ruleset.flushChanges()
                                return@filterEach
                            }
                        }.toList()

                        if(searchedBeatmap.isNotEmpty()) {
                            selected.compareAndSet(null, ruleset to searchedBeatmap.random())
                        }
                    } else return@query //跳出搜图过程
                }
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