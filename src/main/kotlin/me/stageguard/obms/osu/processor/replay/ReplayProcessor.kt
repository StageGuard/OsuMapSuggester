package me.stageguard.obms.osu.processor.replay

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import me.stageguard.obms.osu.processor.beatmap.HitObjectPosition
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import me.stageguard.obms.utils.readCompressedLzmaContent
import me.stageguard.obms.utils.readNullableString
import java.io.File
import java.nio.charset.Charset
import kotlin.properties.Delegates

class ReplayProcessor constructor(bytes: ByteArray) {
    private val replayBuffer : ByteBuf = Unpooled.wrappedBuffer(bytes)

    constructor(file: File) : this(file.readBytes())

    // replay info
    private var gameMode by Delegates.notNull<Int>()
    private var fileFormat by Delegates.notNull<Int>()
    private lateinit var mapHash : String
    private lateinit var player : String
    private lateinit var replayHash : String
    private var n300 by Delegates.notNull<Int>()
    private var n100 by Delegates.notNull<Int>()
    private var n50 by Delegates.notNull<Int>()
    private var nMiss by Delegates.notNull<Int>()
    private var totalScore by Delegates.notNull<Int>()
    private var maxCombo by Delegates.notNull<Int>()
    private var isPerfect by Delegates.notNull<Boolean>()
    private lateinit var mods : ModCombination

    private val lifeFrames : MutableList<LifeFrame> = mutableListOf()
    private var seed : Int = -1
    private val replayFrames : MutableList<ReplayFrame> = mutableListOf()

    private fun parseFully() {
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
        if(replayLength > 0) parseBody()

    }

    private fun parseBody() {
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
                position = HitObjectPosition(
                    x = split[1].toDouble(),
                    y = split[2].toDouble()
                ),
                keys = Key.parse(split[3].toInt())
            ))

            lastTime = replayFrames.last().time
        }
        repeat(3) { replayFrames.removeFirst() }
    }

    fun processFully() : ReplayData {
        parseFully()
        return ReplayData(
            gameMode, fileFormat, mapHash, player, replayHash,
            n300, n100, n50, nMiss, totalScore, maxCombo,
            isPerfect, mods, seed, lifeFrames, replayFrames
        )
    }
    fun processReplayFrame() : Array<ReplayFrame> {
        parseBody()
        return replayFrames.toTypedArray()
    }

    companion object {
        fun processFully(bytes: ByteArray) =
            ReplayProcessor(bytes).processFully()

        fun processFully(file: File) =
            ReplayProcessor(file).processFully()

        fun processReplayFrame(bytes: ByteArray) =
            ReplayProcessor(bytes).processReplayFrame()

        fun processReplayFrame(file: File) =
            ReplayProcessor(file).processReplayFrame()
    }
}