package me.stageguard.obms.bot.route

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.*
import me.stageguard.obms.graph.format2DFix
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.script.ScriptContext
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringBooleanWrapped
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringComparableNumberWrapped
import me.stageguard.obms.script.synthetic.ConvenientToolsForBeatmapSkill.contains as toolContains
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.sctimetable.utils.QuitConversationExceptions
import me.stageguard.sctimetable.utils.exception
import me.stageguard.sctimetable.utils.finish
import me.stageguard.sctimetable.utils.interactiveConversation
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import org.ktorm.entity.*
import java.time.LocalDate

typealias Wrapper<T> = ColumnDeclaringComparableNumberWrapped<T>

fun GroupMessageSubscribersBuilder.suggesterTrigger() {
    finding(Regex("[来|搞]一?[张|点](.+)(?:[谱|铺]面?|图)")) { mr ->
        try {
            val variant = mr.groupValues[1]

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
                    bt.triggers.split(";").map { t -> Regex(t) }.any { r ->
                        r.matches(variant).also { triggerMatchers = r }
                    }
                }.forEach filterEach@ { ruleset ->
                    matchedAnyRuleset = true
                    if (selected.value == null) {
                        val triggerMatchesGroup = triggerMatchers.find(variant)?.groupValues
                        val searchedBeatmap = db.sequenceOf(BeatmapSkillTable).filter { btColumn ->
                            try {
                                ScriptContext.evaluateAndGetResult<ColumnDeclaringBooleanWrapped>(
                                    ruleset.condition, properties = mapOf(
                                        //tool function
                                        "contains" to ScriptContext.createJSFunctionFromKJvmStatic("contains", ::toolContains),
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
                                )?.unwrap() ?: kotlin.run {
                                    atReply("""
                                        Return type of this condition expression is not ColumnDeclaring<Boolean>.
                                        Please contact this ruleset creator for more information.
                                        Ruleset info: id=${ruleset.id}, creator qq: ${ruleset.author}
                                    """.trimIndent())
                                    return@filterEach
                                }
                            } catch (ex: IllegalStateException) {
                                atReply("""
                                    An error occurred when executing condition expression: 
                                    $ex
                                    Please contact this ruleset creator for more information.
                                    Ruleset info: id=${ruleset.id}, creator qq: ${ruleset.author}
                                """.trimIndent())
                                return@filterEach
                            }
                        }.map { bsk -> bsk }

                        if(searchedBeatmap.isNotEmpty()) {
                            selected.compareAndSet(null, ruleset to searchedBeatmap.random())
                        }
                    } else return@query //跳出搜图过程
                }
            }

            if(selected.value != null) {
                val unwrapped = selected.value!!
                atReply("""
                    BID: ${unwrapped.second.bid}
                    Stars: ${format2DFix.format(unwrapped.second.stars)}
                    Recommender: ${OsuUserInfo.getOsuIdAndName(unwrapped.first.author).run { 
                        if(this != null) "$second(QQ: ${unwrapped.first.author}, OsuID: $first)" else unwrapped.first.author
                    }}
                    Link: https://osu.ppy.sh/b/${unwrapped.second.bid}
                """.trimIndent())
            } else {
                if(matchedAnyRuleset) {
                    atReply("No proper beatmap found.")
                } else {
                    atReply("No this ruleset.")
                }
            }

        } catch (ex: Exception) {
            atReply("获取谱面推荐时发生了错误：${parseExceptions(ex)}")
        }
    }

    startsWith(".addRuleset") {
        try {
            OsuUserInfo.getOsuId(sender.id) ?: throw IllegalStateException("NOT_BIND")

            Database.query { db ->
                interactiveConversation {
                    send("""
                    添加新的谱面类型规则。
                    请确保熟悉了谱面类型规则表达式后再进行添加，参阅：xxxx
                    请选择操作方式：
                      1. 在 QQ 聊天中交互。
                      2. 在 Web 中编辑。
                    发送序号以决定。
                """.trimIndent())
                    select {
                        "1" {
                            var (addTriggerStep, conditionStep) = true to true
                            val triggers = mutableListOf<String>()
                            var conditionExpression: String? = null
                            var editing = true
                            while (editing) {
                                if(addTriggerStep) {
                                    send("""
                                    为你的规则设置触发词，一行一个，允许正则表达式，不允许空格。
                                    当前的触发词有：${triggers.ifEmpty { listOf("<空>") }.joinToString()}
                                """.trimIndent())
                                    triggers.clear()
                                    triggers.addAll(receivePlain(
                                        mismatchedMessage = "触发词中不允许出现英文分号，请重新输入"
                                    ) { !it.contains(";") }.split("\n"))
                                    addTriggerStep = false
                                }
                                if(conditionStep) {
                                    send("""
                                    为你的规则设置 JavaScript 匹配表达式。
                                    当前规则表达式：${conditionExpression ?: "无"}
                                    若不了解如何写，请参阅：XXX
                                """.trimIndent())
                                    conditionExpression = receivePlain {
                                        try {
                                            val checkedMessage = ScriptContext.checkSyntax(it)
                                            if(checkedMessage.second.isNotEmpty()) {
                                                ScriptContext.launch { send(buildString {
                                                    if(checkedMessage.first) {
                                                        append("编译出现了错误，请重新输入！\n")
                                                    }
                                                    append("编译信息：\n${
                                                        checkedMessage.second.joinToString("\n")
                                                    }")
                                                }) }
                                            }
                                            !checkedMessage.first
                                        } catch (ex: Exception) {
                                            ScriptContext.launch { send("未知错误，请反馈至开发者：$ex") }
                                            finish()
                                            false
                                        }
                                    }
                                    conditionStep = false
                                }
                                send("""
                                设置初步完成。
                                * 触发词：${triggers.ifEmpty { listOf("<空>") }.joinToString()}
                                * 规则表达式：$conditionExpression
                                若需要修改：
                                * 修改触发词请发送 1，修改规则表达式请发送 2。
                                * 输入其他文字将同时重新修改所有两项。
                                * 全部设置已完成，请输入 完成。
                            """.trimIndent())
                                select {
                                    "1" { addTriggerStep = true }
                                    "2" { conditionStep = true }
                                    "完成" { editing = false }
                                    default {
                                        conditionStep = true
                                        addTriggerStep = true
                                    }
                                }
                            }
                            collect("triggers", triggers)
                            collect("condition", conditionExpression!!)
                        }
                        "2" {
                            atReply("Not implemented.")
                            finish("NOT_IMPL")
                        }
                        default {
                            finish("QUIT_OPERATION")
                        }
                    }
                }.finish {
                    BeatmapTypeTable.insert(BeatmapType {
                        triggers = it["triggers"].cast<List<String>>().joinToString(";")
                        author = sender.id
                        condition = it["condition"].cast()
                        priority = db.sequenceOf(BeatmapTypeTable).sortedBy { it.priority }.run {
                            if(isEmpty()) 1 else last().priority + 1
                        }
                        addDate = LocalDate.now()
                        lastEdited = LocalDate.now()
                    })
                    atReply("添加完成。")
                }.exception {
                    when(it) {
                        is QuitConversationExceptions.AdvancedQuitException -> {
                            when {
                                it.toString().contains("QUIT_OPERATION") -> atReply("未知的方式，结束操作")
                            }
                        }
                        else -> { }
                    }
                }
            }
        } catch (ex: Exception) {
            atReply("添加谱面类型规则时发生了错误：${parseExceptions(ex)}")
        }
    }
}