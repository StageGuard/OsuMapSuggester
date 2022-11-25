package me.stageguard.obms.utils.bmf

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import me.stageguard.obms.utils.readNullTerminatedString
import java.io.File
import java.lang.IndexOutOfBoundsException
import kotlin.properties.Delegates

class BitmapFont(
    var bitmapFontInfo: BitmapFontInfo,
    var bitmapFontCommon: BitmapFontCommon,
    val pages: Map<Int, String>,
    val characters: Map<Int, BitmapCharacter>
) {
    companion object {
        val BMF_MAGIC = byteArrayOf(66, 77, 70)
        val SUPPORTED_VERSION = 3

        fun readFromReader(stream: ByteBuf) : BitmapFont{
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

            return BitmapFont(bitmapFontInfo, bitmapFontCommon, pages, characters)
        }
        fun readFromFile(path: String) = readFromReader(Unpooled.wrappedBuffer(File(path).inputStream().use { it.readBytes() }))
    }
}