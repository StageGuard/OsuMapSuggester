package me.stageguard.obms.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.*
import net.mamoe.mirai.utils.Either
import java.io.File

object BeatmapCache {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun beatmapFile(bid: Int) =
        File(OsuMapSuggester.dataFolder.absolutePath + File.separator + "beatmap" + File.separator + bid + ".osu")

    suspend fun getBeatmap(
        bid: Int, maxTryCount: Int = 4, tryCount: Int = 1
    ) : ValueOrIllegalStateException<Beatmap> {
        val file = beatmapFile(bid)
        return if(file.run { exists() && isFile }) try {
            withContext(Dispatchers.IO) {
                InferredEitherOrISE(Beatmap.parse(file.bomReader()))
            }
        } catch (ex: Exception) {
            if(tryCount < maxTryCount) {
                file.delete()
                getBeatmap(bid, maxTryCount, tryCount + 1)
            } else {
                Either(IllegalStateException("BEATMAP_PARSE_ERROR:$ex"))
            }
        } else withContext(Dispatchers.IO) {
            file.parentFile.mkdirs()
            val beatmap = OsuWebApi.getBeatmap(bid)
            runInterruptible {
                file.createNewFile()
                beatmap.use {
                    file.writeBytes(it.readAllBytes())
                }
            }
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