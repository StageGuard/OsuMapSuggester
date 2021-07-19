package me.stageguard.obms.bot.route

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.api.osu.OsuWebApi
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.graph.item.drawBestPerformancesImage
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.export
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

fun GroupMessageSubscribersBuilder.bestPerformanceAnalyze() {
    matching(Regex("\\./bpa\\s*((\\d+)[\\-_→](\\d+))?")) {
        println(it.groupValues)
        val bpScoresResult = it.groupValues.run {
            OsuWebApi.userScore(
                user = sender.id,
                type = "best",
                limit = if(get(3).isEmpty()) 25 else if(get(3).toInt() - get(2).toInt() + 1 > 100) 100 else get(3).toInt() - get(2).toInt() + 1,
                offset = if(get(3).isEmpty()) 0 else if(get(2).toInt() - 1 < 0) 0 else get(2).toInt() - 1
            )
        }
        when(bpScoresResult) {
            is Either.Left -> {
                val output = drawBestPerformancesImage(bpScoresResult.value)
                val outputFile = OsuMapSuggester.dataFolder.absolutePath + "${File.separator}img${File.separator}${
                    LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toString() + (100..999).random().toString() + sender.id.toString()
                }.png"
                output.export(outputFile, EncodedImageFormat.PNG)
                val externalResource = File(outputFile).toExternalResource("png")
                val image = group.uploadImage(externalResource)
                externalResource.close()
                group.sendMessage(image)
            }
            is Either.Right -> {
                atReply("Cannot analyze your best performance scores: ${bpScoresResult.value}")
            }
        }
    }
}