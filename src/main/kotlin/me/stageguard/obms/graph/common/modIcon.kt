package me.stageguard.obms.graph.common

import io.github.humbleui.skija.*
import me.stageguard.obms.graph.osuFont
import me.stageguard.obms.graph.scale
import me.stageguard.obms.osu.processor.beatmap.Mod
import me.stageguard.obms.osu.processor.beatmap.ModType
import kotlin.math.min

fun Canvas.drawModIcon(
    mod: Mod,
    width: Float, height: Float,
    x: Float, y: Float,
    backgroundColor: Int? = null, foregroundColor: Int? = null,
    paint: Paint? = Paint()
) {
    val surface = Surface.makeRasterN32Premul(width.toInt(), height.toInt())

    val innerPaint = Paint().apply { isAntiAlias = true }
    val background = osuFont[0xe04a]!!.run i@ { scale(width / this@i.width, height / this@i.height) }
    innerPaint.colorFilter = ColorFilter.makeBlend(backgroundColor ?: when(mod.type) {
        ModType.DifficultyIncrease -> Color.makeRGB(255, 102, 102)
        ModType.DifficultyReduction -> Color.makeRGB(178, 255, 102)
        ModType.Automation -> Color.makeRGB(102, 204, 255)
        ModType.None -> Color.makeRGB(84, 84, 84)
    }, BlendMode.SRC_IN)
    surface.canvas.drawImage(background, 0f, 0f, innerPaint)

    val modFontImage = osuFont[mod.iconCharacter]
    if (modFontImage != null) {
        val maxSize = min(modFontImage.width, modFontImage.height)
        val heightScale = height / modFontImage.height * 0.85f *
                if (modFontImage.width >modFontImage.height) maxSize / 90.0f else 1.0f
        val foreground = modFontImage.scale(heightScale)
        innerPaint.colorFilter = ColorFilter.makeBlend(foregroundColor ?:
            if (mod.type == ModType.None) Color.makeRGB(255, 204, 33) else Color.makeRGB(84, 84, 84),
        BlendMode.SRC_IN)
        surface.canvas.drawImage(foreground,
            (width - foreground.width) / 2,
            (height - foreground.height) / 2,
            innerPaint
        )
    }

    drawImage(surface.makeImageSnapshot(), x, y, paint)
}