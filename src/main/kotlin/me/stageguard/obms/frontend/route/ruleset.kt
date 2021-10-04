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
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.RulesetCollection
import me.stageguard.obms.database.model.WebVerificationStore
import me.stageguard.obms.frontend.dto.*
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.script.ScriptContext
import org.ktorm.dsl.eq
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import java.net.URLDecoder
import java.nio.charset.Charset

const val RULESET_PATH = "ruleset"

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
        encodeDefaults = true
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
                    }
                    if(find == null) {
                        context.respond(json.encodeToString(WebVerificationResponseDTO(1)))
                        return@query
                    }

                    if(find.qq == -1L) {
                        context.respond(json.encodeToString(WebVerificationResponseDTO(2, osuId = find.osuId)))
                        return@query
                    }

                    val userInfo = db.sequenceOf(OsuUserInfo).find { it.qq eq find.qq }
                    if(userInfo == null) {
                        context.respond(json.encodeToString(WebVerificationResponseDTO(3, osuId = find.osuId, qq = find.qq)))
                        return@query
                    }

                    context.respond(json.encodeToString(WebVerificationResponseDTO(
                        result = 0, qq = userInfo.qq,
                        osuId = userInfo.osuId, osuName = userInfo.osuName
                    )))
                }

                if(querySequence == null) context.respond(
                    json.encodeToString(WebVerificationResponseDTO(-1,
                        errorMessage = "Internal error: Database is disconnected from server."
                    )
                ))

            } catch (ex: Exception) {
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

                        if(ruleset == null) {
                            context.respond(json.encodeToString(CheckEditAccessResponseDTO(1)))
                            return@query
                        }

                        if(ruleset.author != parameter.qq) {
                            context.respond(json.encodeToString(CheckEditAccessResponseDTO(2,
                                EditRulesetDTO(name = ruleset.name)
                            )))
                            return@query
                        }

                        context.respond(json.encodeToString(
                            CheckEditAccessResponseDTO(0, EditRulesetDTO(
                                id = ruleset.id, name = ruleset.name, condition = ruleset.condition,
                                triggers = ruleset.triggers.split(";").map { it.trim() }
                            ))
                        ))
                    }

                    if(querySequence == null) context.respond(
                        json.encodeToString(CheckEditAccessResponseDTO(-1,
                            errorMessage = "Internal error: Database is disconnected from server."
                        )
                    ))
                } else {
                    context.respond(json.encodeToString(CheckEditAccessResponseDTO(1)))
                }
            } catch (ex: Exception) {
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
                context.respond(json.encodeToString(CheckSyntaxResponseDTO(-1, errorMessage = ex.toString())))
            }
        }
        // 提交修改
        post("/$RULESET_PATH/submit") {
            try {
                val parameter = json.decodeFromString<SubmitRequestDTO>(context.receiveText())

            } catch (ex: Exception) {
                context.respond(json.encodeToString(SubmitResponseDTO(-1, errorMessage = ex.toString())))
            }
        }
    }
}