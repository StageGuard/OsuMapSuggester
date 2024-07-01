package me.stageguard.obms.cache

import jakarta.annotation.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.BeatmapParseException
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.*
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.mapRight
import me.stageguard.obms.utils.Either.Companion.onRight
import org.springframework.stereotype.Component
import java.io.File

@Component
class BeatmapCache {
    @Resource
    private lateinit var osuWebApi: OsuWebApi

    val CACHE_FOLDER get() = OsuMapSuggester.dataFolder.absolutePath + File.separator + "beatmap" + File.separator
    @Suppress("NOTHING_TO_INLINE")

    private inline fun beatmapFile(bid: Int) = File("$CACHE_FOLDER$bid.osu")

    suspend fun getBeatmapFile(bid: Int, maxTryCount: Int = 4, tryCount: Int = 1) : OptionalValue<File> {
        val file = beatmapFile(bid)

        if(file.run { exists() && isFile }) {
            return try {
                withContext(Dispatchers.IO) { InferredOptionalValue(file) }
            } catch (ex: Exception) {
                if(tryCount < maxTryCount) {
                    file.delete()
                    getBeatmapFile(bid, maxTryCount, tryCount + 1)
                } else {
                    Either(BeatmapParseException(bid).suppress(ex))
                }
            }
        } else kotlin.run {
            file.parentFile.mkdirs()
            val beatmapStream = osuWebApi.getBeatmapFileStream(bid)
            beatmapStream.onRight { s ->
                withContext(Dispatchers.IO) { runInterruptible {
                    file.createNewFile()
                    s.use {
                        file.writeBytes(it.readAllBytes())
                    }
                } }
                return try {
                    withContext(Dispatchers.IO) { InferredOptionalValue(file) }
                } catch (ex: Exception) {
                    if(tryCount < maxTryCount) {
                        getBeatmapFile(bid, maxTryCount, tryCount + 1)
                    } else {
                        Either(BeatmapParseException(bid).suppress(ex))
                    }
                }
            }.left.also {
                return if(tryCount < maxTryCount) {
                    getBeatmapFile(bid, maxTryCount, tryCount + 1)
                } else {
                    Either(it)
                }
            }
        }
    }

    suspend fun getBeatmap(bid: Int, maxTryCount: Int = 4) : OptionalValue<Beatmap> =
        getBeatmapFile(bid, maxTryCount, 1).mapRight { Beatmap.parse(it.bomReader()) }
}
