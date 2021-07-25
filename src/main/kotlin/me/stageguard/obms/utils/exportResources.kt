package me.stageguard.obms.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

suspend fun KotlinPlugin.exportStaticResourcesToDataFolder() = withContext(Dispatchers.IO) {
    val fonts = listOf("Torus-Bold.otf", "Torus-Regular.otf", "Torus-SemiBold.otf")
    val mods = listOf("mod_dt.png", "mod_ez.png", "mod_fl.png", "mod_hd.png", "mod_hr.png", "mod_ht.png", "mod_nc.png", "mod_nf.png", "mod_nm.png", "mod_pf.png", "mod_sd.png", "mod_so.png")
    val grades = listOf("grade_a.svg", "grade_b.svg", "grade_c.svg", "grade_d.svg", "grade_s.svg", "grade_sh.svg", "grade_x.svg", "grade_xh.svg")

    fonts.map { "/font/$it" }
        .plus(mods.map { "/image/$it" })
        .plus(grades.map { "/svg/$it" })
        .toMutableList().also {
            it.add("/image/background.png")
            it.add("/svg/arrow-right.svg")
        }.forEach {
            val inputStream = this@exportStaticResourcesToDataFolder::class.java.getResourceAsStream(it)
            val outputFile = dataFolder.absolutePath + File.separator + "resources" + it
            if(inputStream != InputStream.nullInputStream()) {
                File(outputFile).run {
                    File(parent).mkdirs()
                    createNewFile()
                    writeBytes(inputStream.readAllBytes())
                }
            }
            inputStream.close()
        }
    this@exportStaticResourcesToDataFolder.logger.info { "Successfully exported static resources." }
}