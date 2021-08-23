package me.stageguard.obms.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.processor.replay.ReplayFrame
import me.stageguard.obms.osu.processor.replay.ReplayProcessor
import me.stageguard.obms.utils.InferredEitherOrISE
import me.stageguard.obms.utils.ValueOrIllegalStateException
import net.mamoe.mirai.utils.Either
import net.mamoe.mirai.utils.Either.Companion.ifRight
import net.mamoe.mirai.utils.Either.Companion.left
import java.io.File
import org.apache.commons.codec.binary.Base64

object ReplayCache {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun replayFile(sid: Long) =
        File(OsuMapSuggester.dataFolder.absolutePath + File.separator + "replay" + File.separator + sid + ".lzma")

    suspend fun getReplayData(
        scoreId: Long,
        maxTryCount: Int = 4, tryCount: Int = 1
    ) : ValueOrIllegalStateException<Array<ReplayFrame>> {
        val file = replayFile(scoreId)
        return if(file.run { exists() && isFile }) try {
            withContext(Dispatchers.IO) {
                InferredEitherOrISE(ReplayProcessor.processReplayFrame(file))
            }
        } catch (ex: Exception) {
            if(tryCount < maxTryCount) {
                file.delete()
                getReplayData(scoreId, maxTryCount, tryCount + 1)
            } else {
                Either(IllegalStateException("REPLAY_PARSE_ERROR:$ex"))
            }
        } else withContext(Dispatchers.IO) {
            file.parentFile.mkdirs()
            val replay = OsuWebApi.getReplay(scoreId)
            replay.ifRight {
                if(it.encoding == "base64") {
                    val decoded = Base64.decodeBase64(it.content.replace("\\", "").toByteArray())
                    runInterruptible {
                        file.createNewFile()
                        file.writeBytes(decoded)
                    }
                    try {
                        InferredEitherOrISE(ReplayProcessor.processReplayFrame(decoded))
                    } catch (exception: Exception) {
                        file.delete()
                        if(tryCount < maxTryCount) {
                            getReplayData(scoreId, maxTryCount, tryCount + 1)
                        } else {
                            Either(IllegalStateException("REPLAY_PARSE_ERROR:${exception}"))
                        }
                    }
                } else {
                    return@withContext Either(IllegalStateException("UNKNOWN_REPLAY_ENCODING:${it.encoding}"))
                }
            } ?: replay.left.run {
                if(toString().contains("REPLAY_NOT_AVAILABLE")) {
                    Either(this)
                } else {
                    if(tryCount < maxTryCount) {
                        getReplayData(scoreId, maxTryCount, tryCount + 1)
                    } else {
                        Either(IllegalStateException("REPLAY_PARSE_ERROR:${replay.left}"))
                    }
                }
            }
        }
    }
}