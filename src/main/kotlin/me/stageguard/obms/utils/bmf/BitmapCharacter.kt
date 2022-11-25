package me.stageguard.obms.utils.bmf

import io.netty.buffer.ByteBuf

data class BitmapCharacter(
    val id: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val xAdvance: Int,
    val page: Int,
    val channel: BMFChannel,
) {
    companion object {
        const val SIZE_IN_BYTES = 20

        fun parseFromStream(stream: ByteBuf): BitmapCharacter {
            val id = stream.readUnsignedIntLE().toInt()
            val x = stream.readUnsignedShortLE()
            val y = stream.readUnsignedShortLE()
            val width = stream.readUnsignedShortLE()
            val height = stream.readUnsignedShortLE()
            val xOffset = stream.readShortLE().toInt()
            val yOffset = stream.readShortLE().toInt()
            val xAdvance = stream.readShortLE().toInt()
            val page = stream.readByte().toInt()
            val channel = kotlin.run {
                val value = stream.readByte().toInt()
                enumValues<BMFChannel>().find { it.value == value }!!
            }

            return BitmapCharacter(
                id,
                x,
                y,
                width,
                height,
                xOffset,
                yOffset,
                xAdvance,
                page,
                channel,
            )
        }
    }
}