package me.stageguard.obms.bot.route

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.api.osu.OsuWebApi
import me.stageguard.obms.api.osu.dto.ScoreDTO
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.graph.item.drawBestPerformancesImage
import me.stageguard.obms.graph.item.orderScores
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.export
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

val regex = Regex("(\\d+)[\\-_→](\\d+)")

fun GroupMessageSubscribersBuilder.bestPerformanceAnalyze() {
    startsWith(".bpa") {
        //parse message
        var (limit, offset) = 25 to 0
        val target: At? = message.filterIsInstance<At>().run {
            if(isEmpty()) null else first()
        }
        regex.matchEntire(
            message.filterIsInstance<PlainText>().joinToString("").substringAfter(".bpa").trim()
        )?.groupValues?.run {
            limit = if(get(2).isEmpty()) 25 else if(get(2).toInt() - get(1).toInt() + 1 > 100) 100 else get(2).toInt() - get(1).toInt() + 1
            offset = if(get(2).isEmpty()) 0 else if(get(1).toInt() - 1 < 0) 0 else get(1).toInt() - 1
        }

        val scoresPair: Pair<List<ScoreDTO>, List<ScoreDTO>?> =
            when(val myBpScores = OsuWebApi.userScore(user = sender.id, type = "best", limit = limit, offset = offset)) {
                is Either.Left -> myBpScores.value
                is Either.Right -> {
                    atReply("Cannot fetch your best performance scores: ${myBpScores.value}")
                    return@startsWith
                }
            } to kotlin.run {
                if(target == null || target.target == sender.id) null else {
                    when(val myBpScores = OsuWebApi.userScore(user = target.target, type = "best", limit = limit, offset = offset)) {
                        is Either.Left -> myBpScores.value
                        is Either.Right -> {
                            atReply("Cannot fetch target's best performance scores: ${myBpScores.value}")
                            return@startsWith
                        }
                    }
                }
            }

        val output = drawBestPerformancesImage(orderScores(scoresPair))
        val outputFile = OsuMapSuggester.dataFolder.absolutePath + "${File.separator}img${File.separator}${
            LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toString() + (100..999).random().toString() + sender.id.toString()
        }.png"
        output.export(outputFile, EncodedImageFormat.PNG)
        val externalResource = File(outputFile).toExternalResource("png")
        val image = group.uploadImage(externalResource)
        externalResource.close()
        group.sendMessage(image)
    }
}