package me.stageguard.obms.bot.route

import kotlinx.coroutines.launch
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.BeatmapType
import me.stageguard.obms.database.model.BeatmapTypeTable
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.script.ScriptContext
import me.stageguard.sctimetable.utils.*
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import org.ktorm.dsl.eq
import org.ktorm.entity.*
import java.lang.NumberFormatException
import java.time.LocalDate

fun GroupMessageSubscribersBuilder.ruleset() {
    suspend fun InteractiveConversationBuilder.processInteractive(
        triggers: MutableList<String> = mutableListOf(), conditionExpressionArg: String? = null
    ) {
        var (addTriggerStep, conditionStep) = true to true
        var conditionExpression: String? = conditionExpressionArg
        var editing = true
        while (editing) {
            if(addTriggerStep) {
                send("""
                为你的规则设置触发词，一行一个，允许正则表达式，不允许空格。
                输入 "退出" 来终止添加规则过程。
                当前的触发词有：${triggers.ifEmpty { listOf("<空>") }.joinToString()}
            """.trimIndent())
                triggers.clear()
                select(checkBlock = {
                    val content = it.contentToString()
                    if (content.contains("退出")) true else !content.contains(";")
                }) {
                    "退出" { finish("QUIT_OPERATION") }
                    default {
                        triggers.addAll(it.contentToString().split("\n"))
                        addTriggerStep = false
                    }
                }
            }
            if(conditionStep) {
                send("""
                为你的规则设置 JavaScript 匹配表达式。
                输入 "退出" 来终止添加规则过程。
                当前规则表达式：${conditionExpression ?: "无"}
                若不了解如何写，请参阅：XXX
            """.trimIndent())
                select(checkBlock = {
                    val content = it.contentToString()
                    if (content.contains("退出")) true else {
                        try {
                            val checkedMessage = ScriptContext.checkSyntax(content)
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
                }) {
                    "退出" { finish("QUIT_OPERATION") }
                    default {
                        conditionExpression = it.contentToString()
                        conditionStep = false
                    }
                }
            }
            send("""
            设置初步完成。
            * 触发词：${triggers.ifEmpty { listOf("<空>") }.joinToString()}
            * 规则表达式：$conditionExpression
            若需要修改：
            * 修改触发词请发送 1，修改规则表达式请发送 2。
            * 输入其他文字将同时重新修改所有两项。
            全部设置已完成，请输入 完成。
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

    routeLock(startsWith(".ruleset add")) {
        try {
            OsuUserInfo.getOsuId(sender.id) ?: throw IllegalStateException("NOT_BIND")

            Database.query { db -> RouteLock.withLockSuspend(sender) {
                interactiveConversation(eachTimeLimit = 30000L) {
                    send("""
                    添加新的谱面类型规则。
                    请确保熟悉了谱面类型规则表达式后再进行添加，参阅：xxxx
                    请选择操作方式：
                      1. 在 QQ 聊天中交互。
                      2. 在 Web 中编辑。
                    发送序号以决定。
                """.trimIndent())
                    select {
                        "1" { processInteractive() }
                        "2" {
                            atReply("Not implemented.")
                            finish("EDIT_IN_WEB")
                        }
                        default {
                            finish("UNKNOWN_OPERATION")
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
                        enabled = 1
                        lastError = ""
                    })
                    atReply("添加完成。")
                }.exception {
                    when(it) {
                        is QuitConversationExceptions.AdvancedQuitException -> {
                            when {
                                it.toString().contains("QUIT_OPERATION") -> atReply("结束操作。")
                                it.toString().contains("UNKNOWN_OPERATION") -> atReply("未知的操作方式。")
                                it.toString().contains("EDIT_IN_WEB") -> {

                                }
                            }
                        }
                        is QuitConversationExceptions.TimeoutException -> {
                            atReply("长时间未输入(30s)，请重新添加。")
                        }
                    }
                }
            } }
        } catch (ex: Exception) {
            atReply("添加谱面类型规则时发生了错误：${parseExceptions(ex)}")
        }
    }

    routeLock(startsWith(".ruleset edit")) {
        try {
            val rulesetId = try {
                message.contentToString().removePrefix(".ruleset edit").trim().toInt()
            } catch (ex: NumberFormatException) {
                throw IllegalStateException("INVALID_INPUT_FORMAT")
            }

            Database.query { db ->
                val ruleset = db.sequenceOf(BeatmapTypeTable).find { it.id eq rulesetId }
                if(ruleset != null && ruleset.author == sender.id) {
                    interactiveConversation {
                        send("""
                        正在编辑谱面类型规则。
                        请选择操作方式：
                          1. 在 QQ 聊天中交互。
                          2. 在 Web 中编辑。
                        发送序号以决定。
                    """.trimIndent())
                        select {
                            "1" {
                                processInteractive(ruleset.triggers.split(";").toMutableList(), ruleset.condition)
                            }
                            "2" {
                                atReply("Not implemented.")
                                finish("EDIT_IN_WEB")
                            }
                            default {
                                finish("UNKNOWN_OPERATION")
                            }
                        }
                    }.finish {
                        ruleset.triggers = it["triggers"].cast<List<String>>().joinToString(";")
                        ruleset.condition = it["condition"].cast()
                        ruleset.lastEdited = LocalDate.now()
                        ruleset.enabled = 1
                        ruleset.lastError = ""
                        ruleset.flushChanges()
                        atReply("修改完成。")
                    }.exception {
                        when(it) {
                            is QuitConversationExceptions.AdvancedQuitException -> {
                                when {
                                    it.toString().contains("QUIT_OPERATION") -> atReply("结束操作。")
                                    it.toString().contains("UNKNOWN_OPERATION") -> atReply("未知的操作方式。")
                                    it.toString().contains("EDIT_IN_WEB") -> {

                                    }
                                }
                            }
                            is QuitConversationExceptions.TimeoutException -> {
                                atReply("长时间未输入(30s)，请重新添加。")
                            }
                        }
                    }
                } else {
                    atReply("没有找到 ID 为 $rulesetId 的谱面规则，或不是这个谱面规则的创建者。")
                }
            }
        } catch (ex: Exception) {
            atReply("编辑谱面类型规则时发生了错误：${parseExceptions(ex)}")
        }
    }
}