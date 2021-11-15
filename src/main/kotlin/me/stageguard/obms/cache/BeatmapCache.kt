package me.stageguard.obms.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.BeatmapParseException
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.*
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.onRight
import java.io.File

object BeatmapCache {
    val CACHE_FOLDER get() = OsuMapSuggester.dataFolder.absolutePath + File.separator + "beatmap" + File.separator
    @Suppress("NOTHING_TO_INLINE")
    private inline fun beatmapFile(bid: Int) = File("$CACHE_FOLDER$bid.osu")

    suspend fun getBeatmap(
        bid: Int, maxTryCount: Int = 4, tryCount: Int = 1
    ) : OptionalValue<Beatmap> {
        val file = beatmapFile(bid)

        if(file.run { exists() && isFile }) {
            return try {
                withContext(Dispatchers.IO) { InferredOptionalValue(Beatmap.parse(file.bomReader())) }
            } catch (ex: Exception) {
                if(tryCount < maxTryCount) {
                    file.delete()
                    getBeatmap(bid, maxTryCount, tryCount + 1)
                } else {
                    Either(BeatmapParseException(bid).suppress(ex))
                }
            }
        } else kotlin.run {
            file.parentFile.mkdirs()
            val beatmapStream = OsuWebApi.getBeatmapFileStream(bid)
            beatmapStream.onRight { s ->
                withContext(Dispatchers.IO) { runInterruptible {
                    file.createNewFile()
                    s.use {
                        file.writeBytes(it.readAllBytes())
                    }
                } }
                return file.bomReader().use {
                    try {
                        withContext(Dispatchers.IO) { InferredOptionalValue(Beatmap.parse(it)) }
                    } catch (ex: Exception) {
                        if(tryCount < maxTryCount) {
                            getBeatmap(bid, maxTryCount, tryCount + 1)
                        } else {
                            Either(BeatmapParseException(bid).suppress(ex))
                        }
                    }
                }
            }.left.also {
                return if(tryCount < maxTryCount) {
                    getBeatmap(bid, maxTryCount, tryCount + 1)
                } else {
                    Either(it)
                }
            }
        }
    }
}