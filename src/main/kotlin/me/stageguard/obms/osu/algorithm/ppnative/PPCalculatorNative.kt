package me.stageguard.obms.osu.algorithm.ppnative

import me.stageguard.obms.osu.algorithm.PerformanceCalculator
import me.stageguard.obms.osu.algorithm.pp.DifficultyAttributes
import me.stageguard.obms.osu.algorithm.pp.PPResult
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModCombination
import java.io.DataInputStream
import java.io.File

open class PPCalculatorNative private constructor(
    private var _nPtr: Long) : PerformanceCalculator<PPResult<DifficultyAttributes>> {
    private var _nAttributeResultPtr: Long? = null

    override fun mods(vararg mods: Mod): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = mods(_nPtr, ModCombination.of(mods.toList()).rawValue)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun mods(mods: List<Mod>): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = mods(_nPtr, ModCombination.of(mods).rawValue)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun combo(cb: Int): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = combo(_nPtr, cb)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun n300(n: Int): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = n300(_nPtr, n)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun n100(n: Int): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = n100(_nPtr, n)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun n50(n: Int): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = n50(_nPtr, n)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun misses(n: Int): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = misses(_nPtr, n)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun passedObjects(n: Int): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = passedObjects(_nPtr, n)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun accuracy(acc: Double): PPCalculatorNative {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val ptr = accuracy(_nPtr, acc)
        _nPtr = ptr
        assertNativePointer(ptr)
        return this
    }

    override fun calculate(): PPResult<DifficultyAttributes> {
        LAZY_LOAD_LIB
        assertNativePointer(_nPtr)
        val stream = calculate(_nPtr).inputStream()
        return stream.use { s -> DataInputStream(s).use { data ->
            // get leak address of the result struct to reuse by native backend.
            _nAttributeResultPtr = data.readLong()

            val total = data.readDouble()
            val aim = data.readDouble()
            val speed = data.readDouble()
            val accuracy = data.readDouble()
            val stars = data.readDouble()
            val approachRate = data.readDouble()
            val overallDifficulty = data.readDouble()
            val hpDrain = data.readDouble()
            val circleSize = data.readDouble()
            val aimStrain = data.readDouble()
            val sliderFactor = data.readDouble()
            val speedStrain = data.readDouble()
            val maxCombo = data.readInt()
            val nCircles = data.readInt()
            val nSliders = data.readInt()
            val nSpinners = data.readInt()

            // this calculator is consumed by unsafe { Box::from_raw(calc_ptr) }
            _nPtr = -1L

            PPResult(total, aim, speed,accuracy, DifficultyAttributes(
                stars, approachRate, overallDifficulty, hpDrain, circleSize,
                aimStrain, sliderFactor, speedStrain, maxCombo, nCircles, nSliders, nSpinners
            ))
        } }
    }

    companion object {
        private fun assertNativePointer(ptr: Long) {
            if (ptr == 0L) throw NullPointerException("Null native pointer.")
            if (ptr == -1L) throw IllegalStateException("This calculator is released by native backend.")
        }

        fun of(beatmapPath: String): PPCalculatorNative {
            LAZY_LOAD_LIB
            val ptr = createFromNative(beatmapPath)
            if (ptr == 0L) throw RuntimeException("Cannot create pp calculator from native")
            return PPCalculatorNative(ptr)
        }
    }
}
private val LAZY_LOAD_LIB by lazy {
    val host: String = System.getProperty("os.name")
    val libExt = when {
        host.startsWith("Windows") -> "dll"
        host == "Mac OS X" -> "dylib"
        host == "Linux" -> "so"
        else -> {
            println("Unknown host: $host, fallback to linux")
            "so"
        }
    }
    val tempLibDir = File.createTempFile("rosu_pp", libExt).also { it.createNewFile() }
    val libInputStream = PPCalculatorNative::class.java.getResourceAsStream("/rosu_pp.$libExt")

    if (libInputStream != null) {
        libInputStream.use { lis -> tempLibDir.outputStream().use { tos ->
            lis.copyTo(tos)
        } }

        System.load(tempLibDir.absolutePath)
    } else {
        println("Native pp calculator module is not found, falling to back jvm pp calculator.")
    }
}

@JvmName("nCreate") private external fun createFromNative(beatmapPath: String): Long
@JvmName("nMods") private external fun mods(ptr: Long, mods: Int): Long
@JvmName("nCombo") private external fun combo(ptr: Long, combo: Int): Long
@JvmName("nN300") private external fun n300(ptr: Long, n300: Int): Long
@JvmName("nN100") private external fun n100(ptr: Long, n100: Int): Long
@JvmName("nN50") private external fun n50(ptr: Long, n50: Int): Long
@JvmName("nMisses") private external fun misses(ptr: Long, misses: Int): Long
@JvmName("nPassedObjects") private external fun passedObjects(ptr: Long, passedObjects: Int): Long
@JvmName("nAccuracy") private external fun accuracy(ptr: Long, accuracy: Double): Long
@JvmName("nCalculate") private external fun calculate(ptr: Long): ByteArray
