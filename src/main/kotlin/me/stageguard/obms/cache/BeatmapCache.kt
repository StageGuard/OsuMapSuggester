package me.stageguard.obms.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.*
import me.stageguard.obms.utils.Either
import java.io.File

object BeatmapCache {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun beatmapFile(bid: Int) =
        File(OsuMapSuggester.dataFolder.absolutePath + File.separator + "beatmap" + File.separator + bid + ".osu")

    suspend fun getBeatmap(
        bid: Int, maxTryCount: Int = 4, tryCount: Int = 1
    ) : ValueOrISE<Beatmap> {
        val file = beatmapFile(bid)
        return if(file.run { exists() && isFile }) try {
            InferredEitherOrISE(Beatmap.parse(file.bomReader()))
        } catch (ex: Exception) {
            if(tryCount < maxTryCount) {
                file.delete()
                getBeatmap(bid, maxTryCount, tryCount + 1)
            } else {
                Either(IllegalStateException("BEATMAP_PARSE_ERROR:$ex"))
            }
        } else kotlin.run {
            file.parentFile.mkdirs()
            val beatmap = OsuWebApi.getBeatmapFileStream(bid)
            withContext(Dispatchers.IO) { runInterruptible {
                file.createNewFile()
                beatmap.use {
                    file.writeBytes(it.readAllBytes())
                }
            } }
            file.bomReader().use {
                try {
                    InferredEitherOrISE(Beatmap.parse(it))
                } catch (ex: Exception) {
                    if(tryCount < maxTryCount) {
                        getBeatmap(bid, maxTryCount, tryCount + 1)
                    } else {
                        Either(IllegalStateException("BEATMAP_PARSE_ERROR:$ex"))
                    }
                }
            }
        }
    }
}