package me.stageguard.obms.utils

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import java.io.IOException
import java.nio.charset.Charset


@Throws(IOException::class)
fun ByteBuf.readString(): String {
    val len = readVarInt()
    val bytes = readBytes(len)
    val str = bytes.toString(Charset.defaultCharset())
    ReferenceCountUtil.release(bytes)
    return str
}
@Throws(IOException::class)

fun ByteBuf.readVarInt(): Int {
    var (i, j) = 0 to 0
    var b0: Byte

    do {
        b0 = this.readByte()
        i = i or (b0.toInt() and 127 shl j++ * 7)
        if (j > 5) throw RuntimeException("VarInt too big")
    } while (b0.toInt() and 128 == 128)

    return i
}

@Throws(IOException::class)
fun ByteBuf.readNullableString() : String? {
    if(readByte().toInt() != 0x0B) return null
    return readString()
}

@Throws(Exception::class)
fun ByteBuf.readCompressedLzmaContent(): ByteBuf {
    val bufferedIStream = ByteBufInputStream(this, true).buffered()

    val bytes = Unpooled.buffer()
    val byteBufOutputStream = ByteBufOutputStream(bytes)

    val lzmaInputStream = LZMACompressorInputStream(bufferedIStream)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    var n: Int
    while (-1 != lzmaInputStream.read(buffer).also { n = it }) {
        byteBufOutputStream.write(buffer, 0, n)
    }

    lzmaInputStream.close()
    byteBufOutputStream.close()
    return bytes
}