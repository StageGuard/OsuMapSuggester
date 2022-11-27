package me.stageguard.obms.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.warning
import java.io.File
import java.io.InputStream

suspend fun KotlinPlugin.exportStaticResourcesToDataFolder() = withContext(Dispatchers.IO) {
    listOf(
        "/font/osuFont.fnt",
        "/font/osuFont_0.png",
        "/font/Torus-Bold.otf",
        "/font/Torus-Regular.otf",
        "/font/Torus-SemiBold.otf",
        "/svg/grade_a.svg",
        "/svg/grade_b.svg",
        "/svg/grade_c.svg",
        "/svg/grade_d.svg",
        "/svg/grade_s.svg",
        "/svg/grade_sh.svg",
        "/svg/grade_x.svg",
        "/svg/grade_xh.svg",
        "/svg/grade_f.svg",
        "/image/hit_good.png",
        "/image/hit_great.png",
        "/image/hit_meh.png",
        "/image/hit_miss.png",
        "/image/background.png",
        "/svg/arrow_right.svg",
        "/svg/bpm.svg",
        "/svg/total_length.svg",
        "/image/avatar_guest.png",
        "/svg/icon_discord.svg",
        "/svg/icon_link.svg",
        "/svg/icon_twitter.svg",
        "/svg/polygon_level.svg",
    ).forEach {
        val inputStream = this@exportStaticResourcesToDataFolder::class.java.getResourceAsStream(it)
        val outputFile = dataFolder.absolutePath + File.separator + "resources" + it
        if(inputStream != null && inputStream != InputStream.nullInputStream()) {
            File(outputFile).run {
                File(parent).mkdirs()
                createNewFile()
                writeBytes(inputStream.readAllBytes())
            }
        } else {
            this@exportStaticResourcesToDataFolder.logger.warning { "Resource not found: $it" }
        }
        inputStream?.close()
    }
    this@exportStaticResourcesToDataFolder.logger.info { "Successfully exported static resources." }
}