package me.stageguard.obms.utils.bmf

import io.github.humbleui.skija.Image
import io.github.humbleui.skija.Surface
import io.netty.buffer.Unpooled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.stageguard.obms.utils.readNullTerminatedString
import java.io.File
import java.lang.IndexOutOfBoundsException
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates

class BitmapFont(
    private val file: File,
    private val bitmapFontInfo: BitmapFontInfo,
    private val bitmapFontCommon: BitmapFontCommon,
    private val pages: Map<Int, String>,
    private val characters: Map<Int, BitmapCharacter>
) : Iterable<BitmapCharacter> {
    private val fontFaceCache: ConcurrentHashMap<String, Image> = ConcurrentHashMap()

    operator fun get(value: Int) : Image? {
        val character = characters[value] ?: return null
        val pageFileName = pages[character.page] ?: return null

        val image = fontFaceCache.getOrPut(pageFileName) {
            runBlocking(Dispatchers.IO) {
                Image.makeFromEncoded(File(file.parent, pageFileName).inputStream().use { it.readAllBytes() })
            }
        }

        return Surface.makeRasterN32Premul(character.width, character.height).run {
            canvas.translate(-character.x.toFloat(), -character.y.toFloat())
            canvas.drawImage(image, 0f, 0f)
            canvas.restore()
            makeImageSnapshot()
        }
    }
    companion object {
        val BMF_MAGIC = byteArrayOf(66, 77, 70)
        val SUPPORTED_VERSION = 3

        fun readFromFile(path: String): BitmapFont {
            val file = File(path)
            check(file.exists() && file.isFile) { "BitmapFont file $path is not exists or not a file." }

            val stream = Unpooled.wrappedBuffer(file.inputStream().use { it.readBytes() })

            ByteArray(3).apply {
                stream.readBytes(this)
                check(this.contentEquals(BMF_MAGIC)) { "Unknown BitmapFont format." }
            }
            stream.readByte().toInt().run {
                check(this == SUPPORTED_VERSION) { "Unimplemented BitmapFont version: $this" }
            }

            var bitmapFontInfo: BitmapFontInfo by Delegates.notNull()
            var bitmapFontCommon: BitmapFontCommon by Delegates.notNull()
            val pages = mutableMapOf<Int, String>()
            val characters = mutableMapOf<Int, BitmapCharacter>()

            var pageCount = 0
            var blockId = stream.readByte().toInt()

            while (blockId != -1) {
                when (blockId) {
                    1 -> {
                        bitmapFontInfo = BitmapFontInfo.parseFromStream(stream)
                    }
                    2 -> {
                        bitmapFontCommon = BitmapFontCommon.parseFromStream(stream)
                        pageCount = bitmapFontCommon.pageCount
                    }
                    3 -> {
                        stream.skipBytes(4)
                        repeat(pageCount) { pages[it] = stream.readNullTerminatedString() }
                    }
                    4 -> {
                        val characterBlockSize = stream.readIntLE()
                        check(characterBlockSize % BitmapCharacter.SIZE_IN_BYTES == 0) {
                            "Invalid character block size."
                        }
                        val characterCount = characterBlockSize / BitmapCharacter.SIZE_IN_BYTES

                        repeat(characterCount) {
                            val character = BitmapCharacter.parseFromStream(stream)
                            characters[character.id] = character
                        }
                    }
                }

                blockId = try { stream.readByte().toInt() } catch (_: IndexOutOfBoundsException) { -1 }
            }

            return BitmapFont(file, bitmapFontInfo, bitmapFontCommon, pages, characters)
        }
    }

    override fun iterator(): Iterator<BitmapCharacter> {
        return characters.values.iterator()
    }
}