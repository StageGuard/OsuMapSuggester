package me.stageguard.obms.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.processor.beatmap.Beatmap
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.bomReader
import java.io.File
import java.lang.IllegalStateException

object BeatmapPool {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun beatmapFile(bid: Int) =
        File(OsuMapSuggester.dataFolder.absolutePath + File.separator + "beatmap" + File.separator + bid + ".osu")

    suspend fun getBeatmap(bid: Int, tryCount: Int = 1) : Either<Beatmap, IllegalStateException> {
        val file = beatmapFile(bid)
        return if(file.run { exists() && isFile }) try {
            withContext(Dispatchers.IO) {
                Either.Left(Beatmap.parse(file.bomReader()))
            }
        } catch (ex: Exception) {
            if(tryCount <= 4) {
                file.delete()
                getBeatmap(bid, tryCount + 1)
            } else {
                Either.Right(IllegalStateException("BEATMAP_PARSE_ERROR:$ex"))
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
            val result: Either<Beatmap, IllegalStateException>
            file.bomReader().use {
                result = try {
                    Either.Left(Beatmap.parse(it))
                } catch (ex: Exception) {
                    if(tryCount <= 4) {
                        getBeatmap(bid, tryCount + 1)
                    } else {
                        Either.Right(IllegalStateException("BEATMAP_PARSE_ERROR:$ex"))
                    }
                }
            }
            result
        }
    }
}