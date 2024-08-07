package me.stageguard.obms.bot.route

/*
fun GroupMessageSubscribersBuilder.ruleset() {
    suspend fun InteractiveConversationBuilder.processInteractive(
        nameArg: String? = null,
        triggers: MutableList<String> = mutableListOf(),
        conditionExpressionArg: String? = null
    ) {
        var editNameStep = true
        var addTriggerStep = true
        var conditionStep = true

        var conditionExpression: String? = conditionExpressionArg
        var name: String? = nameArg

        var editing = true
        while (editing) {
            if(editNameStep) {
                send("""
                为你的规则设置名称，用于显示在推图结果中。
                输入 "退出" 来终止添加规则过程。
                当前名称：${name ?: "<空>"}
            """.trimIndent())
                select {
                    "退出" { finish("QUIT_OPERATION") }
                    default {
                        name = it.contentToString()
                        editNameStep = false
                    }
                }
            }
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
            """.trimIndent())
                select(checkBlock = {
                    val content = it.contentToString()
                    if (content.contains("退出")) true else {
                        try {
                            val checkedMessage = ScriptEnvironHost.checkSyntax(content)
                            if(checkedMessage.second.isNotEmpty()) {
                                ScriptEnvironHost.launch { send(buildString {
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
                            ScriptEnvironHost.launch { send("未知错误，请反馈至开发者：$ex") }
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
            * 规则名称：$name
            * 触发词：${triggers.ifEmpty { listOf("<空>") }.joinToString()}
            * 规则表达式：$conditionExpression
            若需要修改：
            * 修改名称请发送 1，修改触发词 2，修改规则表达式请发送 3。
            * 输入其他文字将同时重新修改所有两项。
            全部设置已完成，请输入 完成。
        """.trimIndent())
            select {
                "1" { editNameStep = true }
                "2" { addTriggerStep = true }
                "3" { conditionStep = true }
                "完成" { editing = false }
                default {
                    editNameStep = true
                    conditionStep = true
                    addTriggerStep = true
                }
            }
        }
        collect("name", name!!)
        collect("triggers", triggers)
        collect("expression", conditionExpression!!)
    }

    routeLock(startWithIgnoreCase(".ruleset add")) {
        OsuUserInfo.getOsuId(sender.id) ?: throw NotBindException(sender.id)

        RouteLock.withLockSuspend(sender) {
            interactiveConversation(eachTimeLimit = 60000L) {
                send("""
                    添加新的谱面类型规则。
                    请确保熟悉了谱面类型规则后再进行添加。
                    查看 https://github.com/StageGuard/OsuMapSuggester/wiki/Beatmap-Type-Ruleset 获取更多信息。
                    请选择操作方式：
                      1. 在 QQ 聊天中交互。
                      2. 在 Web 中编辑（推荐）。
                    发送序号以决定。
                """.trimIndent())
                select {
                    "1" { processInteractive() }
                    "2" { finish("EDIT_IN_WEB") }
                    default {
                        finish("UNKNOWN_OPERATION")
                    }
                }
            }.finish {
                RulesetCollection.insert(Ruleset {
                    name = it["name"].cast()
                    triggers = it["triggers"].cast<List<String>>().joinToString(";")
                    author = sender.id
                    expression = it["expression"].cast()
                    priority = 50
                    addDate = LocalDate.now()
                    lastEdited = LocalDate.now()
                    enabled = 1
                    lastError = ""
                })
                OsuMapSuggester.logger.info { "New ruleset added from from interactive chat: ${it["name"]} by qq ${sender.id}." }
                atReply("添加完成。")
            }.exception {
                when(it) {
                    is QuitConversationExceptions.AdvancedQuitException -> {
                        when {
                            it.toString().contains("QUIT_OPERATION") -> atReply("结束操作。")
                            it.toString().contains("UNKNOWN_OPERATION") -> atReply("未知的操作方式。")
                            it.toString().contains("EDIT_IN_WEB") -> {
                                atReply("请访问 ${PluginConfig.osuAuth.authCallbackBaseUrl}/$RULESET_PATH/edit/new 添加。")
                            }
                        }
                    }
                    is QuitConversationExceptions.TimeoutException -> {
                        atReply("长时间未输入(30s)，请重新添加。")
                    }
                    else -> { }
                }
            }
        }
    }

    routeLock(startWithIgnoreCase(".ruleset edit")) {
        OsuUserInfo.getOsuId(sender.id) ?: throw NotBindException(sender.id)

        val rulesetId = try {
            message.contentToString().removePrefix(".ruleset edit").trim().toInt()
        } catch (ex: NumberFormatException) {
            throw IllegalStateException("INVALID_INPUT_FORMAT")
        }

        Database.query { db ->
            val ruleset = db.sequenceOf(RulesetCollection).find { it.id eq rulesetId }
            if(ruleset != null && ruleset.author == sender.id) {
                interactiveConversation(eachTimeLimit = 60000L) {
                    send("""
                        正在编辑谱面类型规则。
                        请选择操作方式：
                          1. 在 QQ 聊天中交互。
                          2. 在 Web 中编辑（推荐）。
                        发送序号以决定。
                    """.trimIndent())
                    select {
                        "1" {
                            processInteractive(
                                ruleset.name,
                                ruleset.triggers.split(";").toMutableList(),
                                ruleset.expression
                            )
                        }
                        "2" { finish("EDIT_IN_WEB") }
                        default {
                            finish("UNKNOWN_OPERATION")
                        }
                    }
                }.finish {
                    ruleset.name = it["name"].cast()
                    ruleset.triggers = it["triggers"].cast<List<String>>().joinToString(";")
                    ruleset.expression = it["expression"].cast()
                    ruleset.lastEdited = LocalDate.now()
                    ruleset.enabled = 1
                    ruleset.lastError = ""
                    ruleset.flushChanges()
                    atReply("修改完成。")
                    OsuMapSuggester.logger.info { "Ruleset changed from interactive chat: ${it["name"]}(id=${rulesetId}) by qq ${sender.id}." }
                }.exception {
                    when(it) {
                        is QuitConversationExceptions.AdvancedQuitException -> {
                            when {
                                it.toString().contains("QUIT_OPERATION") -> atReply("结束操作。")
                                it.toString().contains("UNKNOWN_OPERATION") -> atReply("未知的操作方式。")
                                it.toString().contains("EDIT_IN_WEB") -> {
                                    atReply("请访问 ${PluginConfig.osuAuth.authCallbackBaseUrl}/$RULESET_PATH/edit/${rulesetId} 编辑。")
                                }
                            }
                        }
                        is QuitConversationExceptions.TimeoutException -> {
                            atReply("长时间未输入(30s)，请重新添加。")
                        }
                        else -> { }
                    }
                }
            } else {
                atReply("没有找到 ID 为 $rulesetId 的谱面规则，或不是这个谱面规则的创建者。")
            }
        }
    }

    routeLock(startWithIgnoreCase(".ruleset list") or startWithIgnoreCase(".ruleset all")) {
        OsuUserInfo.getOsuId(sender.id) ?: throw NotBindException(sender.id)

        Database.query { db ->
            val ruleset = db.sequenceOf(RulesetCollection).toList()

            val rulesetCreatorsInfo = ruleset.map { it.author }.toSet().map { it to OsuUserInfo.getOsuIdAndName(it) }

            val bytes = withContext(Dispatchers.IO) {
                MapSuggester.drawRulesetList(ruleset, rulesetCreatorsInfo).bytes(EncodedImageFormat.PNG)
            }
            val externalResource = bytes.toExternalResource("png")
            val image = withContext(Dispatchers.IO) { group.uploadImage(externalResource) }
            runInterruptible { externalResource.close() }

            atReply(image.toMessageChain())
        }
    }

    routeLock(startWithIgnoreCase(".ruleset delete")) {
        OsuUserInfo.getOsuId(sender.id) ?: throw NotBindException(sender.id)

        val rulesetId = message.contentToString().removePrefix(".ruleset delete").trim().run {
            try {
                toInt()
            } catch (ex: NumberFormatException) {
                throw InvalidInputException(this)
            }
        }

        Database.query { db ->
            val ruleset = db.sequenceOf(RulesetCollection).find { it.id eq rulesetId }
            if(ruleset != null && ruleset.author == sender.id) {
                interactiveConversation(eachTimeLimit = 10000L) {
                    send("""
                        确认要删除 ${ruleset.name} 类型谱面规则吗？
                        删除后将无法再触发这个谱面规则，且不可恢复。
                        输入 "确认" 或 "是" 来确认删除。
                    """.trimIndent())
                    select {
                        "是" { collect("delete", true) }
                        "确认" { collect("delete", true) }
                        default { collect("delete", false) }
                    }
                }.finish {
                    if(it["delete"].cast()) {
                        db.sequenceOf(BeatmapCommentTable).removeIf { col -> col.rulesetId eq ruleset.id }
                        OsuMapSuggester.logger.info { "Ruleset deleted from interactive chat: ${ruleset.name}(id=${ruleset.id}) by qq ${ruleset.author}." }
                        ruleset.delete()
                        atReply("删除成功。")
                    }
                }.exception {
                    when(it) {
                        is QuitConversationExceptions.TimeoutException -> {
                            atReply("长时间未输入(10s)，取消删除。")
                        }
                        else -> { }
                    }
                }
            } else {
                atReply("没有找到 ID 为 $rulesetId 的谱面规则，或不是这个谱面规则的创建者。")
            }
        }
    }
}
*/
