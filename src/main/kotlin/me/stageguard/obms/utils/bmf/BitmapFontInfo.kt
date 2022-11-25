package me.stageguard.obms.utils.bmf

import io.netty.buffer.ByteBuf
import me.stageguard.obms.utils.readNullTerminatedString
import okhttp3.internal.and

data class BitmapFontInfo(
    val size: Int,
    val smooth: Boolean,
    val unicode: Boolean,
    val italic: Boolean,
    val bold: Boolean,
    val charset: BMFCharset,
    val stretchHeight: Int,
    val superSamplingLevel: Int,
    val paddingTop: Int,
    val paddingRight: Int,
    val paddingBottom: Int,
    val paddingLeft: Int,
    val spacingHorizontal: Int,
    val spadingVertical: Int,
    val outline: Int,
    val face: String,

) {
    companion object {
        private const val MIN_SIZE_IN_BYTES = 15

        fun parseFromStream(stream: ByteBuf) : BitmapFontInfo {
            check(stream.readIntLE() >= MIN_SIZE_IN_BYTES) { "Invalid info block size in reading BitmapFontInfo." }

            val size = stream.readShortLE().toInt()
            val type = stream.readByte()
            val smooth = type and (1 shl 7) != 0
            val unicode = type and (1 shl 6) != 0
            val italic = type and (1 shl 5) != 0
            val bold = type and (1 shl 4) != 0

            val charset = kotlin.run {
                val value = stream.readByte().toInt()
                enumValues<BMFCharset>().find { it.value == value }!!
            }

            val stretchHeight = stream.readUnsignedShortLE()
            val superSamplingLevel = stream.readByte().toInt()

            val paddingTop = stream.readByte().toInt()
            val paddingRight = stream.readByte().toInt()
            val paddingBottom = stream.readByte().toInt()
            val paddingLeft = stream.readByte().toInt()
            val spacingHorizontal = stream.readByte().toInt()
            val spadingVertical = stream.readByte().toInt()
            val outline = stream.readByte().toInt()
            val face = stream.readNullTerminatedString()

            return BitmapFontInfo(
                size,
                smooth,
                unicode,
                italic,
                bold,
                charset,
                stretchHeight,
                superSamplingLevel,
                paddingTop,
                paddingRight,
                paddingBottom,
                paddingLeft,
                spacingHorizontal,
                spadingVertical,
                outline,
                face
            )
        }
    }
}