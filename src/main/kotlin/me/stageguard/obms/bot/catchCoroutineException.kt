package me.stageguard.obms.bot

import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.stageguard.obms.InvalidTokenException
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.RefactoredException
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.MessageRoute.atReplyText
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MessageRouteExceptionHandler")

val GroupMessageEvent.refactoredExceptionCatcher: CoroutineExceptionHandler
    get() = CoroutineExceptionHandler { context, throwable ->
            logger.error("Error in message handler", throwable)
            runBlocking(Dispatchers.IO) {
                if (throwable !is RefactoredException) {
                    atReplyText("发生了未知错误，请访问 github.com/StageGuard/OsuMapSuggester/issues 并提交以下错误：\n${throwable}")
                    return@runBlocking
                }
                if (throwable is InvalidTokenException/* && OsuUserInfo.getOsuIdAndName(sender.userId) != null*/) {
                    atReplyText("绑定已失效，请输入 .rebind 重新获取绑定链接")
                    return@runBlocking
                }
                atReplyText(throwable.outgoingMessage)
            }
        }

@Suppress("NOTHING_TO_INLINE")
inline fun <reified R> Either<Throwable, R>.rightOrThrowLeft() = rightOrNull ?: throw left