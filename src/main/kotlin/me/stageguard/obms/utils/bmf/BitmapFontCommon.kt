package me.stageguard.obms.utils.bmf

import io.netty.buffer.ByteBuf
import okhttp3.internal.and

data class BitmapFontCommon(
    val lineHeight: Int,
    val base: Int,
    val scaleWidth: Int,
    val scaleHeight: Int,
    val pageCount: Int,
    val packed: Boolean,
    val alphaChannel: BMFChannelData,
    val redChannel: BMFChannelData,
    val greenChannel: BMFChannelData,
    val blueChannel: BMFChannelData
) {
    companion object {
        private const val SIZE_IN_BYTES = 15

        fun parseFromStream(stream: ByteBuf) : BitmapFontCommon {
            val size = stream.readIntLE()
            check(size >= SIZE_IN_BYTES) { "Invalid info block size in reading BitmapFontCommon." }

            val lineHeight = stream.readUnsignedShortLE()
            val base = stream.readUnsignedShortLE()
            val scaleWidth = stream.readUnsignedShortLE()
            val scaleHeight = stream.readUnsignedShortLE()
            val pageCount = stream.readUnsignedShortLE()
            val packed = stream.readByte() and 1 != 0

            val alphaChannel = kotlin.run {
                val value = stream.readByte().toInt()
                enumValues<BMFChannelData>().find { it.value == value }!!
            }
            val redChannel = kotlin.run {
                val value = stream.readByte().toInt()
                enumValues<BMFChannelData>().find { it.value == value }!!
            }
            val greenChannel = kotlin.run {
                val value = stream.readByte().toInt()
                enumValues<BMFChannelData>().find { it.value == value }!!
            }
            val blueChannel = kotlin.run {
                val value = stream.readByte().toInt()
                enumValues<BMFChannelData>().find { it.value == value }!!
            }

            return BitmapFontCommon(
                lineHeight,
                base,
                scaleWidth,
                scaleHeight,
                pageCount,
                packed,
                alphaChannel,
                redChannel,
                greenChannel,
                blueChannel,
            )
        }
    }
}