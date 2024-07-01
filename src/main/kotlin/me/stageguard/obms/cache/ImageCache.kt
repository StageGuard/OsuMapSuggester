package me.stageguard.obms.cache

import io.github.humbleui.skija.Data
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
import io.github.humbleui.skija.Image
import io.github.humbleui.skija.svg.SVGDOM
import jakarta.annotation.Resource
import me.stageguard.obms.RefactoredException
import me.stageguard.obms.graph.svgDom
import me.stageguard.obms.osu.api.OsuHttpClient
import me.stageguard.obms.utils.Either.Companion.invoke
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import kotlin.properties.Delegates

@Component
class ImageCache {
    @Resource
    private lateinit var osuHttpClient: OsuHttpClient

    @Suppress("NOTHING_TO_INLINE")
    private inline fun imageFile(name: String) = File(File(OsuMapSuggester.dataFolder.absolutePath, "image"), name)

    suspend fun getImageAsStream(
        url: String, fileName: String? = null, maxTryCount: Int = 4, tryCount: Int = 1
    ) : OptionalValue<InputStream> {
        val headers = osuHttpClient.head(url, headers = mapOf(), parameters = mapOf())
        headers.onRight { h ->
            val eTag = h["etag"]
            if (eTag != null || fileName != null) {
                val file = imageFile(eTag ?.trim('"') ?: fileName!!)
                file.parentFile.mkdirs()
                if (file.exists()) {
                    return try {
                        InferredOptionalValue(file.inputStream())
                    } catch (ex: Exception) {
                        file.delete()
                        if(tryCount < maxTryCount) {
                            getImageAsStream(url, fileName, maxTryCount, tryCount + 1)
                        } else {
                            Either(ImageReadException(url).suppress(ex))
                        }
                    }
                } else {
                    val imageStream = osuHttpClient.openStream(url, headers = mapOf(), parameters = mapOf())
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
                            getImageAsStream(url, fileName, maxTryCount, tryCount + 1)
                        } else {
                            Either(it)
                        }
                    }
                }
            } else {
                return if(tryCount < maxTryCount) {
                    getImageAsStream(url, fileName, maxTryCount, tryCount + 1)
                } else {
                    Either(ImageMissingETagException(url))
                }
            }
        }.left.also {
            return if(tryCount < maxTryCount) {
                getImageAsStream(url, fileName, maxTryCount, tryCount + 1)
            } else {
                Either(it)
            }
        }
    }

    suspend fun getImageAsSkijaImage(url: String, fileName: String? = null) = getImageAsStream(url, fileName).run {
        runInterruptible { mapRight { it.use { stream -> Image.makeFromEncoded(stream.readAllBytes()) } } }
    }

    suspend fun getSVGAsSkiaSVGDOM(url: String, saveFile: String, maxTryCount: Int = 5): OptionalValue<SVGDOM> {
        val file = imageFile(saveFile)
        if (file.exists() && file.isFile) {
            try {
                return InferredOptionalValue(runInterruptible {
                    imageFile(file.name).inputStream().use { SVGDOM(Data.makeFromBytes(it.readAllBytes())) }
                })
            } catch (_: Exception) {
                file.delete()
            }
        }

        var exception: RefactoredException by Delegates.notNull()
        repeat(maxTryCount) {
            file.delete()
            val imageStream = osuHttpClient.openStream(url, headers = mapOf(), parameters = mapOf())
            exception = imageStream.onRight {
                file.createNewFile()
                return it.use { s ->
                    val bytes = s.readAllBytes()
                    file.writeBytes(bytes)
                    InferredOptionalValue(runInterruptible {
                        imageFile(file.name).inputStream().use { SVGDOM(Data.makeFromBytes(bytes)) }
                    })
                }
            }.left
        }
        return Either(exception)
    }
}
