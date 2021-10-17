package me.stageguard.obms.bot

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.RefactoredException
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.event.events.GroupMessageEvent

val GroupMessageEvent.refactoredExceptionCatcher
    get() = CoroutineExceptionHandler { _, throwable ->
        OsuMapSuggester.logger.error(throwable)
        if (throwable is RefactoredException) {
            runBlocking { atReply(throwable.outgoingMessage) }
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun <reified R> Either<Throwable, R>.rightOrThrowLeft() = rightOrNull ?: throw left