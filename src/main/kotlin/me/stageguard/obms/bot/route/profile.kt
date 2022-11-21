package me.stageguard.obms.bot.route

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.bot.graphicProcessorDispatcher
import me.stageguard.obms.bot.refactoredExceptionCatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.graph.bytes
import me.stageguard.obms.graph.item.Profile
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.dto.ScoreDTO
import me.stageguard.obms.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import io.github.humbleui.skija.EncodedImageFormat
import java.time.LocalDateTime
import java.time.ZoneOffset

fun GroupMessageSubscribersBuilder.profile() {
    routeLock(startWithIgnoreCase(".info")) {
        OsuMapSuggester.launch(
            CoroutineName("Command \"info\" of ${sender.id}") + refactoredExceptionCatcher
        ) {
            val profile = OsuWebApi.me(sender.id).rightOrThrowLeft()
            val bestScore = OsuWebApi.userScore(sender.id, type = "best", limit = 100).rightOrNull
                ?.mapIndexed { index, scoreDTO -> index to scoreDTO } ?.sortedBy { it.first }

            val recentBp = bestScore ?.run {
                val currentLocalDateTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                bestScore.filter { (_, score) ->
                    currentLocalDateTime - score.createdAt.primitive.toEpochSecond(ZoneOffset.UTC) < 60 * 60 * 24
                }
            }
            val previousBp = bestScore ?.minus(recentBp)

            val newBpScores = mutableListOf<Pair<ScoreDTO, Double>>()

            val bytes = withContext(graphicProcessorDispatcher) {
                Profile.drawProfilePanel(profile).bytes(EncodedImageFormat.PNG)
            }
            val externalResource = bytes.toExternalResource("png")
            val image = group.uploadImage(externalResource)
            runInterruptible { externalResource.close() }
            atReply(image.toMessageChain())
        }
    }
}
