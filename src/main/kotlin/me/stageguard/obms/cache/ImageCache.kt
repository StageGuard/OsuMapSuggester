package me.stageguard.obms.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.stageguard.obms.ImageMissingETagException
import me.stageguard.obms.ImageReadException
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.mapRight
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import org.jetbrains.skija.Image
import java.io.File
import java.io.InputStream
import java.lang.Exception

object ImageCache {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun imageFile(name: String) =
        File(OsuMapSuggester.dataFolder.absolutePath + File.separator + "image" + File.separator + name)

    suspend fun getImageAsStream(
        url: String, maxTryCount: Int = 4, tryCount: Int = 1
    ) : OptionalValue<InputStream> {
        val headers = OsuWebApi.head(url, headers = mapOf(), parameters = mapOf())
        headers.onRight { h ->
            val eTag = h["etag"]
            if (eTag != null) {
                val file = imageFile(eTag.trim('"'))
                file.parentFile.mkdirs()
                if (file.exists()) {
                    return try {
                        InferredOptionalValue(file.inputStream())
                    } catch (ex: Exception) {
                        file.delete()
                        if(tryCount < maxTryCount) {
                            getImageAsStream(url, maxTryCount, tryCount + 1)
                        } else {
                            Either(ImageReadException(url).suppress(ex))
                        }
                    }
                } else {
                    val imageStream = OsuWebApi.openStream(url, headers = mapOf(), parameters = mapOf())
                    imageStream.onRight { s ->
                        withContext(Dispatchers.IO) { runInterruptible {
                            file.createNewFile()
                            s.use {
                                file.writeBytes(it.readAllBytes())
                            }
                        } }
                        return InferredOptionalValue(file.inputStream())
                    }.left.also {
                        return if(tryCount < maxTryCount) {
                            getImageAsStream(url, maxTryCount, tryCount + 1)
                        } else {
                            Either(it)
                        }
                    }
                }
            } else {
                return if(tryCount < maxTryCount) {
                    getImageAsStream(url, maxTryCount, tryCount + 1)
                } else {
                    Either(ImageMissingETagException(url))
                }
            }
        }.left.also {
            return if(tryCount < maxTryCount) {
                getImageAsStream(url, maxTryCount, tryCount + 1)
            } else {
                Either(it)
            }
        }
    }

    suspend fun getImageAsSkijaImage(url: String) = getImageAsStream(url).run {
        runInterruptible { mapRight { Image.makeFromEncoded(it.readAllBytes()) } }
    }
}