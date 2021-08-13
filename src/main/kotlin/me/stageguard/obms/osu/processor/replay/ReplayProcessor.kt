package me.stageguard.obms.osu.processor.replay

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.readCompressedLzmaContent
import me.stageguard.obms.utils.readNullableString
import java.io.File
import java.nio.charset.Charset
import kotlin.properties.Delegates

class ReplayProcessor(replayFile: File) {
    private val replayBuffer : ByteBuf = Unpooled.wrappedBuffer(replayFile.readBytes())

    private var isHeaderRead = false
    private var isBodyRead = false

    // replay info
    var gameMode by Delegates.notNull<Int>()
    var fileFormat by Delegates.notNull<Int>()
    lateinit var mapHash : String
    lateinit var player : String
    lateinit var replayHash : String
    var n300 by Delegates.notNull<Int>()
    var n100 by Delegates.notNull<Int>()
    var n50 by Delegates.notNull<Int>()
    var nMiss by Delegates.notNull<Int>()
    var totalScore by Delegates.notNull<Int>()
    var maxCombo by Delegates.notNull<Int>()
    var isPerfect by Delegates.notNull<Boolean>()
    lateinit var mods : ModCombination

    val lifeFrames : MutableList<LifeFrame> = mutableListOf()
    var seed by Delegates.notNull<Int>()
    val replayFrames : MutableList<ReplayFrame> = mutableListOf()

    private fun readHeader() {
        if(isHeaderRead) return

        gameMode = replayBuffer.readByte().toInt()
        fileFormat = replayBuffer.readIntLE()
        mapHash = replayBuffer.readNullableString() ?: ""
        player = replayBuffer.readNullableString() ?: ""
        replayHash = replayBuffer.readNullableString() ?: ""
        n300 = replayBuffer.readUnsignedShortLE()
        n100 = replayBuffer.readUnsignedShortLE()
        n50 = replayBuffer.readUnsignedShortLE()
        replayBuffer.skipBytes(4) // skip countGeki and countKatu
        nMiss = replayBuffer.readUnsignedShortLE()
        totalScore = replayBuffer.readIntLE()
        maxCombo = replayBuffer.readUnsignedShortLE()
        isPerfect = replayBuffer.readBoolean()
        mods = ModCombination.ofRaw(replayBuffer.readIntLE())

        isHeaderRead = true
    }

    private fun readBody() {
        require(isHeaderRead) { "Cannot read replay body before reading header." }
        if(isBodyRead) return

        // life percentage
        val lifeFrames = replayBuffer.readNullableString() ?: ""
        if(lifeFrames.isNotEmpty()) {
            lifeFrames.split(",").forEach { lifeFrame ->
                val life = lifeFrame.split("|")
                if(life.size == 2) {
                    this.lifeFrames.add(LifeFrame(time = life[0].toInt(), percentage = life[1].toDouble()))
                }
            }
        }

        replayBuffer.skipBytes(8) // skip time

        val replayLength = replayBuffer.readIntLE()
        if(replayLength > 0) {
            val decompressed = replayBuffer.readCompressedLzmaContent()
            val contents = decompressed.toString(Charset.defaultCharset())

            ReferenceCountUtil.release(decompressed) // release this byteBuf after reading contents

            var lastTime = 0
            contents.split(",").forEach lambdaContinue@ { frame ->
                if(frame.isEmpty()) return@lambdaContinue

                val split = frame.split("|")
                if(split.size < 4) return@lambdaContinue

                if(split[0] == "-12345") {
                    seed = split[3].toInt()
                    return@lambdaContinue
                }

                replayFrames.add(ReplayFrame(
                    timeDiff = split[0].toInt(),
                    time = split[0].toInt() + lastTime,
                    x = split[1].toDouble(),
                    y = split[2].toDouble(),
                    keys = Keys.parse(split[3].toInt())
                ))

                lastTime = replayFrames.last().time
            }
        }

        repeat(3) { replayFrames.removeFirst() }
        isBodyRead = true
    }

    fun process() {
        readHeader()
        readBody()
    }
}