package me.stageguard.obms.frontend.route

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.WebVerificationStore
import me.stageguard.obms.frontend.dto.*
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthManager
import org.ktorm.dsl.eq
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import java.nio.charset.Charset

const val RULESET_PATH = "ruleset"

/**
 * 编辑谱面时的身份认证流程：
 * 1. 用户请求 `http://host/ruleset/{id}` 或 `http://host/ruleset/new` (下方的 (`I`))，回应 `ruleset.html` 前端。
 * 2. `ruleset.html` 前端获取 cookies 中的 `token`，认证身份(请求下方的 `II`)。
 * 3. 如果没有 `identification`， `ruleset.html` 前端会请求后端(下方的 (`III`))获取 oauth 链接
 *    (`OAuthManager.createOAuthLink(type = AuthType.EDIT_RULESET)`)。
 * 4. 点击链接完成认证后，结果会在 `AUTH_CALLBACK_PATH` 中处理(在数据库中创建一个 `token` 并绑定到用户 QQ 上)，
 *    处理后会重定向至 (`I`)，并带有 `Set-Cookie: token`。
 */

@OptIn(ExperimentalSerializationApi::class)
fun Application.ruleset() {
    routing {
        // (I): 回应前端 HTML，由前端处理 `{type}`
        get("/$RULESET_PATH/edit/new") {
            val rulesetPage = getFrontendResourceStream("ruleset.html")
            if(rulesetPage != null) {
                runInterruptible(Dispatchers.IO) { runBlocking {
                    context.respondText(
                        rulesetPage.readAllBytes().toString(Charset.forName("UTF-8")),
                        ContentType.Text.Html, HttpStatusCode.OK
                    )
                } }
                finish()
            } else {
                context.respond(HttpStatusCode.NotFound, "Page ruleset.html is not found.")
            }
        }
        get("/$RULESET_PATH/edit/{rid}") {
            finish()
        }
        post("/$RULESET_PATH/verify") p@ {
            try {
                val parameter = Json.decodeFromString<WebVerificationRequestDTO>(context.receiveText())
                val querySequence = Database.query { db ->
                    val find = db.sequenceOf(WebVerificationStore).find { it.token eq parameter.token }
                    if(find == null) {
                        context.respond(Json.encodeToString(WebVerificationResponseDTO(1)))
                        return@query
                    }

                    if(find.qq == -1L) {
                        context.respond(Json.encodeToString(WebVerificationResponseDTO(2)))
                        return@query
                    }

                    val userInfo = db.sequenceOf(OsuUserInfo).find { it.qq eq find.qq }
                    if(userInfo == null) {
                        context.respond(Json.encodeToString(WebVerificationResponseDTO(3, qq = find.qq)))
                        return@query
                    }

                    context.respond(WebVerificationResponseDTO(
                        result = 0, qq = userInfo.qq,
                        osuId = userInfo.osuId, osuName = userInfo.osuName
                    ))
                }
                if(querySequence == null) context.respond(HttpStatusCode.InternalServerError,
                    Json.encodeToString(WebVerificationResponseDTO(-1,
                        errorMessage = "Internal error: Database is disconnected from server."
                    )
                ))

            } catch (ex: Exception) {
                context.respond(
                    HttpStatusCode.InternalServerError,
                    Json.encodeToString(WebVerificationResponseDTO(-1, errorMessage = ex.toString()))
                )
            }
            finish()
        }
        // (III): 回应一个 oauth 链接
        post("/$RULESET_PATH/getVerifyLink") {
            try {
                val parameter = Json.decodeFromString<CreateVerificationLinkRequestDTO>(context.receiveText())
                val link = OAuthManager.createOAuthLink(AuthType.EDIT_RULESET, listOf(parameter.callbackPath))
                context.respond(Json.encodeToString(CreateVerificationLinkResponseDTO(0, link = link)))
            } catch (ex: Exception) {
                context.respond(Json.encodeToString(CreateVerificationLinkResponseDTO(-1, errorMessage = ex.toString())))
            }
            finish()
        }
    }
}