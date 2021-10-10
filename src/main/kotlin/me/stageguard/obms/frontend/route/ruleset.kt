package me.stageguard.obms.frontend.route

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.*
import me.stageguard.obms.frontend.dto.*
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.BeatmapDTO
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.osu.api.oauth.OAuthManager.updateToken
import me.stageguard.obms.script.ScriptContext
import me.stageguard.obms.utils.SimpleEncryptionUtils
import net.mamoe.mirai.utils.info
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.or
import org.ktorm.entity.*
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.LocalDate

const val RULESET_PATH = "ruleset"
const val ENC_KEY = "tg@#F%^J*^I(%f19"

/**
 * 编辑谱面时的身份认证流程：
 * 1. 用户请求 `http://host/ruleset/{id}` 或 `http://host/ruleset/new` (下方的 (`I`))，回应 `ruleset.html` 前端。
 * 2. `ruleset.html` 前端获取 cookies 中的 `token`，认证身份(请求下方的 `II`)。
 * 3. 如果没有 `token`， `ruleset.html` 前端会请求后端(下方的 (`III`))获取 oauth 链接
 *    (`OAuthManager.createOAuthLink(type = AuthType.EDIT_RULESET)`)。
 * 4. 点击链接完成认证后，结果会在 `AUTH_CALLBACK_PATH` 中处理(在数据库中创建一个 `token` 并绑定到用户 QQ 上)，
 *    处理后会重定向至 (`I`)，并带有 `Set-Cookie: token`(通过 `context.response.cookies.append("token", ...)`)。
 */

@OptIn(ExperimentalSerializationApi::class)
fun Application.ruleset() {
    val json = Json {
        ignoreUnknownKeys = true
    }
    routing {
        suspend fun PipelineContext<Unit, ApplicationCall>.getRulesetPage() {
            val rulesetPage = getFrontendResourceStream("ruleset.html")
            if(rulesetPage != null) {
                runInterruptible(Dispatchers.IO) { runBlocking {
                    context.respondText(
                        rulesetPage.readAllBytes().toString(Charset.forName("UTF-8")),
                        ContentType.Text.Html, HttpStatusCode.OK
                    )
                } }
            } else {
                context.respond(HttpStatusCode.NotFound, "Page ruleset.html is not found.")
            }
            finish()
        }

        // (I): 回应前端 HTML，由前端处理 `{type}`
        get("/$RULESET_PATH/edit/new") { getRulesetPage() }
        get("/$RULESET_PATH/edit/{rid}") { getRulesetPage() }
        // (II): 认证 `token` 是否绑定了某个 QQ 账号。
        post("/$RULESET_PATH/verify") {
            try {
                val parameter = json.decodeFromString<WebVerificationRequestDTO>(context.receiveText())
                val querySequence = Database.query { db ->
                    val find = db.sequenceOf(WebVerificationStore).find {
                        it.token eq URLDecoder.decode(parameter.token, Charset.defaultCharset())
                    } ?: return@query WebVerificationResponseDTO(1)

                    if(find.qq == -1L)
                        return@query WebVerificationResponseDTO(2, osuId = find.osuId)

                    val userInfo = db.sequenceOf(OsuUserInfo).find { it.qq eq find.qq }
                        ?: return@query WebVerificationResponseDTO(3, osuId = find.osuId, qq = find.qq)

                    WebVerificationResponseDTO(
                        result = 0, qq = userInfo.qq,
                        osuId = userInfo.osuId, osuName = userInfo.osuName,
                        osuApiToken = SimpleEncryptionUtils.aesEncrypt(userInfo.updateToken().token, ENC_KEY)
                    )
                }

                context.respond(json.encodeToString(
                querySequence ?: WebVerificationResponseDTO(-1,
                        errorMessage = "Internal error: Database is disconnected from server."
                    )
                ))

            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(json.encodeToString(WebVerificationResponseDTO(-1, errorMessage = ex.toString())))
            }
            finish()
        }
        // (III): 回应一个 `oAuth` 链接
        post("/$RULESET_PATH/getVerifyLink") {
            try {
                val parameter = json.decodeFromString<CreateVerificationLinkRequestDTO>(context.receiveText())
                val link = OAuthManager.createOAuthLink(AuthType.EDIT_RULESET, listOf(parameter.callbackPath))
                context.respond(json.encodeToString(CreateVerificationLinkResponseDTO(0, link = link)))
            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(
                    json.encodeToString(CreateVerificationLinkResponseDTO(-1, errorMessage = ex.toString()))
                )
            }
            finish()
        }
        // 检测是否有权限编辑这个 ruleset
        post("/$RULESET_PATH/checkAccess") {
            try {
                val parameter = json.decodeFromString<CheckEditAccessRequestDTO>(context.receiveText())

                if(parameter.editType == 1) {
                    context.respond(json.encodeToString(CheckEditAccessResponseDTO(0)))
                } else if (parameter.editType == 2) {
                    val querySequence = Database.query { db ->
                        val ruleset = db.sequenceOf(RulesetCollection).find { it.id eq parameter.rulesetId }
                            ?: return@query CheckEditAccessResponseDTO(1)

                        if(ruleset.author != parameter.qq)
                            return@query CheckEditAccessResponseDTO(2, EditRulesetDTO(name = ruleset.name))

                        CheckEditAccessResponseDTO(0, EditRulesetDTO(
                            id = ruleset.id, name = ruleset.name, expression = ruleset.expression,
                            triggers = ruleset.triggers.split(";").map { it.trim() }
                        ))
                    }

                    context.respond(json.encodeToString(
                        querySequence ?: CheckEditAccessResponseDTO(-1,
                            errorMessage = "Internal error: Database is disconnected from server."
                        )
                    ))
                } else {
                    context.respond(json.encodeToString(CheckEditAccessResponseDTO(1)))
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(json.encodeToString(CheckEditAccessResponseDTO(-1, errorMessage = ex.toString())))
            }
            finish()
        }
        // 检测 JavaScript 语法错误
        post("/$RULESET_PATH/checkSyntax") {
            try {
                val parameter = json.decodeFromString<CheckSyntaxRequestDTO>(context.receiveText())
                val result = ScriptContext.checkSyntax(parameter.code)
                context.respond(json.encodeToString(
                    CheckSyntaxResponseDTO(0, result.first, result.second)
                ))
            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(json.encodeToString(CheckSyntaxResponseDTO(-1, errorMessage = ex.toString())))
            }
            finish()
        }
        // 提交修改
        post("/$RULESET_PATH/submit") {
            try {
                val parameter = json.decodeFromString<SubmitRequestDTO>(context.receiveText())

                val querySequence = Database.query { db ->
                    val webUser = db.sequenceOf(WebVerificationStore).find {
                        it.token eq URLDecoder.decode(parameter.token, Charset.defaultCharset())
                    } ?: return@query SubmitResponseDTO(1)

                    if(webUser.qq <= 0)
                        return@query SubmitResponseDTO(1)

                    when(parameter.type) {
                        0 -> {
                            if(parameter.ruleset.run {
                                name.isEmpty() || triggers.isEmpty() ||
                                    triggers.any { it.contains(";") } ||
                                    ScriptContext.checkSyntax(expression).first
                            }) return@query SubmitResponseDTO(3)

                            var newId = 0
                            if(parameter.ruleset.id > 0) {
                                val existRuleset = db.sequenceOf(RulesetCollection).find {
                                    it.id eq parameter.ruleset.id
                                } ?: return@query SubmitResponseDTO(4)

                                if(existRuleset.author != webUser.qq)
                                    return@query SubmitResponseDTO(2)

                                existRuleset.name = parameter.ruleset.name
                                existRuleset.triggers = parameter.ruleset.triggers.joinToString(";")
                                existRuleset.expression = parameter.ruleset.expression
                                existRuleset.lastEdited = LocalDate.now()
                                existRuleset.enabled = 1
                                existRuleset.lastError = ""
                                existRuleset.flushChanges()
                                OsuMapSuggester.logger.info { "Ruleset changed from web: ${parameter.ruleset.name}(id=${newId}) by qq ${webUser.qq}." }
                            } else {
                                RulesetCollection.insert(Ruleset {
                                    name = parameter.ruleset.name
                                    triggers = parameter.ruleset.triggers.joinToString(";")
                                    author = webUser.qq
                                    expression = parameter.ruleset.expression
                                    priority = 50
                                    addDate = LocalDate.now()
                                    lastEdited = LocalDate.now()
                                    enabled = 1
                                    lastError = ""
                                })

                                newId = db.sequenceOf(RulesetCollection).filter {
                                    (it.name eq parameter.ruleset.name) and
                                            (it.triggers eq parameter.ruleset.triggers.joinToString(";")) and
                                            (it.author eq webUser.qq) and
                                            (it.expression eq parameter.ruleset.expression) and
                                            (it.priority eq 50) and
                                            (it.lastEdited eq LocalDate.now()) and
                                            (it.enabled eq 1) and
                                            (it.lastError eq "")
                                }.last().id
                            }
                            OsuMapSuggester.logger.info { "New ruleset added from web: ${parameter.ruleset.name}(id=${newId}) by qq ${webUser.qq}." }
                            SubmitResponseDTO(0, newId = newId)
                        }
                        1 -> {
                            if(parameter.ruleset.id > 0) {
                                val existRuleset = db.sequenceOf(RulesetCollection).find {
                                    it.id eq parameter.ruleset.id
                                } ?: return@query SubmitResponseDTO(4)

                                if(existRuleset.author != webUser.qq)
                                    return@query SubmitResponseDTO(2)

                                existRuleset.delete()
                                OsuMapSuggester.logger.info { "Ruleset deleted from web: ${parameter.ruleset.name}(id=${parameter.ruleset.id}) by qq ${webUser.qq}." }
                                SubmitResponseDTO(5)
                            } else {
                                SubmitResponseDTO(4)
                            }
                        } else -> SubmitResponseDTO(6)
                    }
                }

                context.respond(json.encodeToString(
                    querySequence ?: SubmitResponseDTO(-1,
                        errorMessage = "Internal error: Database is disconnected from server."
                    )
                ))

            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(json.encodeToString(SubmitResponseDTO(-1, errorMessage = ex.toString())))
            }
            finish()
        }
        // 获取谱面备注
        post("/$RULESET_PATH/getBeatmapComment") {
            try {
                val parameter = json.decodeFromString<GetBeatmapCommentRequestDTO>(context.receiveText())

                val querySequence = Database.query { db ->
                    val comments = mutableMapOf(*parameter.beatmap.map { it to "" }.toTypedArray())

                    db.sequenceOf(BeatmapCommentTable).filter { col ->
                        if(parameter.beatmap.isEmpty()) {
                            col.bid.eq(-1)
                        } else if(parameter.beatmap.size == 1) {
                            col.bid.eq(parameter.beatmap.single())
                        } else {
                            parameter.beatmap.drop(1).map { col.bid.eq(it) }
                                .fold(col.bid.eq(parameter.beatmap.first())) { r, t -> r.or(t) }
                        } and (col.rulesetId eq parameter.rulesetId)
                    }.forEach { comments[it.bid] = it.content }

                    GetBeatmapCommentResponseDTO(0, comments = comments.map {
                        BeatmapIDWithCommentDTO(it.key, it.value)
                    })
                }

                context.respond(json.encodeToString(
                    querySequence ?: GetBeatmapCommentResponseDTO(-1,
                        errorMessage = "Internal error: Database is disconnected from server."
                    )
                ))
            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(json.encodeToString(GetBeatmapCommentResponseDTO(-1, errorMessage = ex.toString())))
            }
        }
        // 获取谱面信息，由于 osu!api 的 cors 限制，不能从前端访问 osu!api。
        post("/$RULESET_PATH/cacheBeatmapInfo") {
            try {
                val parameter = json.decodeFromString<CacheBeatmapInfoRequestDTO>(context.receiveText())
                val decryptedOsuApiToken = SimpleEncryptionUtils.aesDecrypt(parameter.osuApiToken, ENC_KEY)

                // 不直接使用 OAuthManager.getBindingToken 因为每次使用就要查询一次数据库。
                val apiResponse = OsuWebApi.getImpl<String, BeatmapDTO>(
                    url = OsuWebApi.BASE_URL_V2 + "/beatmaps/${parameter.bid}/",
                    parameters = mapOf(),
                    mapOf("Authorization" to "Bearer $decryptedOsuApiToken")
                ) { json.decodeFromString(this) }

                context.respond(json.encodeToString(CacheBeatmapInfoResponseDTO(0,
                    source = apiResponse.beatmapset!!.source,
                    title = apiResponse.beatmapset.title,
                    artist = apiResponse.beatmapset.artist,
                    difficulty = apiResponse.difficultyRating,
                    version = apiResponse.version
                )))
            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(json.encodeToString(CacheBeatmapInfoResponseDTO(-1, errorMessage = ex.toString())))
            }
        }
        // 提交谱面备注
        post("/$RULESET_PATH/submitBeatmapComment") {
            try {
                val parameter = json.decodeFromString<SubmitBeatmapCommentRequestDTO>(context.receiveText())

                val querySequence = Database.query { db ->
                    val webUser = db.sequenceOf(WebVerificationStore).find {
                        it.token eq URLDecoder.decode(parameter.token, Charset.defaultCharset())
                    } ?: return@query SubmitBeatmapCommentResponseDTO(1)

                    if(webUser.qq <= 0)
                        return@query SubmitBeatmapCommentResponseDTO(1)

                    val existRuleset = db.sequenceOf(RulesetCollection).find {
                        it.id eq parameter.rulesetId
                    } ?: return@query SubmitBeatmapCommentResponseDTO(4)

                    if(existRuleset.author != webUser.qq)
                        return@query SubmitBeatmapCommentResponseDTO(2)

                    // 筛选不为空的备注
                    val toInsert = parameter.comments.filter { it.comment.trim().isNotEmpty() }.toMutableList()
                    val toUpdate = mutableListOf<BeatmapIDWithCommentDTO>()

                    // 查找已存在的备注
                    db.sequenceOf(BeatmapCommentTable).filter { col ->
                        if(toInsert.isEmpty()) {
                            col.bid.eq(-1)
                        } else if(toInsert.size == 1) {
                            col.bid.eq(toInsert.single().bid)
                        } else {
                            toInsert.drop(1).map { col.bid.eq(it.bid) }
                                .fold(col.bid.eq(toInsert.first().bid)) { r, t -> r.or(t) }
                        } and (col.rulesetId eq parameter.rulesetId) and (col.commenterQq eq webUser.qq)
                    }.forEach { bc ->
                        val exist = toInsert.find { c -> c.bid == bc.bid }
                        // 如果数据库已经存在就更新然后移除原列表的项目
                        if(exist != null) {
                            toUpdate.add(exist)
                            toInsert.remove(exist)
                        }
                    }

                    // 更新已存在的备注
                    if (toUpdate.isNotEmpty()) BeatmapCommentTable.batchUpdate {
                        toUpdate.forEach { u ->
                            item {
                                set(BeatmapCommentTable.content, u.comment)
                                where {
                                    BeatmapCommentTable.rulesetId eq parameter.rulesetId and
                                            (BeatmapCommentTable.commenterQq eq webUser.qq)
                                }
                            }
                        }
                    }

                    // 添加新的备注
                    if (toInsert.isNotEmpty()) BeatmapCommentTable.batchInsert(toInsert) {
                        BeatmapComment {
                            bid = it.bid
                            rulesetId = parameter.rulesetId
                            commenterQq = webUser.qq
                            content = it.comment
                        }
                    }

                    // 删除空备注（无论是原先有现在删除还是原先就没有）
                    val emptyCommentsBid = parameter.comments.filter { it.comment.trim().isEmpty() }.map { it.bid }
                    db.sequenceOf(BeatmapCommentTable).removeIf { col ->
                        if(emptyCommentsBid.isEmpty()) {
                            col.bid.eq(-1)
                        } else if(emptyCommentsBid.size == 1) {
                            col.bid.eq(emptyCommentsBid.single())
                        } else {
                            emptyCommentsBid.drop(1).map { col.bid.eq(it) }
                                .fold(col.bid.eq(emptyCommentsBid.first())) { r, t -> r.or(t) }
                        } and (col.rulesetId eq parameter.rulesetId) and (col.commenterQq eq webUser.qq)
                    }
                    SubmitBeatmapCommentResponseDTO(0)
                }

                context.respond(json.encodeToString(
                    querySequence ?: SubmitBeatmapCommentResponseDTO(-1,
                        errorMessage = "Internal error: Database is disconnected from server."
                    )
                ))
            } catch (ex: Exception) {
                ex.printStackTrace()
                context.respond(json.encodeToString(SubmitBeatmapCommentResponseDTO(-1, errorMessage = ex.toString())))
            }
        }
    }
}