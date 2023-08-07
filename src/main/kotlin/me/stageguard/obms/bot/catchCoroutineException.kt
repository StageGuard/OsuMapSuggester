package me.stageguard.obms.bot

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.stageguard.obms.InvalidTokenException
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.RefactoredException
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.event.events.GroupMessageEvent

val GroupMessageEvent.refactoredExceptionCatcher
    get() = CoroutineExceptionHandler { context, throwable ->
        OsuMapSuggester.logger.error(throwable)
        runBlocking(Dispatchers.IO) {
            if (throwable !is RefactoredException) {
                atReply("发生了未知错误，请访问 github.com/StageGuard/OsuMapSuggester/issues 并提交以下错误：\n${throwable}")
                return@runBlocking
            }
            if (throwable is InvalidTokenException && OsuUserInfo.getOsuIdAndName(sender.id) != null) {
                val link = OAuthManager.createOAuthLink(AuthType.BIND_ACCOUNT, listOf(sender.id, group.id))
                atReply("请点击这个链接进行绑定更新: $link")
                return@runBlocking
            }
            atReply(throwable.outgoingMessage)
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun <reified R> Either<Throwable, R>.rightOrThrowLeft() = rightOrNull ?: throw left