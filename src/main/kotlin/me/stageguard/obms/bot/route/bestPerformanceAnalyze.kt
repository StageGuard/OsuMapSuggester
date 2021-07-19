package me.stageguard.obms.bot.route

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.api.osu.OsuWebApi
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.graph.item.drawBestPerformancesImage
import me.stageguard.obms.utils.export
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.skija.EncodedImageFormat
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

fun GroupMessageSubscribersBuilder.bestPerformanceAnalyze() {
    startsWith("./bpa") {
        val bpScoresResult = OsuWebApi.userScore(user = sender.id, type = "best", limit = 100)
        if(bpScoresResult.isSuccess) {
            val output = drawBestPerformancesImage(bpScoresResult.getOrNull()!!)
            val outputFile = OsuMapSuggester.dataFolder.absolutePath + "${File.separator}img${File.separator}${
                LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toString() + (100..999).random().toString() + sender.id.toString()
            }.png"
            output.export(outputFile, EncodedImageFormat.PNG)
            val externalResource = File(outputFile).toExternalResource("png")
            val image = group.uploadImage(externalResource)
            externalResource.close()
            group.sendMessage(image)
        } else {
            atReply("Cannot analyze your best performance scores: ${bpScoresResult.exceptionOrNull()}")
        }
    }
}