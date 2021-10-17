package me.stageguard.obms.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.ReplayNotAvailable
import me.stageguard.obms.ReplayParseException
import me.stageguard.obms.UnsupportedReplayFormatException
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.processor.replay.ReplayFrame
import me.stageguard.obms.osu.processor.replay.ReplayProcessor
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.onRight
import java.io.File
import org.apache.commons.codec.binary.Base64

object ReplayCache {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun replayFile(sid: Long) =
        File(OsuMapSuggester.dataFolder.absolutePath + File.separator + "replay" + File.separator + sid + ".lzma")

    suspend fun getReplayData(
        scoreId: Long, maxTryCount: Int = 4, tryCount: Int = 1
    ) : OptionalValue<Array<ReplayFrame>> {
        val file = replayFile(scoreId)

        if(file.run { exists() && isFile }) {
            return try {
                InferredOptionalValue(ReplayProcessor.processReplayFrame(file))
            } catch (ex: Exception) {
                if(tryCount < maxTryCount) {
                    file.delete()
                    getReplayData(scoreId, maxTryCount, tryCount + 1)
                } else {
                    Either(ReplayParseException(scoreId).suppress(ex))
                }
            }
        } else {
            file.parentFile.mkdirs()
            val replay = OsuWebApi.getReplay(scoreId)
            replay.onRight {
                return if(it.encoding == "base64") {
                    val decoded = Base64.decodeBase64(it.content.replace("\\", "").toByteArray())
                    withContext(Dispatchers.IO) { runInterruptible {
                        file.createNewFile()
                        file.writeBytes(decoded)
                    } }
                    try {
                        InferredOptionalValue(ReplayProcessor.processReplayFrame(decoded))
                    } catch (ex: Exception) {
                        file.delete()
                        if(tryCount < maxTryCount) {
                            getReplayData(scoreId, maxTryCount, tryCount + 1)
                        } else {
                            Either(ReplayParseException(scoreId).suppress(ex))
                        }
                    }
                } else {
                    Either(UnsupportedReplayFormatException(scoreId, it.encoding))
                }
            }.left.also {
                return if(it is ReplayNotAvailable) {
                    Either(it)
                } else if(tryCount < maxTryCount) {
                    getReplayData(scoreId, maxTryCount, tryCount + 1)
                } else {
                    Either(it)
                }
            }
        }
    }
}